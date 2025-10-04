package com.igormaznitsa.jcpai.providers.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcp.utils.PreprocessorUtils;
import com.igormaznitsa.jcpai.commons.AbstractJcpAiProcessor;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public class AnthropicJcpAiProcessor extends AbstractJcpAiProcessor {

  public static final String PROPERTY_ANTHROPIC_MODEL = "jcpai.anthropic.model";
  public static final String PROPERTY_ANTHROPIC_AUTH_TOKEN = "jcpai.anthropic.auth.token";
  public static final String PROPERTY_ANTHROPIC_API_KEY = "jcpai.anthropic.api.key";
  public static final String PROPERTY_ANTHROPIC_BASE_URL = "jcpai.anthropic.base.url";

  public AnthropicJcpAiProcessor() {
    super();
  }

  private MessageCreateParams makeMessage(
      final PreprocessorContext context,
      final String model,
      final String prompt) {
    var builder = MessageCreateParams.builder();

    this.findParamTemperature(context)
        .ifPresentOrElse(builder::temperature, () -> builder.temperature(0.15f));
    this.findParamTopP(context).ifPresent(builder::topP);
    this.findParamTopK(context).map(Float::longValue).ifPresent(builder::topK);

    this.findParamInstructionSystem(context)
        .ifPresentOrElse(builder::system, () -> builder.system(DEFAULT_SYSTEM_INSTRUCTION));
    this.findParamMaxTokens(context)
        .ifPresentOrElse(builder::maxTokens, () -> builder.maxTokens(4096));

    builder.addUserMessage(prompt);

    if (model != null) {
      builder.model(model);
    } else {
      builder.model(Model.CLAUDE_SONNET_4_0);
    }

    return builder.build();
  }

  @Override
  public String getProcessorTextId() {
    return "ANTHROPIC";
  }

  @Override
  protected Map<String, Object> getExtraPromptKeyValues(
      final PreprocessorContext context) {
    final FilePositionInfo positionInfo = PreprocessorUtils.extractFilePositionInfo(context);
    return Map.of(
        "distilled", this.isDistillationRequired(context),
        "model", this.findModel(PROPERTY_ANTHROPIC_MODEL, context, positionInfo),
        "system", this.findParamInstructionSystem(context).orElse(DEFAULT_SYSTEM_INSTRUCTION)
    );
  }

  private AnthropicClient prepareAnthropicClient(final PreprocessorContext context) {
    var builder = AnthropicOkHttpClient.builder().fromEnv();

    findPreprocessorVar(PROPERTY_ANTHROPIC_API_KEY, context).map(Value::asString)
        .ifPresent(builder::apiKey);
    findPreprocessorVar(PROPERTY_ANTHROPIC_AUTH_TOKEN, context).map(Value::asString)
        .ifPresent(builder::authToken);

    findBaseUrl(PROPERTY_ANTHROPIC_BASE_URL, context).ifPresent(x -> {
      this.logWarn("non-default API base url: " + x);
      builder.baseUrl(x);
    });
    findTimeoutMs(context)
        .ifPresent(x -> builder.timeout(Duration.ofMillis(x)));

    return builder.build();
  }

  @Override
  public String processPrompt(
      final PreprocessorContext context,
      final String prompt) {
    final FilePositionInfo positionInfo = PreprocessorUtils.extractFilePositionInfo(context);
    final String sources = positionInfo.getFile().getName() + ':' + positionInfo.getLineNumber();

    final Message response;
    final long start = System.currentTimeMillis();
    final AnthropicClient client = this.prepareAnthropicClient(context);
    try {
      final String model = this.findModel(PROPERTY_ANTHROPIC_MODEL, context, positionInfo);

      final MessageCreateParams message = makeMessage(context, model, prompt);
      this.logDebug("Message create params: " + message);
      logInfo(String.format("sending prompt from %s, model is %s, max tokens %d", sources,
          message.model().asString(), message.maxTokens()));
      response =
          client.messages().create(makeMessage(context, model, prompt));

    } finally {
      client.close();
    }
    final long spent = System.currentTimeMillis() - start;

    String result =
        response.content().stream().map(x -> x.text().map(TextBlock::text).orElse("")).collect(
            Collectors.joining("\n"));
    this.logDebug("RESPONSE\n-------------\n" + result + "\n-------------");

    this.logInfo(
        String.format("got response for the prompt at %s, spent %d ms, response %d char(s)",
            sources, spent, result.length()));
    result = this.makeDistillationIfAllowed(context, result);

    if (result.isBlank()) {
      throw new IllegalStateException(
          "Can't find code content in the result of request at " + sources);
    }

    return result;
  }

}
