package com.igormaznitsa.jaip.common;

import static com.igormaznitsa.jaip.common.StringUtils.JAIP_PROMPT_PREFIX;
import static com.igormaznitsa.jaip.common.StringUtils.leftTrim;
import static java.lang.String.join;

import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.CommentTextProcessor;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.logger.PreprocessorLogger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract processor to prepare answer from a prompt.
 *
 * @since 1.0.0
 */
public abstract class AbstractJaipProcessor implements CommentTextProcessor {

  private PreprocessorLogger logger;

  protected static List<String> extreactPrefixLines(final String[] textLines) {
    return Arrays.stream(textLines).takeWhile(x -> !leftTrim(x).startsWith(JAIP_PROMPT_PREFIX))
        .collect(Collectors.toList());
  }

  protected static List<String> extreactPrompt(final List<String> prefix,
                                               final String[] textLines) {
    return Arrays.stream(textLines).skip(prefix.size())
        .map(StringUtils::leftTrim)
        .takeWhile(x -> x.startsWith(JAIP_PROMPT_PREFIX))
        .map(x -> x.substring(JAIP_PROMPT_PREFIX.length()))
        .collect(Collectors.toList());
  }

  protected static List<String> extreactPostfixLines(final List<String> prefix,
                                                     final List<String> prompt,
                                                     final String[] textLines) {
    final List<String> result = Arrays.stream(textLines).skip(prefix.size() + prompt.size())
        .collect(Collectors.toList());
    if (result.stream().anyMatch(x -> leftTrim(x).startsWith(JAIP_PROMPT_PREFIX))) {
      throw new IllegalArgumentException("Detected unexpected mix of prompt and postfix lines");
    }
    return result;
  }

  @Override
  public final void onContextStarted(PreprocessorContext context) {
    this.logger = context.getPreprocessorLogger();
    logInfo("init processor");
    this.onProcessorStarted(context);
  }

  protected void logInfo(String text) {
    if (this.logger != null) {
      this.logger.info(this.getProcessorTextId() + ": " + text);
    }
  }

  protected void logDebug(String text) {
    if (this.logger != null) {
      this.logger.debug(this.getProcessorTextId() + ": " + text);
    }
  }

  protected void logError(String text) {
    if (this.logger != null) {
      this.logger.error(this.getProcessorTextId() + ": " + text);
    }
  }

  protected void logWarn(String text) {
    if (this.logger != null) {
      this.logger.warning(this.getProcessorTextId() + ": " + text);
    }
  }

  protected void onProcessorStarted(PreprocessorContext context) {

  }

  @Override
  public final void onContextStopped(
      final PreprocessorContext context,
      final Throwable error) {
    if (error == null) {
      logInfo("stopping processor");
    } else {
      logError("stopping processor with error: " + error.getMessage());
    }
    this.onProcessorStopped(context, error);
    this.logger = null;
  }

  protected void onProcessorStopped(
      PreprocessorContext context,
      Throwable error) {

  }

  @Override
  public final String processUncommentedText(
      final int recommendedIndent,
      final String uncommentedText,
      final FileInfoContainer sourceFileContainer,
      final FilePositionInfo positionInfo,
      final PreprocessorContext context,
      final PreprocessingState state
  ) {
    logDebug("Incoming potential prompt: " + uncommentedText);

    final String[] lines = uncommentedText.split("\\R");

    final String indent =
        context.isPreserveIndents() ? " ".repeat(recommendedIndent) : "";

    final List<String> prefix = extreactPrefixLines(lines);
    final List<String> prompt = extreactPrompt(prefix, lines);
    final List<String> postfix = extreactPostfixLines(prefix, prompt, lines);

    if (prefix.size() + prompt.size() + postfix.size() != lines.length) {
      throw new IllegalStateException(
          "Unexpectedly non-equal number of extracted lines: " + uncommentedText);
    }

    final long start = System.currentTimeMillis();
    try {
      return Stream.concat(
              prefix.stream(),
              Stream.concat(
                  Stream.of(join("\n", prompt))
                      .takeWhile(x -> !x.isBlank())
                      .peek(x -> logDebug("prepared prompt part: " + x))
                      .map(x -> this.processPrompt(x,
                          sourceFileContainer, positionInfo,
                          context,
                          state
                          )
                      ),
                  postfix.stream()))
          .flatMap(x -> Arrays.stream(x.split("\\R")))
          .map(x -> indent + x)
          .collect(
              Collectors.joining(context.getEol(), "", context.getEol()));
    } finally {
      this.logDebug("completed prompt, spent " + (System.currentTimeMillis() - start) + "ms");
    }
  }

  /**
   * Process prompt and generate text to replace the prompt in sources.
   *
   * @param prompt              the prompt text, must not be null
   * @param sourceFileContainer the source file container processing the source file, must not be null
   * @param positionInfo        the text position in the source file, must not be null, if it is the text block then the first text block line as the position.
   * @param context             the current preprocessor context, must not be null
   * @param state               the current preprocessor state, must not be null
   * @return the generated response as single line or multi-line text, must not be null
   * @since 1.0.0
   */
  public abstract String processPrompt(
      String prompt,
      FileInfoContainer sourceFileContainer,
      FilePositionInfo positionInfo,
      PreprocessorContext context,
      PreprocessingState state);

  /**
   * Get the processor text id. It will be used as log prefix and in other operations requiring id of the processor.
   *
   * @return the processor name, must not be null
   * @since 1.0.0
   */
  public abstract String getProcessorTextId();
}
