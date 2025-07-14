package com.igormaznitsa.jaip.providers.gemini;

import static java.lang.String.join;

import com.google.common.collect.Streams;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.CommentTextProcessor;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.logger.PreprocessorLogger;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeminiProcessorService implements CommentTextProcessor {

  public static final String PROPERTY_GEMINI_MODEL = "jaip.gemini.model";
  public static final String PROPERTY_GEMINI_API_KEY = "jaip.gemini.api.key";
  public static final String PROPERTY_GEMINI_GENERATE_CONFIG_JSON =
      "japi.gemini.model.generate.config.json";

  private Client client;
  private String geminiModel;
  private GenerateContentConfig geminiGenerateContentConfig;
  private PreprocessorLogger logger;

  private static GenerateContentConfig makeDefaultGenerateContentConfig() {
    return GenerateContentConfig.builder()
        .temperature(0.0f)
        .topP(0.95f)
        .topK(40.0f)
        .maxOutputTokens(16384)
        .stopSequences("```")
        .build();
  }

  private static String findPropertyNonNullableValue(final String property,
                                                     final String defaultValue) {
    final String result = System.getProperty(property, defaultValue);
    if (result == null) {
      throw new IllegalStateException("Can't find property: " + property);
    }
    return result;
  }

  @Override
  public void onContextStarted(final PreprocessorContext context) {
    this.geminiModel = findPropertyNonNullableValue(PROPERTY_GEMINI_MODEL, null);
    this.client = Client.builder()
        .apiKey(findPropertyNonNullableValue(PROPERTY_GEMINI_API_KEY, null))
        .build();
    this.logger = context.getPreprocessorLogger();
    this.logger.info("Init GEMINI client");

    this.logger.info("GEMINI model in use: " + this.geminiModel);

    final String generateContentConfigJson =
        System.getProperty(PROPERTY_GEMINI_GENERATE_CONFIG_JSON);
    if (generateContentConfigJson == null) {
      this.geminiGenerateContentConfig = makeDefaultGenerateContentConfig();
    } else {
      this.geminiGenerateContentConfig = GenerateContentConfig.fromJson(generateContentConfigJson);
    }
  }

  @Override
  public void onContextStopped(final PreprocessorContext context, Throwable error) {
    try {
      this.client.close();
    } finally {
      this.logger.info("GEMINI client stopped");
    }
  }

  private static final String JAIP_PROMPT_PREFIX = "JAIP>";

  private static List<String> extreactPrefixLines(final String[] textLines) {
    return Arrays.stream(textLines).takeWhile(x -> !leftTrim(x).startsWith(JAIP_PROMPT_PREFIX))
        .collect(Collectors.toList());
  }

  private static String leftTrim(final String text) {
    int i = 0;
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      i++;
    }
    return text.substring(i);
  }

  private static List<String> extreactPrompt(final List<String> prefix, final String[] textLines) {
    return Arrays.stream(textLines).skip(prefix.size())
        .map(GeminiProcessorService::leftTrim)
        .takeWhile(x -> x.startsWith(JAIP_PROMPT_PREFIX))
        .map(x -> x.substring(JAIP_PROMPT_PREFIX.length()))
        .collect(Collectors.toList());
  }

  private static List<String> extreactPostfixLines(final List<String> prefix,
                                                   final List<String> prompt,
                                                   final String[] textLines) {
    final List<String> result = Arrays.stream(textLines).skip(prefix.size() + prompt.size())
        .collect(Collectors.toList());
    if (result.stream().anyMatch(x -> leftTrim(x).startsWith(JAIP_PROMPT_PREFIX))) {
      throw new IllegalArgumentException("Detected unexpected mix of prompt and postfix lines");
    }
    return result;
  }

  private String makeRequest(final PreprocessorContext context, final String prompt) {
    final long start = System.currentTimeMillis();

    if (context.isVerbose()) {
      this.logger.info("Sending prompt to the GEMINI model (" + this.geminiModel + "): " + prompt);
    } else {
      this.logger.debug("Starting GEMINI generation model (" + this.geminiModel + ")");
    }
    final GenerateContentResponse response =
        this.client.models.generateContent(this.geminiModel, prompt,
            this.geminiGenerateContentConfig);

    this.logger.debug("GEMINI response code execution result: " + response.codeExecutionResult());
    if (context.isVerbose()) {
      this.logger.info(
          "Completed GEMINI request, spent " + (System.currentTimeMillis() - start) + "ms");
    }

    return response.text();
  }

  @Override
  public String onUncommentText(final String text, FileInfoContainer fileInfoContainer,
                                PreprocessorContext preprocessorContext,
                                PreprocessingState preprocessingState) throws IOException {
    final String[] lines = text.split("\\R");

    final List<String> prefix = extreactPrefixLines(lines);
    final List<String> prompt = extreactPrompt(prefix, lines);
    final List<String> postfix = extreactPostfixLines(prefix, prompt, lines);

    if (prefix.size() + prompt.size() + postfix.size() != lines.length) {
      throw new IllegalStateException("Unexpectedly non-equal number of extracted lines: " + text);
    }

    return Streams.concat(
            prefix.stream(),
            Stream.of(join("\n", prompt)).takeWhile(x -> !x.isBlank())
                .map(x -> makeRequest(preprocessorContext, x)),
            postfix.stream())
        .collect(Collectors.joining(preprocessorContext.getEol()));
  }
}
