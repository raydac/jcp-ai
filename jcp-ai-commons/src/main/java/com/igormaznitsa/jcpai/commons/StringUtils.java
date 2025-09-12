package com.igormaznitsa.jcpai.commons;

import static com.igormaznitsa.jcpai.commons.MarkdownCodeExtractor.extractFencedCodeBlocks;
import static java.lang.String.join;
import static java.util.Objects.requireNonNullElse;

import com.igormaznitsa.jcp.exceptions.FilePositionInfo;

public final class StringUtils {

  public static final String AI_PROMPT_PREFIX = "AI>";

  private StringUtils() {

  }

  /**
   * Search code part in response, removing non needed parenthesis and markdown section labels.
   *
   * @param text the text to be processed
   * @param eol  the end of line to be used as separator between found blocks
   * @return extracted code part
   */
  public static String extractCodePart(final String text, final String eol) {
    if (text == null || text.isEmpty()) {
      return text;
    }

    String trimmed = text.trim();
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
      return extractCodePart(trimmed, eol);
    }

    final String fenced = join(requireNonNullElse(eol, "\n"),
        extractFencedCodeBlocks(trimmed)).trim();
    return fenced.isEmpty() ? trimmed : fenced;
  }

  /**
   * Left trim of text.
   *
   * @param text the text to be trimmed
   * @return left trimmed text
   */
  public static String leftTrim(final String text) {
    if (text == null) {
      return null;
    }
    int i = 0;
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      i++;
    }
    return text.substring(i);
  }

  /**
   * Get position info in format file:line
   *
   * @param positionInfo the position info, can be null
   * @param onlyName     if true then only file name will be in use, full absolute path otherwise
   * @return empty string if position info is null or file name and line number otherwise
   * @since 1.0.1
   */
  public static String asText(final FilePositionInfo positionInfo, final boolean onlyName) {
    if (positionInfo == null) {
      return "";
    }
    return
        (onlyName ? positionInfo.getFile().getName() : positionInfo.getFile().getAbsolutePath()) +
            ':' + positionInfo.getLineNumber();
  }
}
