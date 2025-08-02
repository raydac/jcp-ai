package com.igormaznitsa.jcpai.commons;

import static com.igormaznitsa.jcpai.commons.StringUtils.AI_PROMPT_PREFIX;
import static com.igormaznitsa.jcpai.commons.StringUtils.leftTrim;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import com.igormaznitsa.jcp.containers.FileInfoContainer;
import com.igormaznitsa.jcp.context.CommentTextProcessor;
import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcp.expression.ValueType;
import com.igormaznitsa.jcp.logger.PreprocessorLogger;
import com.igormaznitsa.jcpai.commons.cache.JcpAiPromptCacheFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract processor to prepare answer from a prompt.
 *
 * @since 1.0.0
 */
public abstract class AbstractJcpAiProcessor implements CommentTextProcessor {

  public static final String DEFAULT_SYSTEM_INSTRUCTION =
      "You are a world-class software engineer with decades of experience in software architecture, algorithms, and clean code. You write production-quality, idiomatic, maintainable code using best practices (including SOLID, DRY, KISS, and YAGNI). Your code is well-structured, efficient, modular, and extensible. You include only minimal but meaningful comments where necessary, and always prioritize clarity, correctness, and real-world applicability. Respond only with the complete source code. Do not include explanations, markup, formatting symbols, or sections - just the raw code output as if writing directly into a source file. You are acting strictly as a code generator.";

  public static final String PROPERTY_JCPAI_PROMPT_CACHE = "jcpai.prompt.cache.file";
  public static final String PROPERTY_JCPAI_PROMPT_CACHE_GC_THRESHOLD =
      "jcpai.prompt.cache.file.gc.threshold";
  public static final String PROPERTY_JCPAI_ONLY_PROCESSOR = "jcpai.prompt.only.processor";
  public static final String PROPERTY_JCPAI_TEMPERATURE = "jcpai.prompt.temperature";
  public static final String PROPERTY_JCPAI_TIMEOUT_MS = "jcpai.prompt.timeout.ms";
  public static final String PROPERTY_JCPAI_DISTILLATE_RESPONSE =
      "jcpai.prompt.distillate.response";
  public static final String PROPERTY_JCPAI_TOP_P = "jcpai.prompt.top.p";
  public static final String PROPERTY_JCPAI_TOP_K = "jcpai.prompt.top.k";
  public static final String PROPERTY_JCPAI_SEED = "jcpai.prompt.seed";
  public static final String PROPERTY_JCPAI_MAX_TOKENS = "jcpai.prompt.max.tokens";
  public static final String PROPERTY_JCPAI_INSTRUCTION_SYSTEM = "jcpai.prompt.instruction.system";

  public static final long DEFAULT_CACHE_GC_THRESHOLD = 15;
  public static final MessageDigest SHA512_DIGEST;
  public static final MessageDigest MD5_DIGEST;

  static {
    try {
      SHA512_DIGEST = MessageDigest.getInstance("SHA-512");
      MD5_DIGEST = MessageDigest.getInstance("MD5");
    } catch (Exception ex) {
      throw new Error("Can't find or instantiate a digest provider", ex);
    }
  }

  private final Map<File, Map.Entry<JcpAiPromptCacheFile, Set<String>>> promptFiles =
      new ConcurrentHashMap<>();
  private PreprocessorLogger logger;

  private static String makeCachePromptKey(final List<String> prompt,
                                           final Map<String, Object> additional) {
    String normalized = String.join("\n", prompt);

    if (!additional.isEmpty()) {
      normalized += "\nADDITIONAL: " + additional.entrySet()
          .stream()
          .sorted(Map.Entry.comparingByKey())
          .map(x -> x.getKey() + "->" + requireNonNullElse(x.getValue(), "[]"))
          .collect(joining(";"));
    }

    final byte[] promptBytes = normalized.getBytes(StandardCharsets.UTF_8);
    final byte[] md5 = MD5_DIGEST.digest(promptBytes);
    final byte[] sha512 = SHA512_DIGEST.digest(promptBytes);
    final byte[] aggregated = new byte[md5.length + sha512.length];
    System.arraycopy(md5, 0, aggregated, 0, md5.length);
    System.arraycopy(sha512, 0, aggregated, md5.length, sha512.length);
    return Base64.getEncoder().encodeToString(aggregated);
  }

  private static List<TextBlock> splitToTextBlocks(final FilePositionInfo startLinePosition,
                                                   final String[] textLines) {
    final List<TextBlock> result = new ArrayList<>();

    final List<String> jcpAiLines = new ArrayList<>();
    final List<String> justLines = new ArrayList<>();

    int stringLineIndex = 0;
    FilePositionInfo position = startLinePosition;
    for (final String line : textLines) {
      final String leftTrim = leftTrim(line);
      if (leftTrim.startsWith(AI_PROMPT_PREFIX)) {
        if (!justLines.isEmpty()) {
          result.add(new JustTextBlock(justLines, position));
          justLines.clear();
          position = new FilePositionInfo(position.getFile(),
              startLinePosition.getStringIndex() + stringLineIndex);
        }
        jcpAiLines.add(leftTrim.substring(AI_PROMPT_PREFIX.length()));
      } else {
        if (!jcpAiLines.isEmpty()) {
          result.add(new JcpAiPrompt(jcpAiLines, position));
          jcpAiLines.clear();
          position = new FilePositionInfo(position.getFile(),
              startLinePosition.getStringIndex() + stringLineIndex);
        }
        justLines.add(leftTrim);
      }
      stringLineIndex++;
    }

    if (!justLines.isEmpty()) {
      result.add(new JustTextBlock(justLines, position));
    }
    if (!jcpAiLines.isEmpty()) {
      result.add(new JcpAiPrompt(jcpAiLines, position));
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

  private static Optional<Float> findPreprocessorFloatVariable(final String variable,
                                                               final PreprocessorContext context) {
    final Value value = findPreprocessorVar(variable, context).orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    if (value.getType() == ValueType.INT) {
      return Optional.of((float) value.asLong());
    }
    if (value.getType() == ValueType.FLOAT) {
      return Optional.of(value.asFloat());
    }
    if (value.getType() == ValueType.STRING) {
      try {
        return Optional.of(Float.parseFloat(value.asString().trim()));
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
            "Detected non-float value for " + variable + " : " + value);
      }
    }
    throw new IllegalArgumentException("Unexpected value for float " + variable + " : " + value);
  }

  private static Optional<String> findPreprocessorStringVariable(final String variable,
                                                                 final PreprocessorContext context) {
    final Value value = findPreprocessorVar(variable, context).orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    return switch (value.getType()) {
      case BOOLEAN -> Optional.of(value.asBoolean().toString());
      case STRING -> Optional.of(value.asString());
      case INT -> Optional.of(Long.toString(value.asLong()));
      case FLOAT -> Optional.of(Float.toString(value.asFloat()));
      default -> throw new IllegalArgumentException(
          "Unexpected value type for " + variable + " : " + value);
    };
  }

  private static Optional<Boolean> findPreprocessorBooleanVariable(final String variable,
                                                                   final PreprocessorContext context) {
    final Value value = findPreprocessorVar(variable, context).orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    return switch (value.getType()) {
      case BOOLEAN -> Optional.of(value.asBoolean());
      case STRING -> Optional.of(Boolean.parseBoolean(value.asString().trim()));
      case INT -> Optional.of(value.asLong() != 0);
      case FLOAT -> Optional.of(value.asFloat() > 0.0f);
      default -> throw new IllegalArgumentException(
          "Unexpected value type for " + variable + " : " + value);
    };
  }

  private static Optional<Long> findPreprocessorLongVariable(final String varName,
                                                             final PreprocessorContext context) {
    final Value value = findPreprocessorVar(varName, context).orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    if (value.getType() == ValueType.BOOLEAN) {
      return Optional.of(value.asBoolean() ? 1L : 0L);
    }
    if (value.getType() == ValueType.INT) {
      return Optional.of(value.asLong());
    }
    if (value.getType() == ValueType.FLOAT) {
      return Optional.of(Math.round((double) value.asFloat()));
    }
    if (value.getType() == ValueType.STRING) {
      try {
        return Optional.of(Long.parseLong(value.asString().trim()));
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException(
            "Detected non-long value for " + varName + " : " + value);
      }
    }
    throw new IllegalArgumentException("Unexpected value for " + varName + " : " + value);
  }

  private static File findPromptCacheFile(final PreprocessorContext context,
                                          final PreprocessingState state) {
    final Value value = findPreprocessorVar(PROPERTY_JCPAI_PROMPT_CACHE, context).orElse(null);
    if (value == null) {
      return null;
    }
    if (value.getType() != ValueType.STRING) {
      throw new IllegalArgumentException(
          "Detected non-string value for " + PROPERTY_JCPAI_PROMPT_CACHE +
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
  public final void onContextStopped(
      final PreprocessorContext context,
      final Throwable error) {
    if (error == null) {
      logInfo(
          "stopping processor, cached prompt map contains " + this.promptFiles.size() + " file(s)");
    } else {
      logError("stopping processor with error: " + error.getMessage());
    }

    final long gcThreshold =
        findPreprocessorLongVariable(PROPERTY_JCPAI_PROMPT_CACHE_GC_THRESHOLD, context).orElse(
            DEFAULT_CACHE_GC_THRESHOLD);
    if (gcThreshold <= 0) {
      this.logWarn("GC threshold disabled for prompt file caches");
    } else {
      this.logInfo("GC threshold is " + gcThreshold + " for prompt file caches");
    }

    this.promptFiles.values()
        .forEach(x -> {
          try {
            final JcpAiPromptCacheFile cacheContainer = x.getKey();
            final Set<String> detectedPrompts = x.getValue();

            final Set<String> removedPrompts = new HashSet<>();
            cacheContainer.stream().forEach(y -> {
              if (detectedPrompts.contains(y.getKey())) {
                if (y.getSinceUse() != 0) {
                  cacheContainer.markChanged();
                  y.setSinceUse(0);
                }
              } else {
                if (gcThreshold > 0) {
                  final long newGc = y.getSinceUse() + 1L;
                  y.setSinceUse(newGc);
                  cacheContainer.markChanged();
                  if (newGc > gcThreshold) {
                    removedPrompts.add(y.getKey());
                  }
                }
              }
            });
            this.logInfo(
                "Detected " + removedPrompts.size() + " prompt(s) marked for GC in cache file " +
                    x.getKey().getPath());
            if (cacheContainer.flush(y -> !removedPrompts.contains(y.getKey()))) {
              logInfo("Written prompt cache file: " + x.getKey().getPath());
            }
          } catch (IOException ex) {
            logError(
                "Can't flush prompt cache file " + x.getKey().getPath() + " : " + ex.getMessage());
          }
        });
    this.promptFiles.clear();

    this.onProcessorStopped(context, error);
    this.logger = null;
  }

  public Optional<Float> findParamTemperature(final PreprocessorContext context) {
    return findPreprocessorFloatVariable(PROPERTY_JCPAI_TEMPERATURE, context);
  }

  public Optional<String> findParamInstructionSystem(final PreprocessorContext context) {
    return findPreprocessorStringVariable(PROPERTY_JCPAI_INSTRUCTION_SYSTEM, context);
  }

  public Optional<Float> findParamTopP(final PreprocessorContext context) {
    return findPreprocessorFloatVariable(PROPERTY_JCPAI_TOP_P, context);
  }

  public Optional<Float> findParamTopK(final PreprocessorContext context) {
    return findPreprocessorFloatVariable(PROPERTY_JCPAI_TOP_K, context);
  }

  public Optional<Long> findParamSeed(final PreprocessorContext context) {
    return findPreprocessorLongVariable(PROPERTY_JCPAI_SEED, context);
  }

  public Optional<Long> findTimeoutMs(final PreprocessorContext context) {
    return findPreprocessorLongVariable(PROPERTY_JCPAI_TIMEOUT_MS, context);
  }

  public Optional<String> findBaseUrl(final String varName, final PreprocessorContext context) {
    return findPreprocessorStringVariable(varName, context);
  }

  public Optional<Long> findParamMaxTokens(final PreprocessorContext context) {
    return findPreprocessorLongVariable(PROPERTY_JCPAI_MAX_TOKENS, context);
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

  /**
   * Called on start of preprocessing
   *
   * @param context the preprocessor context, must not be null
   * @since 1.0.0
   */
  protected void onProcessorStarted(PreprocessorContext context) {

  }

  /**
   * Called on stop of preprocessing
   *
   * @param context the preprocessor context, must not be null
   * @since 1.0.0
   */
  protected void onProcessorStopped(
      PreprocessorContext context,
      Throwable error) {

  }

  /**
   * Find model name provided by specified property.
   *
   * @param modelNameProperty the property contains model name
   * @param context           the preprocessor context, must not be null
   * @param positionInfo      the position info, must not be null
   * @return the model name
   * @throws IllegalStateException if there is no any model name in scope
   * @since 1.0.1
   */
  protected String findModel(
      final String modelNameProperty,
      final PreprocessorContext context,
      final FilePositionInfo positionInfo) {

    final String sources = StringUtils.asText(positionInfo, true);

    return findPreprocessorVar(modelNameProperty, context)
        .map(Value::asString)
        .orElseThrow(() -> new IllegalStateException(
            "Can't find model by " + modelNameProperty + " at " +
                sources));
  }

  /**
   * Get extra values to make prompt key
   *
   * @param context the preprocessor context, must not be null
   * @return extra values as map, must not be null but can be empty.
   * @since 1.0.1
   */
  protected Map<String, Object> getExtraPromptKeyValues(
      final FileInfoContainer sourceFileContainer,
      final FilePositionInfo positionInfo,
      final PreprocessorContext context,
      final PreprocessingState state) {
    return Map.of();
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

    final List<TextBlock> detectedTextBlocks = splitToTextBlocks(positionInfo, lines);

    final File currentPromptCache = findPromptCacheFile(context, state);
    final Map.Entry<JcpAiPromptCacheFile, Set<String>> cacheFilePair;
    if (currentPromptCache == null) {
      cacheFilePair = null;
    } else {
      cacheFilePair = this.promptFiles.computeIfAbsent(currentPromptCache, x -> {
        try {
          logInfo("registering prompt cache file: " + x);
          return Map.entry(new JcpAiPromptCacheFile(x.toPath()), ConcurrentHashMap.newKeySet());
        } catch (IOException ex) {
          throw new RuntimeException("Can't create or open the prompt cache file for error: " + x,
              ex);
        }
      });
    }

    final long start = System.currentTimeMillis();
    try {
      final String sources =
          positionInfo.getFile().getName() + ':' + positionInfo.getLineNumber();
      final List<String> resultLines = new ArrayList<>();
      for (final TextBlock block : detectedTextBlocks) {
        if (block instanceof JustTextBlock) {
          resultLines.addAll(block.lines);
        } else if (block instanceof JcpAiPrompt) {
          final String promptKey;
          String cachedResponse = null;
          if (cacheFilePair != null) {
            promptKey = makeCachePromptKey(block.lines,
                this.getExtraPromptKeyValues(sourceFileContainer, positionInfo, context, state));
            cachedResponse = cacheFilePair.getKey().getCache().find(promptKey);
            cacheFilePair.getValue().add(promptKey);
            logDebug("registered use of prompt key for " + sources + " : " + promptKey);
          } else {
            promptKey = null;
          }

          if (cachedResponse == null) {
            final List<String> responseLines = List.of(this.processPrompt(block.asString("\n"),
                sourceFileContainer, block.positionInfo,
                context,
                state
            ).split("\\R"));
            if (promptKey != null) {
              logInfo("caching result for " + sources);
              cacheFilePair.getKey().getCache()
                  .put(promptKey, block.positionInfo.getFile().getName(),
                      block.positionInfo.getLineNumber(), String.join("\n", responseLines));
            }
            resultLines.addAll(responseLines);
          } else {
            resultLines.addAll(List.of(cachedResponse.split("\\R")));
            this.logInfo("found cached prompt response for " + sources
                + " in cache file " + cacheFilePair.getKey().getPath().getFileName());
          }
        }
      }
      return resultLines.stream().map(x -> context.isPreserveIndents() ? indent + x : x)
          .collect(joining(context.getEol()));
    } finally {
      this.logDebug("completed prompt, spent " + (System.currentTimeMillis() - start) + "ms");
    }
  }

  @Override
  public boolean isAllowed(
      final FileInfoContainer fileInfoContainer,
      final FilePositionInfo filePositionInfo,
      final PreprocessorContext preprocessorContext,
      final PreprocessingState preprocessingState) {
    final Value modelName =
        findPreprocessorVar(PROPERTY_JCPAI_ONLY_PROCESSOR, preprocessorContext).orElse(null);
    if (modelName == null) {
      return true;
    } else {
      final boolean enabled = modelName.asString().equalsIgnoreCase(this.getProcessorTextId());
      if (!enabled) {
        logDebug("processor disabled for " + filePositionInfo.getFile().getName() + ':' +
            filePositionInfo.getLineNumber() + " by " + PROPERTY_JCPAI_ONLY_PROCESSOR + "=" +
            modelName.asString());
      }
      return enabled;
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
   * Get flag that response distillation is allowed.
   *
   * @param context the preprocessor context, must not be null
   * @return true if required, false otherwise
   * @since 1.0.1
   */
  protected boolean isDistillationRequired(final PreprocessorContext context) {
    return findPreprocessorBooleanVariable(PROPERTY_JCPAI_DISTILLATE_RESPONSE, context).orElse(
        true);
  }

  /**
   * Get the processor text id. It will be used as log prefix and in other operations requiring id of the processor.
   *
   * @return the processor name, must not be null
   * @since 1.0.0
   */
  public abstract String getProcessorTextId();

  /**
   * Make distillation of response text if needed.
   *
   * @param context  the preprocessor context, must not be null
   * @param response the response to be processed
   * @return the distilled response or original one
   * @since 1.0.1
   */
  protected String makeDistillationIfAllowed(final PreprocessorContext context,
                                             final String response) {
    if (this.isDistillationRequired(context)) {
      logInfo("distilling the response");
      return StringUtils.extractCodePart(response);
    } else {
      logDebug("distilling is turned off");
      return response;
    }
  }

  private static final class JustTextBlock extends TextBlock {
    JustTextBlock(final List<String> text, final FilePositionInfo positionInfo) {
      super(text, positionInfo);
    }
  }

  private static final class JcpAiPrompt extends TextBlock {
    JcpAiPrompt(final List<String> text, final FilePositionInfo positionInfo) {
      super(text, positionInfo);
    }
  }

  private abstract static class TextBlock {
    final List<String> lines;
    final FilePositionInfo positionInfo;

    TextBlock(final List<String> lines, final FilePositionInfo positionInfo) {
      this.lines = List.copyOf(lines);
      this.positionInfo = positionInfo;
    }

    String asString(final String eol) {
      return String.join(eol, this.lines);
    }
  }
}
