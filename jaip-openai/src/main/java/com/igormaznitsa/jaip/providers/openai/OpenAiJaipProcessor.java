package com.igormaznitsa.jaip.providers.openai;

import static com.igormaznitsa.jaip.common.StringUtils.normalizeJavaResponse;

import com.igormaznitsa.jaip.common.AbstractJaipProcessor;
import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcp.expression.ValueType;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.util.stream.Collectors;

public class OpenAiJaipProcessor extends AbstractJaipProcessor {

  public static final String PROPERTY_OPENAI_MODEL = "jaip.openai.model";
  public static final String PROPERTY_OPENAI_MAX_TOKENS = "jaip.openai.max.tokens";
  public static final String PROPERTY_OPENAI_PROJECT = "jaip.openai.project";
  public static final String PROPERTY_OPENAI_ORG_ID = "jaip.openai.org.id";
  public static final String PROPERTY_OPENAI_WEBHOOK_SECRET = "jaip.openai.webhook.secret";
  public static final String PROPERTY_OPENAI_API_KEY = "jaip.openai.api.key";
  public static final String PROPERTY_OPENAI_BASE_URL = "jaip.openai.base.url";
  public static final long MAX_TOKENS_BY_DEFAULT = 4096;

  public OpenAiJaipProcessor() {
    super();
  }

  private static ChatCompletionCreateParams makeMessage(
      final String model,
      final long maxTokens,
      final String message) {
    var builder = ChatCompletionCreateParams.builder()
        .temperature(0.1f)
        .topP(0.95f)
        .maxCompletionTokens(maxTokens)
        .addSystemMessage(
            "You are a highly skilled senior software engineer with deep expertise in algorithms and advanced Java development, including core Java concepts and best practices. Respond with precise, efficient, and idiomatic Java solutions, and explain your reasoning when needed.")
        .addUserMessage(message);

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

    findPreprocessorVar(PROPERTY_OPENAI_BASE_URL, context).map(Value::asString)
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

    String response;
    final long start = System.currentTimeMillis();
    final OpenAIClient client = this.prepareOpenAiClient(context);
    try {
      final String model = findPreprocessorVar(PROPERTY_OPENAI_MODEL, context).map(
          Value::asString).orElse(null);
      final String webhookSecret = findPreprocessorVar(PROPERTY_OPENAI_WEBHOOK_SECRET, context).map(
          Value::asString).orElse(null);
      final long maxTokens = findPreprocessorVar(PROPERTY_OPENAI_MAX_TOKENS, context)
          .map(x -> {
            if (x.getType() != ValueType.INT) {
              throw new IllegalArgumentException(
                  "expected integer value for " + PROPERTY_OPENAI_MAX_TOKENS + " but found " +
                      x.getType() + " : " + x.asString());
            }
            return x.asLong();
          }).orElse(MAX_TOKENS_BY_DEFAULT);

      logInfo(String.format("sending prompt from %s, model is %s, max tokens %d", sources,
          (model == null ? "DEFAULT" : model), maxTokens));

      final ChatCompletionCreateParams messageParams = makeMessage(model, maxTokens, prompt);
      this.logDebug("Message create params: " + messageParams);

      response = client.chat().completions().create(messageParams).choices().stream()
          .flatMap(choice -> choice.message().content().stream()).collect(Collectors.joining());
    } finally {
      client.close();
    }
    final long spent = System.currentTimeMillis() - start;

    this.logDebug("RESPONSE\n-------------\n" + response + "\n-------------");

    this.logInfo(
        String.format("got response for the prompt at %s, spent %d ms, response %d char(s)",
            sources, spent, response.length()));
    response = normalizeJavaResponse(response);

    if (response.isBlank()) {
      throw new IllegalStateException(
          "Can't find code content in the result of request at " + sources);
    }

    return response;
  }

}
