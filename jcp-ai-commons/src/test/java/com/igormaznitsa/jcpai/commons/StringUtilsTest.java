package com.igormaznitsa.jcpai.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void testExtractCodePart() {
    assertEquals("var e=1;", StringUtils.extractCodePart("```   \nvar e=1;\n```\n", null));
    assertEquals("var e=1;",
        StringUtils.extractCodePart("```java\nvar e=1;\n```|code_block\n", null));
    assertEquals("final class AAA{}", StringUtils.extractCodePart("final class AAA{}", null));
    assertEquals("var a = 33;", StringUtils.extractCodePart("{ var a = 33; }", null));
    assertEquals("var e=1;", StringUtils.extractCodePart("```java\nvar e=1;\n```\n", null));
    assertEquals("var e=1;\nvar b1=2;",
        StringUtils.extractCodePart("```java\nvar e=1;\n```sss\n```java\nvar b1=2;\n```", null));
  }
}