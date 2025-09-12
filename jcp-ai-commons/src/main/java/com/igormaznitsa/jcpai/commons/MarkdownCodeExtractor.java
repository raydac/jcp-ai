package com.igormaznitsa.jcpai.commons;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownCodeExtractor {

  private static final String FENCED_CODE_REGEX = "^\\s*```[^\\r\\n]*$(.*?)^\\s*```[^\\r\\n]*$";

  private static final Pattern FENCED_PATTERN =
      Pattern.compile(FENCED_CODE_REGEX, Pattern.MULTILINE | Pattern.DOTALL);

  /**
   * Extract all fenced code blocks from markdown text
   *
   * @param markdown The markdown text
   * @return List of code block contents
   */
  public static List<String> extractFencedCodeBlocks(String markdown) {
    List<String> codeBlocks = new ArrayList<>();
    Matcher matcher = FENCED_PATTERN.matcher(markdown);

    while (matcher.find()) {
      String code = matcher.group(1).trim();
      codeBlocks.add(code);
    }

    return codeBlocks;
  }

}