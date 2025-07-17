package com.igormaznitsa.jaip.providers.anthropic;

import static com.igormaznitsa.jaip.common.StringUtils.normalizeJavaResponse;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.igormaznitsa.jaip.common.AbstractJaipProcessor;
import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcp.expression.ValueType;
import java.util.stream.Collectors;

public class AnthropicJaipProcessor extends AbstractJaipProcessor {

  public static final String PROPERTY_ANTHROPIC_MODEL = "jaip.anthropic.model";
  public static final String PROPERTY_ANTHROPIC_MAX_TOKENS = "jaip.anthropic.max.tokens";
  public static final String PROPERTY_ANTHROPIC_AUTH_TOKEN = "jaip.anthropic.auth.token";
  public static final String PROPERTY_ANTHROPIC_API_KEY = "jaip.anthropic.api.key";
  public static final String PROPERTY_ANTHROPIC_BASE_URL = "jaip.anthropic.base.url";
  public static final long MAX_TOKENS_BY_DEFAULT = 4096;

  public AnthropicJaipProcessor() {
    super();
  }

  private MessageCreateParams makeMessage(
      final String model,
      final long maxTokens,
      final String message) {
    var builder = MessageCreateParams.builder()
        .temperature(0.1f)
        .topP(0.95f)
        .maxTokens(maxTokens)
        .system(
            "You are a highly skilled senior software engineer with deep expertise in algorithms and advanced Java development, including core Java concepts and best practices. Respond with precise, efficient, and idiomatic Java solutions, and explain your reasoning when needed.")
        .addUserMessage(message);

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

  private AnthropicClient prepareGClient(final PreprocessorContext context) {
    var builder = AnthropicOkHttpClient.builder().fromEnv();

    findPreprocessorVar(PROPERTY_ANTHROPIC_API_KEY, context).map(Value::asString)
        .ifPresent(builder::apiKey);
    findPreprocessorVar(PROPERTY_ANTHROPIC_AUTH_TOKEN, context).map(Value::asString)
        .ifPresent(builder::authToken);

    findPreprocessorVar(PROPERTY_ANTHROPIC_BASE_URL, context).map(Value::asString)
        .ifPresent(builder::baseUrl);

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

    final Message response;
    final long start = System.currentTimeMillis();
    final AnthropicClient client = this.prepareGClient(context);
    try {
      final String model = findPreprocessorVar(PROPERTY_ANTHROPIC_MODEL, context).map(
          Value::asString).orElse(null);
      final long maxTokens = findPreprocessorVar(PROPERTY_ANTHROPIC_MAX_TOKENS, context)
          .map(x -> {
            if (x.getType() != ValueType.INT) {
              throw new IllegalArgumentException(
                  "expected integer value for " + PROPERTY_ANTHROPIC_MAX_TOKENS + " but found " +
                      x.getType() + " : " + x.asString());
            }
            return x.asLong();
          }).orElse(MAX_TOKENS_BY_DEFAULT);

      logInfo(String.format("sending prompt from %s, model is %s, max tokens %d", sources,
          (model == null ? "DEFAULT" : model), maxTokens));
      response =
          client.messages().create(makeMessage(model, maxTokens, prompt));

    } finally {
      client.close();
    }
    final long spent = System.currentTimeMillis() - start;

    String result =
        response.content().stream().map(x -> x.text().map(TextBlock::text).orElse("")).collect(
            Collectors.joining(context.getEol()));

    this.logDebug("RESULT\n-------------\n" + result + "\n-------------");

    this.logInfo(
        String.format("got response for the prompt at %s, spent %d ms, response %d char(s)",
            sources, spent, result.length()));
    result = normalizeJavaResponse(result);
    if (result.isBlank()) {
      throw new IllegalStateException(
          "Can't find code content in the result of request at " + sources);
    }

    return result;
  }

}
