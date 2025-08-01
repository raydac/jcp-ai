package com.igormaznitsa.jcpai.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void testExtractCodePart() {
    assertEquals("final class AAA{}", StringUtils.extractCodePart("final class AAA{}"));
    assertEquals("var a = 33;", StringUtils.extractCodePart("{ var a = 33; }"));
    assertEquals("var e=1;", StringUtils.extractCodePart("```   \nvar e=1;\n```\n"));
    assertEquals("var e=1;", StringUtils.extractCodePart("```java\nvar e=1;\n```\n"));
  }
}