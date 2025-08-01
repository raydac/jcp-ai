package com.igormaznitsa.jcpai.commons;

public final class StringUtils {

  public static final String AI_PROMPT_PREFIX = "AI>";

  private StringUtils() {

  }

  /**
   * Search code part in response, removing non needed parenthesis and markdown section labels.
   *
   * @param text the text to be processed
   * @return extracted code part
   */
  public static String extractCodePart(final String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }

    String trimmed = text.trim();
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
      return extractCodePart(trimmed);
    }
    if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
      int isoCharPosition = -1;
      for (int i = 0; i < trimmed.length(); i++) {
        if (Character.isISOControl(trimmed.charAt(i))) {
          isoCharPosition = i;
          break;
        }
      }
      if (isoCharPosition >= 0) {
        trimmed = trimmed.substring(isoCharPosition + 1, trimmed.length() - 3);
      }
      return extractCodePart(trimmed);
    }
    if (trimmed.endsWith("```")) {
      trimmed = trimmed.substring(0, trimmed.length() - 3);
      return extractCodePart(trimmed);
    }
    return trimmed;
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
}
