package com.igormaznitsa.jaip.providers.gemini;

import static com.igormaznitsa.jaip.common.StringUtils.findPropertyNonNullableValue;
import static com.igormaznitsa.jaip.common.StringUtils.normalizeJavaResponse;
import static com.igormaznitsa.jaip.common.cache.JaipPromptCacheFile.PROPERTY_JAIP_PROMPT_CACHE_FILE;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.igormaznitsa.jaip.common.AbstractJaipProcessor;
import com.igormaznitsa.jaip.common.cache.JaipPromptCacheFile;
import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class GeminiJaipProcessor extends AbstractJaipProcessor {

  public static final String PROPERTY_GEMINI_MODEL = "jaip.gemini.model";
  public static final String PROPERTY_GEMINI_PROJECT_ID = "jaip.gemini.project.id";
  public static final String PROPERTY_GEMINI_API_KEY = "jaip.gemini.api.key";
  public static final String PROPERTY_GEMINI_GENERATE_CONFIG_JSON =
      "jaip.gemini.model.generate.config.json";
  public static final String PROPERTY_GEMINI_DISABLE_NORMALIZE_RESPONSE =
      "jaip.gemini.disable.normalize.response";
  public static final String PROPERTY_GEMINI_CLIENT_HTTP_CONFIG_JSON =
      "jaip.gemini.client.http.config.json";
  public static final String PROPERTY_GEMINI_CLIENT_OPTIONS_JSON =
      "jaip.gemini.client.options.json";
  private static final MessageDigest SHA512_DIGEST;

  static {
    try {
      SHA512_DIGEST = MessageDigest.getInstance("SHA-512");
    } catch (Exception ex) {
      throw new Error("Can't find SHA-512 digest", ex);
    }
  }

  private Client client;
  private String geminiModel;
  private GenerateContentConfig geminiGenerateContentConfig;
  private JaipPromptCacheFile jaipPromptCacheFile;

  public GeminiJaipProcessor() {
    super();
  }

  private static GenerateContentConfig makeDefaultGenerateContentConfig() {
    return GenerateContentConfig.builder()
        .temperature(0.3f)
        .topP(0.95f)
        .topK(40.0f)
        .maxOutputTokens(128 * 1024)
        .systemInstruction(Content.builder()
            .role("model")
            .parts(Part.builder().text(
                    "You are very skilled senior programming engineer with very deep knowledge in algorithms and Java and core Java development.")
                .build())
            .build())
        .seed(234789324)
        .responseModalities("TEXT")
        .build();
  }

  private static String makeKey(final String model, final String prompt) {
    return Base64.getEncoder()
        .encodeToString(SHA512_DIGEST.digest((model + prompt).getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public String getProcessorTextId() {
    return "GEMINI";
  }

  @Override
  protected void onProcessorStarted(PreprocessorContext context) {
    this.geminiModel = findPropertyNonNullableValue(PROPERTY_GEMINI_MODEL, null);
    logInfo("required model: " + this.geminiModel);

    var clientBuilder = Client.builder();

    final String clientConfigJson = System.getProperty(PROPERTY_GEMINI_CLIENT_OPTIONS_JSON);
    if (clientConfigJson != null) {
      logInfo("applying provided client config as Json");
      clientBuilder.clientOptions(ClientOptions.fromJson(clientConfigJson));
    }
    final String apiKey = System.getProperty(PROPERTY_GEMINI_API_KEY, null);
    if (apiKey != null) {
      logInfo("api key provided");
      clientBuilder.apiKey(apiKey);
    }

    final String projectId = System.getProperty(PROPERTY_GEMINI_PROJECT_ID, null);
    if (projectId != null) {
      logInfo("project id: " + projectId);
      clientBuilder.project(projectId);
    }

    this.client = clientBuilder.build();

    final String httpClientJson =
        System.getProperty(PROPERTY_GEMINI_CLIENT_HTTP_CONFIG_JSON);
    if (httpClientJson != null) {
      logInfo("applying http client config provided as Json");
      clientBuilder.httpOptions(HttpOptions.fromJson(httpClientJson));
    } else {
      logInfo("using default http client config");
    }

    final String generateContentConfigJson =
        System.getProperty(PROPERTY_GEMINI_GENERATE_CONFIG_JSON);
    if (generateContentConfigJson == null) {
      logInfo("using default generate content config");
      this.geminiGenerateContentConfig = makeDefaultGenerateContentConfig();
    } else {
      logInfo("applying generate client config provided as Json");
      this.geminiGenerateContentConfig =
          GenerateContentConfig.fromJson(generateContentConfigJson);
    }

    try {
      this.jaipPromptCacheFile = JaipPromptCacheFile.findAmongSystemProperties();
      if (this.jaipPromptCacheFile == null) {
        logInfo("there is no any system property defining file cache (property  " +
            PROPERTY_JAIP_PROMPT_CACHE_FILE + ")");
      } else {
        logInfo("prompt cache file: " + this.jaipPromptCacheFile.getPath());
      }
    } catch (IOException ex) {
      logError("Error during open prompt cache file, so it will be ignored: " + ex.getMessage());
      this.jaipPromptCacheFile = null;
    }
    this.client = clientBuilder.build();
  }

  @Override
  protected void onProcessorStopped(PreprocessorContext context, Throwable error) {
    try {
      this.client.close();
    } finally {
      if (this.jaipPromptCacheFile != null) {
        this.logInfo("flushing prompt cache file: " + this.jaipPromptCacheFile.getPath());
        this.jaipPromptCacheFile.flush();
      }
      this.jaipPromptCacheFile = null;
    }
  }

  @Override
  public String processPrompt(
      final String prompt,
      final FileInfoContainer sourceFileContainer, final FilePositionInfo positionInfo,
      final PreprocessorContext context,
      final PreprocessingState state) {
    final String cacheKey = makeKey(this.geminiModel, prompt);

    String result = this.jaipPromptCacheFile == null ? null :
        this.jaipPromptCacheFile.getCache().find(cacheKey);

    final String preprocessedFilePosition =
        positionInfo.getFile().getName() + ':' + positionInfo.getLineNumber();
    if (result == null) {
      final long start = System.currentTimeMillis();
      RuntimeException exception = null;
      try {
        logInfo(
            "sending request for prompt found at " + preprocessedFilePosition);
        final GenerateContentResponse response =
            this.client.models.generateContent(
                this.geminiModel,
                prompt,
                this.geminiGenerateContentConfig);

        this.logDebug("response: " + response);
        result = response.text();
        if (context.isVerbose()) {
          this.logInfo("response text: " + result);
        }

        if (result == null) {
          throw new IllegalStateException("unexpectedly returned null as response text");
        }

        if (Boolean.getBoolean(PROPERTY_GEMINI_DISABLE_NORMALIZE_RESPONSE)) {
          logWarn("response normalize disabled");
        } else {
          logDebug("normalizing response");
          result = normalizeJavaResponse(result);
        }
        if (result.isBlank()) {
          throw new IllegalStateException("Blank text as result of request");
        }

        this.jaipPromptCacheFile.getCache()
            .put(cacheKey, positionInfo.getFile().getName(), positionInfo.getLineNumber(),
                result);
        this.jaipPromptCacheFile.flush();
      } catch (RuntimeException ex) {
        exception = ex;
        throw ex;
      } finally {
        if (exception != null) {
          logError("can't get response for " + preprocessedFilePosition + " for error: " +
              exception.getMessage());
        } else {
          logInfo(String.format(
              "got response for %s, length %d char(S), spent %d ms",
              preprocessedFilePosition,
              result == null ? -1 : result.length(),
              System.currentTimeMillis() - start)
          );
        }
      }
    } else {
      this.logInfo("detected cached response for prompt at " + preprocessedFilePosition);
    }

    return result;
  }

}
