package com.igormaznitsa.jcpai.commons.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class JcpAiPromptResultDataTest {

  @Test
  void testPutGet() throws Exception {
    final JcpAiPromptResultData cache = new JcpAiPromptResultData();

    cache.put("key1", "test.java", 1,"test 1");
    cache.put("key2", "test.java", 1,"test 2");
    cache.put("key3", "hello.java", 1,"test 3");
    cache.put("key4", "test.java", 1,"test 4");

    final StringWriter writer = new StringWriter();
    cache.write(writer);

    //System.out.println(writer.toString());

    final JcpAiPromptResultData cache2 = new JcpAiPromptResultData();
    cache2.read(new StringReader(writer.toString()));

    assertEquals(cache.size(), cache2.size());
  }


}