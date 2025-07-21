package com.igormaznitsa.jcpai.commons;

import com.igormaznitsa.jcp.context.PreprocessingState;
import com.igormaznitsa.jcp.exceptions.FilePositionInfo;

public final class StringUtils {

  public static final String AI_PROMPT_PREFIX = "AI>";

  private StringUtils() {

  }

  public static FilePositionInfo findFilePositionInfo(final PreprocessingState state) {
    var stack = state.makeIncludeStack();
    if (stack == null || stack.length == 0) throw new IllegalStateException("Can't find any sources int include stack");
    return stack[stack.length - 1];
  }

  public static String getCurrentSourcesPosition(final PreprocessingState state) {
    final FilePositionInfo fileInfo = findFilePositionInfo(state);
    return  fileInfo.getFile().getName() +  ':' + fileInfo.getLineNumber();
  }

  public static String normalizeJavaResponse(final String response) {
    String trimmed = response.trim();
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
      return normalizeJavaResponse(trimmed);
    }
    if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
      int isoCharPosition = -1;
      for(int i=0;i<trimmed.length();i++) {
        if (Character.isISOControl(trimmed.charAt(i))) {
          isoCharPosition = i;
          break;
        }
      }
      if (isoCharPosition >= 0) {
        trimmed = trimmed.substring(isoCharPosition + 1, trimmed.length() - 3);
      }
      return normalizeJavaResponse(trimmed);
    }
    if (trimmed.endsWith("```")) {
      trimmed = trimmed.substring(0, trimmed.length() - 3);
      return normalizeJavaResponse(trimmed);
    }
    return trimmed;
  }

  public static String findSystemPropertyNonNullableValue(
      final String property,
      final String defaultValue
  ) {
    final String result = System.getProperty(property, defaultValue);
    if (result == null) {
      throw new IllegalStateException("Can't find property: " + property);
    }
    return result;
  }

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
