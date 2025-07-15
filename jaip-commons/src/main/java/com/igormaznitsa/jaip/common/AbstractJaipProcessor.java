package com.igormaznitsa.jaip.common;

import static com.igormaznitsa.jaip.common.StringUtils.JAIP_PROMPT_PREFIX;
import static com.igormaznitsa.jaip.common.StringUtils.leftTrim;
import static java.lang.String.join;

import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.CommentTextProcessor;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.logger.PreprocessorLogger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    logInfo("Init client");
    this.doContextStarted(context);
  }

  protected void logInfo(String text) {
    if (this.logger != null) {
      this.logger.info(this.getName() + ": " + text);
    }
  }

  protected void logDebug(String text) {
    if (this.logger != null) {
      this.logger.debug(this.getName() + ": " + text);
    }
  }

  protected void logError(String text) {
    if (this.logger != null) {
      this.logger.error(this.getName() + ": " + text);
    }
  }

  protected void logWarn(String text) {
    if (this.logger != null) {
      this.logger.warning(this.getName() + ": " + text);
    }
  }

  protected void doContextStarted(PreprocessorContext context) {

  }

  @Override
  public final void onContextStopped(PreprocessorContext context, Throwable error) {
    logInfo("Stopping client");
    this.doContextStopped(context, error);
    this.logger = null;
  }

  protected void doContextStopped(PreprocessorContext context, Throwable error) {

  }

  @Override
  public final String onUncommentText(
      final int firstLineIndent,
      final String text,
      final FileInfoContainer fileInfoContainer,
      final PreprocessorContext preprocessorContext,
      final PreprocessingState preprocessingState) {

    logDebug("Incoming potential prompt: " + text);

    final String[] lines = text.split("\\R");

    final String indent = preprocessorContext.isPreserveIndents() ?  " ".repeat(firstLineIndent) : "";

    final List<String> prefix = extreactPrefixLines(lines);
    final List<String> prompt = extreactPrompt(prefix, lines);
    final List<String> postfix = extreactPostfixLines(prefix, prompt, lines);

    if (prefix.size() + prompt.size() + postfix.size() != lines.length) {
      throw new IllegalStateException("Unexpectedly non-equal number of extracted lines: " + text);
    }

    final long start = System.currentTimeMillis();
    try {
      final String result = Stream.concat(
              prefix.stream(),
              Stream.concat(
                  Stream.of(join("\n", prompt))
                      .takeWhile(x -> !x.isBlank())
                      .peek(x -> logDebug("prepared prompt part: " + x))
                      .map(x -> this.doRequestForPrompt(x, fileInfoContainer, preprocessorContext,
                          preprocessingState)),
                  postfix.stream()))
          .flatMap(x -> Arrays.stream(x.split("\\R")))
          .map(x -> indent + x)
          .collect(Collectors.joining(preprocessorContext.getEol(), "",  preprocessorContext.getEol()));
      return result;
    } finally {
      if (preprocessorContext.isVerbose()) {
        this.logInfo("completed prompt, spent " + (System.currentTimeMillis() - start) + "ms");
      }
    }
  }

  public abstract String doRequestForPrompt(
      String prompt,
      FileInfoContainer fileInfoContainer,
      PreprocessorContext context,
      PreprocessingState state);

  public abstract String getName();
}
