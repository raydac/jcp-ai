package com.igormaznitsa.jaip.common;

import static com.igormaznitsa.jaip.common.StringUtils.JAIP_PROMPT_PREFIX;
import static com.igormaznitsa.jaip.common.StringUtils.leftTrim;
import static java.lang.String.join;

import com.igormaznitsa.jaip.common.cache.JaipPromptCacheFile;
import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.CommentTextProcessor;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcp.expression.ValueType;
import com.igormaznitsa.jcp.logger.PreprocessorLogger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract processor to prepare answer from a prompt.
 *
 * @since 1.0.0
 */
public abstract class AbstractJaipProcessor implements CommentTextProcessor {

  public static final String PROPERTY_JAIP_PROMPT_CACHE = "jaip.prompt.cache.file";
  private static final MessageDigest SHA512_DIGEST;
  private static final MessageDigest MD5_DIGEST;

  static {
    try {
      SHA512_DIGEST = MessageDigest.getInstance("SHA-512");
      MD5_DIGEST = MessageDigest.getInstance("MD5");
    } catch (Exception ex) {
      throw new Error("Can't find or instantiate a digest provider", ex);
    }
  }

  private final Map<File, JaipPromptCacheFile> promptFiles = new ConcurrentHashMap<>();
  private PreprocessorLogger logger;

  private static String makeCachePromptKey(final String prompt) {
    final byte[] promptBytes = prompt.getBytes(StandardCharsets.UTF_8);
    final byte[] md5 = MD5_DIGEST.digest(promptBytes);
    final byte[] sha512 = SHA512_DIGEST.digest(promptBytes);
    final byte[] aggregated = new byte[md5.length + sha512.length];
    System.arraycopy(md5, 0, aggregated, 0, md5.length);
    System.arraycopy(sha512, 0, aggregated, md5.length, sha512.length);
    return Base64.getEncoder().encodeToString(aggregated);
  }

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

  protected static Optional<Value> findPreprocessorVar(final String varName,
                                                       final PreprocessorContext context) {
    final Value localValue = context.getLocalVariable(varName);
    final Value globalValue = context.getGlobalVarTable().get(varName);

    Value result = null;
    if (localValue != null) {
      result = localValue;
    }
    if (globalValue != null) {
      result = globalValue;
    }
    return Optional.ofNullable(result);
  }

  private static File findPromptCacheFile(final PreprocessorContext context,
                                          final PreprocessingState state) {
    final Value value = findPreprocessorVar(PROPERTY_JAIP_PROMPT_CACHE, context).orElse(null);
    if (value == null) {
      return null;
    }
    if (value.getType() != ValueType.STRING) {
      throw new IllegalArgumentException(
          "Detected non-string value for " + PROPERTY_JAIP_PROMPT_CACHE +
              " in the preprocessor context");
    }

    final String path = value.asString();

    final File pathFile = new File(path);
    final File file;
    if (pathFile.isAbsolute()) {
      file = pathFile;
    } else {
      file = new File(state.peekFile().getFile().getParentFile(), pathFile.getPath());
    }
    if (context.isFileInBaseDir(file)) {
      return file;
    } else {
      throw new SecurityException(
          "Detected attempt to get access or created a prompt cache file outside of the project, check your code: "
              + file.getAbsolutePath());
    }
  }

  @Override
  public final void onContextStarted(PreprocessorContext context) {
    this.logger = context.getPreprocessorLogger();
    this.promptFiles.clear();

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
      logInfo(
          "stopping processor, cached prompt map contains " + this.promptFiles.size() + " file(s)");
    } else {
      logError("stopping processor with error: " + error.getMessage());
    }

    this.promptFiles.values()
        .forEach(x -> {
          try {
            if (x.flush()) {
              logInfo("Written prompt cache file: " + x.getPath());
            }
          } catch (IOException ex) {
            logError("Can't flush prompt cache file " + x.getPath() + " : " + ex.getMessage());
          }
        });
    this.promptFiles.clear();

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

    final File currentPromptCache = findPromptCacheFile(context, state);
    final JaipPromptCacheFile cacheFile;
    if (currentPromptCache == null) {
      cacheFile = null;
    } else {
      cacheFile = this.promptFiles.computeIfAbsent(currentPromptCache, x -> {
        try {
          logInfo("registering prompt cache file: " + x);
          return new JaipPromptCacheFile(x.toPath());
        } catch (IOException ex) {
          throw new RuntimeException("Can't create or open the prompt cache file for error: " + x,
              ex);
        }
      });
    }

    final long start = System.currentTimeMillis();
    try {
      final String sources = positionInfo.getFile().getName() + ':' + positionInfo.getLineNumber();
      return Stream.concat(
              prefix.stream(),
              Stream.concat(
                  Stream.of(join("\n", prompt))
                      .takeWhile(x -> !x.isBlank())
                      .peek(x -> logDebug("prepared prompt part: " + x))
                      .map(x -> {
                            String response = null;
                            final String promptKey;
                            if (cacheFile != null) {
                              promptKey = makeCachePromptKey(x);
                              response = cacheFile.getCache().find(promptKey);
                            } else {
                              promptKey = null;
                            }

                            if (response == null) {
                              response = this.processPrompt(x,
                                  sourceFileContainer, positionInfo,
                                  context,
                                  state
                              );
                              if (promptKey != null) {
                                logInfo("caching result for " + sources);
                                cacheFile.getCache().put(promptKey, positionInfo.getFile().getName(),
                                    positionInfo.getLineNumber(), response);
                              }
                            } else {
                              logInfo("found cached prompt response for " + sources
                                  + " in cache " + cacheFile.getPath().getFileName());
                            }

                            return response;
                          }
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
