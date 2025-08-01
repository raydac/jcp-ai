package com.igormaznitsa.jcpai.providers.gemini;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcpai.commons.AbstractJcpAiProcessor;
import java.util.Optional;

public class GeminiJcpAiProcessor extends AbstractJcpAiProcessor {

  public static final String PROPERTY_GEMINI_MODEL = "jcpai.gemini.model";
  public static final String PROPERTY_GEMINI_PROJECT_ID = "jcpai.gemini.project.id";
  public static final String PROPERTY_GEMINI_API_KEY = "jcpai.gemini.api.key";
  public static final String PROPERTY_GEMINI_BASE_URL = "jcpai.gemini.base.url";
  public static final String PROPERTY_GEMINI_GENERATE_CONTENT_CONFIG_JSON =
      "jcpai.gemini.generate.content.config.json";
  public static final String PROPERTY_GEMINI_CLIENT_HTTP_CONFIG_JSON =
      "jcpai.gemini.http.config.json";
  public static final String PROPERTY_GEMINI_CLIENT_OPTIONS_JSON =
      "jcpai.gemini.client.options.json";

  public GeminiJcpAiProcessor() {
    super();
  }

  private GenerateContentConfig makeDefaultGenerateContentConfig(
      final PreprocessorContext context) {
    var builder = GenerateContentConfig.builder()
        .candidateCount(1)
        .responseModalities("TEXT");

    builder.temperature(this.findParamTemperature(context).orElse(0.15f));
    this.findParamTopP(context).ifPresent(builder::topP);
    this.findParamTopK(context).ifPresent(builder::topK);
    this.findParamSeed(context).map(Long::intValue).ifPresent(builder::seed);
    this.findParamMaxTokens(context).map(Long::intValue).ifPresent(builder::maxOutputTokens);
    this.findParamInstructionSystem(context).ifPresentOrElse(x ->
        builder.systemInstruction(Content.builder()
            .role("model")
            .parts(Part.builder().text(x).build())
            .build()), () ->
        builder.systemInstruction(Content.builder()
            .role("model")
            .parts(Part.builder().text(DEFAULT_SYSTEM_INSTRUCTION).build())
            .build())
    );
    return builder.build();
  }

  @Override
  public String getProcessorTextId() {
    return "GEMINI";
  }

  private Client prepareGeminiClient(final PreprocessorContext context) {
    final Client.Builder builder = Client.builder();

    findPreprocessorVar(PROPERTY_GEMINI_CLIENT_OPTIONS_JSON, context)
        .map(Value::asString).ifPresent(
            clientOptionsJson -> builder.clientOptions(ClientOptions.fromJson(clientOptionsJson)));

    final Optional<Long> timeout = findTimeoutMs(context);
    final Optional<String> baseUrl = findBaseUrl(PROPERTY_GEMINI_BASE_URL, context);

    if (timeout.isPresent() || baseUrl.isPresent()) {
      final HttpOptions.Builder httpBuilder = HttpOptions.builder();

      timeout.ifPresent(x -> httpBuilder.timeout(x.intValue()));
      baseUrl.ifPresent(x -> {
        this.logDebug("detected provided base url: " + x);
        httpBuilder.baseUrl(x);
      });

      builder.httpOptions(httpBuilder.build());
    }

    findPreprocessorVar(PROPERTY_GEMINI_CLIENT_HTTP_CONFIG_JSON, context)
        .map(Value::asString).ifPresent(
            httpOptionsJson -> builder.httpOptions(HttpOptions.fromJson(httpOptionsJson)));

    findPreprocessorVar(PROPERTY_GEMINI_API_KEY, context).map(Value::asString)
        .ifPresent(builder::apiKey);

    findPreprocessorVar(PROPERTY_GEMINI_PROJECT_ID, context).map(Value::asString)
        .ifPresent(builder::project);

    return builder.build();
  }

  @Override
  public String processPrompt(
      final String prompt,
      final FileInfoContainer sourceFileContainer,
      final FilePositionInfo positionInfo,
      final PreprocessorContext context,
      final PreprocessingState state) {

    final String sources = positionInfo.getFile().getName() + ':' + positionInfo.getLineNumber();

    final GenerateContentConfig generateContentConfig =
        findPreprocessorVar(PROPERTY_GEMINI_GENERATE_CONTENT_CONFIG_JSON, context)
            .map(x -> {
              final String json = x.asString();
              this.logDebug(
                  "detected generate content config json for " + positionInfo + ": " + json);
              return GenerateContentConfig.fromJson(json);
            })
            .orElseGet(() -> this.makeDefaultGenerateContentConfig(context));

    this.logDebug(String.format("prepared generate content config for %s: %s", sources,
        generateContentConfig.toJson()));

    final String geminiModel = findPreprocessorVar(PROPERTY_GEMINI_MODEL, context)
        .map(Value::asString)
        .orElseThrow(() -> new IllegalStateException(
            "Can't find defined Gemini model name through " + PROPERTY_GEMINI_MODEL + " at " +
                sources));

    logInfo(String.format("sending prompt from %s, model is %s, max tokens %s", sources,
        geminiModel,
        generateContentConfig.maxOutputTokens().map(Object::toString).orElse("DEFAULT")));

    final GenerateContentResponse response;
    final long start = System.currentTimeMillis();
    try (final Client client = this.prepareGeminiClient(context)) {
      response =
          client.models.generateContent(
              geminiModel,
              prompt,
              generateContentConfig);

    }
    final long spent = System.currentTimeMillis() - start;

    final String executableCode = response.executableCode();
    String result;
    if (executableCode == null) {
      result = response.text();
    } else {
      logDebug("detected executable code with text: " + response.text());
      result = executableCode;
    }
    this.logDebug("RESPONSE for " + sources + "\n-------------\n" + result + "\n-------------");

    if (result == null) {
      throw new NullPointerException("Unexpectedly returned null as response text at " + sources);
    }
    logInfo(String.format("got response for the prompt at %s, spent %d ms, response %d char(s)",
        sources, spent, result.length()));

    result = this.makeResponseDistillation(context, result);

    if (result.isBlank()) {
      throw new IllegalStateException(
          "Can't find code content in the result of request at " + sources);
    }

    return result;
  }

}
