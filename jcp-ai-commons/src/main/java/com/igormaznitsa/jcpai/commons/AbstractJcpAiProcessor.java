package com.igormaznitsa.jcpai.commons;

import static com.igormaznitsa.jcp.expression.functions.AbstractFunction.ARITY_1_2;
import static com.igormaznitsa.jcp.expression.functions.AbstractFunction.ARITY_ANY;
import static com.igormaznitsa.jcpai.commons.StringUtils.AI_PROMPT_PREFIX;
import static com.igormaznitsa.jcpai.commons.StringUtils.leftTrim;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.joining;

import com.igormaznitsa.jcp.context.CommentTextProcessor;
import com.igormaznitsa.jcp.context.PreprocessorContext;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;
import com.igormaznitsa.jcp.expression.Value;
import com.igormaznitsa.jcp.expression.ValueType;
import com.igormaznitsa.jcp.extension.PreprocessorExtension;
import com.igormaznitsa.jcp.logger.PreprocessorLogger;
import com.igormaznitsa.jcp.utils.PreprocessorUtils;
import com.igormaznitsa.jcpai.commons.cache.JcpAiPromptCacheFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract processor to prepare answer from a prompt.
 *
 * @since 1.0.0
 */
public abstract class AbstractJcpAiProcessor implements CommentTextProcessor,
    PreprocessorExtension {

  public static final String FUNCTION_CALL_AI = "call_ai";

  public static final String DEFAULT_SYSTEM_INSTRUCTION =
      "You are a world-class software engineer with decades of experience in software architecture, algorithms, and clean code. You write production-quality, idiomatic, maintainable code using best practices (including SOLID, DRY, KISS, and YAGNI). Your code is well-structured, efficient, modular, and extensible. You include only minimal but meaningful comments where necessary, and always prioritize clarity, correctness, and real-world applicability. Respond only with the complete source code. Do not include explanations, markup, formatting symbols, or sections - just the raw code output as if writing directly into a source file. You are acting strictly as a code generator. Generate the source code exactly as requested. Do not include any explanations, comments, markdown formatting, or code block delimiters. Do not add any text before or after the code. The output should be plain code, ready to be directly copied or injected into a source file.Ensure the code is syntactically correct, complete, and self-contained if applicable.";

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

  protected final AtomicBoolean started = new AtomicBoolean();

  static {
    try {
      SHA512_DIGEST = MessageDigest.getInstance("SHA-512");
      MD5_DIGEST = MessageDigest.getInstance("MD5");
    } catch (Exception ex) {
      throw new Error("Can't find or instantiate a digest provider", ex);
    }
  }

  protected void assertStarted() {
    if (!this.started.get()) {
      throw new IllegalStateException("Called but processor not started");
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

  private static Optional<Boolean> findPreprocessorBooleanVariable(
      final String variable,
      final PreprocessorContext context
  ) {
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

  private static File findPromptCacheFile(final PreprocessorContext context) {
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
      file = new File(
          context.getPreprocessingState().findActiveTextFileDataContainer().orElseThrow().getFile()
              .getParentFile(), pathFile.getPath());
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
    if (this.started.compareAndSet(true, false)) {
      if (error == null) {
        logInfo(
            "stopping processor, cached prompt map contains " + this.promptFiles.size() +
                " file(s)");
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
                  "Can't flush prompt cache file " + x.getKey().getPath() + " : " +
                      ex.getMessage());
            }
          });
      this.promptFiles.clear();

      this.onProcessorStopped(context, error);
      this.logger = null;
    }
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
    if (this.started.compareAndSet(false, true)) {
      try {
        this.logger = context.getPreprocessorLogger();
        this.promptFiles.clear();

        logInfo("init processor");
        this.onProcessorStarted(context);
      } catch (RuntimeException ex) {
        this.started.set(false);
        throw ex;
      }
    }
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
   * @since 1.1.0
   */
  protected Map<String, Object> getExtraPromptKeyValues(
      final PreprocessorContext context) {
    return Map.of();
  }

  protected Map.Entry<JcpAiPromptCacheFile, Set<String>> findCacheFilePair(
      final PreprocessorContext context) {
    final File currentPromptCache = findPromptCacheFile(context);
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
    return cacheFilePair;
  }

  private String makeRequest(
      final PreprocessorContext context,
      final FilePositionInfo positionInfo,
      final List<TextBlock> detectedTextBlocks,
      final String indent,
      final Map.Entry<JcpAiPromptCacheFile, Set<String>> cacheFilePair
  ) {
    this.assertStarted();

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
          if (cacheFilePair == null) {
            promptKey = null;
          } else {
            promptKey = makeCachePromptKey(block.lines,
                this.getExtraPromptKeyValues(context));
            cachedResponse = cacheFilePair.getKey().getCache().find(promptKey);
            cacheFilePair.getValue().add(promptKey);
            logDebug("registered use of prompt key for " + sources + " : " + promptKey);
          }

          if (cachedResponse == null) {
            final List<String> responseLines =
                List.of(this.processPrompt(context, block.asString("\n")).split("\\R"));
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
  public String processUncommentedText(
      final PreprocessorContext context,
      final int recommendedIndent,
      final String uncommentedText
  ) {
    this.assertStarted();

    final FilePositionInfo positionInfo = PreprocessorUtils.extractFilePositionInfo(context);
    logDebug("Incoming potential prompt from uncommented text: " + uncommentedText);
    final String[] lines = uncommentedText.split("\\R");

    final String indent =
        context.isPreserveIndents() ? " ".repeat(recommendedIndent) : "";
    final List<TextBlock> detectedTextBlocks = splitToTextBlocks(positionInfo, lines);
    final Map.Entry<JcpAiPromptCacheFile, Set<String>> cacheFilePair =
        this.findCacheFilePair(context);

    return this.makeRequest(context, positionInfo, detectedTextBlocks, indent, cacheFilePair);
  }

  @Override
  public boolean isAllowed(PreprocessorContext context) {
    this.assertStarted();
    final FilePositionInfo filePositionInfo = PreprocessorUtils.extractFilePositionInfo(context);

    final Value modelName =
        findPreprocessorVar(PROPERTY_JCPAI_ONLY_PROCESSOR, context).orElse(null);
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
   * @param prompt  the prompt text, must not be null
   * @param context the current preprocessor context, must not be null
   * @return the generated response as single line or multi-line text, must not be null
   * @since 1.1.0
   */
  public abstract String processPrompt(
      PreprocessorContext context,
      String prompt);

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
      return StringUtils.extractCodePart(response, context.getEol());
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

  @Override
  public boolean hasAction(final int i) {
    this.assertStarted();
    return false;
  }

  @Override
  public boolean hasUserFunction(final String name, final Set<Integer> arity) {
    this.assertStarted();
    if (FUNCTION_CALL_AI.equals(name)) {
      return arity.isEmpty() || arity.contains(1) || arity.contains(2);
    } else {
      return false;
    }
  }

  @Override
  public boolean processAction(final PreprocessorContext preprocessorContext,
                               final List<Value> values) {
    throw new UnsupportedOperationException("Action processing is not implemented");
  }

  @Override
  public Value processUserFunction(final PreprocessorContext context,
                                   final String name,
                                   final List<Value> args) {
    this.assertStarted();
    if (name.equals(FUNCTION_CALL_AI)) {
      if (args.isEmpty() || args.size() > 2) {
        throw new IllegalArgumentException(
            "Unexpected number of arguments, expected only either 1 or 2 arguments: " +
                args.size());
      }

      final String prompt;
      final boolean allowCache;
      if (args.size() > 1) {
        allowCache = args.get(0).toBoolean();
        prompt = args.get(1).asString();
      } else {
        prompt = args.get(0).asString();
        allowCache = true;
      }

      final FilePositionInfo positionInfo = PreprocessorUtils.extractFilePositionInfo(context);
      logDebug(String.format("Incoming potential prompt from function (%s): %s",
          (allowCache ? "cache allowed" : "no cache"), prompt));

      if (prompt.isBlank()) {
        throw context.makeException("Empty prompt is not allowed", null);
      }

      final List<String> lines = Arrays.stream(prompt.split("\\R")).toList();
      final Map.Entry<JcpAiPromptCacheFile, Set<String>> cacheFilePair =
          allowCache ? this.findCacheFilePair(context) : null;

      final String result =
          this.makeRequest(context, positionInfo, List.of(new JcpAiPrompt(lines, positionInfo)), "",
              cacheFilePair);
      return Value.valueOf(result);
    } else {
      throw new IllegalStateException("Call for unknown function: " + name);
    }
  }

  @Override
  public Set<Integer> getUserFunctionArity(final String name) {
    this.assertStarted();
    if (FUNCTION_CALL_AI.equals(name)) {
      return ARITY_1_2;
    } else {
      return ARITY_ANY;
    }
  }
}
