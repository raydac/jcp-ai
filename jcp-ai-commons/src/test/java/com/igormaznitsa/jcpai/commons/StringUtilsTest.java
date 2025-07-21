package com.igormaznitsa.jcpai.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void testNormalizeJavaResponse() {
    assertEquals("final class AAA{}", StringUtils.normalizeJavaResponse("final class AAA{}"));
    assertEquals("var a = 33;", StringUtils.normalizeJavaResponse("{ var a = 33; }"));
    assertEquals("var e=1;", StringUtils.normalizeJavaResponse("```   \nvar e=1;\n```\n"));
    assertEquals("var e=1;", StringUtils.normalizeJavaResponse("```java\nvar e=1;\n```\n"));
  }
}