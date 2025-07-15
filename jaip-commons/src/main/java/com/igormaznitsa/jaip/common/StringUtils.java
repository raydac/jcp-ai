package com.igormaznitsa.jaip.common;

public final class StringUtils {

  public static final String JAIP_PROMPT_PREFIX = "JAIP>";

  private StringUtils() {

  }

  public static String findPropertyNonNullableValue(
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
