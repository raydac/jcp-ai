package com.igormaznitsa.jaip.providers.gemini;

import static com.igormaznitsa.jaip.common.StringUtils.findPropertyNonNullableValue;
import static com.igormaznitsa.jaip.common.cache.JaipPromptCacheFile.PROPERTY_JAIP_PROMPT_CACHE_FILE;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.igormaznitsa.jaip.common.AbstractJaipProcessor;
import com.igormaznitsa.jaip.common.cache.JaipPromptCacheFile;
import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class GeminiProcessorService extends AbstractJaipProcessor {

  public static final String PROPERTY_GEMINI_MODEL = "jaip.gemini.model";
  public static final String PROPERTY_GEMINI_API_KEY = "jaip.gemini.api.key";
  public static final String PROPERTY_GEMINI_GENERATE_CONFIG_JSON =
      "jaip.gemini.model.generate.config.json";

  private Client client;
  private String geminiModel;
  private GenerateContentConfig geminiGenerateContentConfig;

  private JaipPromptCacheFile jaipPromptCacheFile;

  private static final MessageDigest MD5_DIGEST;

  static {
    try {
      MD5_DIGEST = MessageDigest.getInstance("MD5");
    } catch (Exception ex) {
      throw new Error("Can't find MD5 digest", ex);
    }
  }

  public GeminiProcessorService() {
    super();
  }

  private static GenerateContentConfig makeDefaultGenerateContentConfig() {
    return GenerateContentConfig.builder()
        .temperature(0.2f)
        .topP(0.95f)
        .topK(40.0f)
        .maxOutputTokens(128 * 1024)
        .systemInstruction(Content.builder()
            .role("model")
            .parts(Part.builder().text("you are a Java code generator").build())
            .build())
        .stopSequences("```")
        .seed(823746)
        .responseModalities("TEXT")
        .build();
  }

  @Override
  public String getName() {
    return "GEMINI";
  }

  @Override
  protected void doContextStarted(PreprocessorContext context) {
    this.geminiModel = findPropertyNonNullableValue(PROPERTY_GEMINI_MODEL, null);
    this.client = Client.builder()
        .apiKey(findPropertyNonNullableValue(PROPERTY_GEMINI_API_KEY, null))
        .build();
    logInfo("model in use: " + this.geminiModel);

    final String generateContentConfigJson =
        System.getProperty(PROPERTY_GEMINI_GENERATE_CONFIG_JSON);
    if (generateContentConfigJson == null) {
      this.geminiGenerateContentConfig = makeDefaultGenerateContentConfig();
    } else {
      this.geminiGenerateContentConfig = GenerateContentConfig.fromJson(generateContentConfigJson);
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
  }

  @Override
  protected void doContextStopped(PreprocessorContext context, Throwable error) {
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

  private static String makeKey(final String model, final String prompt) {
    return Base64.getEncoder()
        .encodeToString(MD5_DIGEST.digest((model + prompt).getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public String doRequestForPrompt(
      final String prompt,
      final FileInfoContainer fileInfoContainer,
      final PreprocessorContext context,
      final PreprocessingState state) {
    final String cacheKey = makeKey(this.geminiModel, prompt);

    String result = this.jaipPromptCacheFile == null ? null :
        this.jaipPromptCacheFile.getCache().find(cacheKey);

    if (result == null) {
      final GenerateContentResponse response =
          this.client.models.generateContent(this.geminiModel, prompt,
              this.geminiGenerateContentConfig);

      this.logDebug("response: " + response);

      result = response.text();
      if (context.isVerbose()) {
        this.logInfo("response text: " + result);
      }

      if (result == null) {
        throw new IllegalStateException("unexpectedly returned null as response text");
      }
      if (result.trim().isEmpty()) {
        throw new IllegalStateException("unexpectedly returned empty response");
      }

      this.jaipPromptCacheFile.getCache().put(cacheKey, result);
      this.jaipPromptCacheFile.flush();
    } else {
      this.logDebug("response found in cache file");
    }

    return result;
  }

}
