package com.igormaznitsa.jcpai.providers.openai;

import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcp.utils.PreprocessorUtils;
import com.igormaznitsa.jcpai.commons.AbstractJcpAiProcessor;
import com.igormaznitsa.jcpai.commons.StringUtils;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public class OpenAiJcpAiProcessor extends AbstractJcpAiProcessor {

  public static final String PROPERTY_OPENAI_MODEL = "jcpai.openai.model";
  public static final String PROPERTY_OPENAI_PROJECT = "jcpai.openai.project";
  public static final String PROPERTY_OPENAI_ORG_ID = "jcpai.openai.org.id";
  public static final String PROPERTY_OPENAI_WEBHOOK_SECRET = "jcpai.openai.webhook.secret";
  public static final String PROPERTY_OPENAI_API_KEY = "jcpai.openai.api.key";
  public static final String PROPERTY_OPENAI_BASE_URL = "jcpai.openai.base.url";

  public OpenAiJcpAiProcessor() {
    super();
  }

  private ChatCompletionCreateParams makeMessage(
      final PreprocessorContext context,
      final String model,
      final String prompt) {
    var builder = ChatCompletionCreateParams.builder()
        .addUserMessage(prompt)
        .n(1);

    this.findParamTemperature(context).ifPresent(builder::temperature);
    this.findParamSeed(context).ifPresent(builder::seed);
    this.findParamTopP(context).ifPresent(builder::topP);
    this.findParamMaxTokens(context).ifPresent(builder::maxCompletionTokens);

    findParamInstructionSystem(context).ifPresentOrElse(builder::addSystemMessage,
        () -> builder.addSystemMessage(DEFAULT_SYSTEM_INSTRUCTION));
    if (model != null) {
      builder.model(model);
    } else {
      builder.model(ChatModel.CODEX_MINI_LATEST);
    }

    return builder.build();
  }

  @Override
  public String getProcessorTextId() {
    return "OPENAI";
  }

  private OpenAIClient prepareOpenAiClient(final PreprocessorContext context) {
    var builder = OpenAIOkHttpClient.builder().fromEnv();

    findPreprocessorVar(PROPERTY_OPENAI_API_KEY, context).map(Value::asString)
        .ifPresent(builder::apiKey);
    findPreprocessorVar(PROPERTY_OPENAI_ORG_ID, context).map(Value::asString)
        .ifPresent(builder::organization);
    findPreprocessorVar(PROPERTY_OPENAI_WEBHOOK_SECRET, context).map(Value::asString)
        .ifPresent(builder::webhookSecret);
    findPreprocessorVar(PROPERTY_OPENAI_PROJECT, context).map(Value::asString)
        .ifPresent(builder::project);

    findBaseUrl(PROPERTY_OPENAI_BASE_URL, context).ifPresent(x -> {
      this.logWarn("non-default API base url: " + x);
      builder.baseUrl(x);
    });
    findTimeoutMs(context).ifPresent(x -> builder.timeout(Duration.ofMillis(x)));

    return builder.build();
  }

  @Override
  protected Map<String, Object> getExtraPromptKeyValues(final PreprocessorContext context) {
    return Map.of(
        "distilled", this.isDistillationRequired(context),
        "model", this.findModel(PROPERTY_OPENAI_MODEL, context,
            PreprocessorUtils.extractFilePositionInfo(context)),
        "system", this.findParamInstructionSystem(context).orElse(DEFAULT_SYSTEM_INSTRUCTION)
    );
  }

  @Override
  public String processPrompt(final PreprocessorContext context, final String prompt) {
    final FilePositionInfo positionInfo = PreprocessorUtils.extractFilePositionInfo(context);
    final String sources = StringUtils.asText(positionInfo, true);

    String response;
    final long start = System.currentTimeMillis();
    final OpenAIClient client = this.prepareOpenAiClient(context);
    try {
      final String model = this.findModel(PROPERTY_OPENAI_MODEL, context, positionInfo);
      final ChatCompletionCreateParams messageParams = this.makeMessage(context, model, prompt);
      this.logDebug("Message create params for " + sources + ": " + messageParams);

      this.logInfo(String.format("sending prompt from %s to model %s, max tokens %s",
          sources,
          messageParams.model().asString(),
          messageParams.maxCompletionTokens().map(Object::toString).orElse("DEFAULT")));

      response = client.chat().completions().create(messageParams).choices().stream()
          .flatMap(choice -> choice.message().content().stream()).collect(Collectors.joining());
    } finally {
      client.close();
    }
    final long spent = System.currentTimeMillis() - start;

    this.logDebug("RESPONSE for " + sources + "\n-------------\n" + response + "\n-------------");

    this.logInfo(
        String.format("got response for the prompt at %s, spent %d ms, response %d char(s)",
            sources, spent, response.length()));

    response = this.makeDistillationIfAllowed(context, response);

    if (response.isBlank()) {
      throw new IllegalStateException(
          "Can't find code content in the result of request at " + sources);
    }

    return response;
  }

}
