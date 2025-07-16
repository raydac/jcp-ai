package com.igormaznitsa.jaip.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class JaipPromptResultDataTest {

  @Test
  void testPutGet() throws IOException {
    final JaipPromptResultData cache = new JaipPromptResultData();

    cache.put("key1", "test.java", 1,"test 1");
    cache.put("key2", "test.java", 1,"test 2");
    cache.put("key3", "hello.java", 1,"test 3");
    cache.put("key4", "test.java", 1,"test 4");

    final StringWriter writer = new StringWriter();
    cache.write(writer);

    final JaipPromptResultData cache2 = new JaipPromptResultData();
    cache2.read(new StringReader(writer.toString()));

    assertEquals(cache.size(), cache2.size());
  }


}