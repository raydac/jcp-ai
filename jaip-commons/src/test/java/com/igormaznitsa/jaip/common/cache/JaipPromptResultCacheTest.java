package com.igormaznitsa.jaip.common.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class JaipPromptResultCacheTest {

  @Test
  void testPutGet() throws IOException {
    final JaipPromptResultCache cache = new JaipPromptResultCache();

    cache.put("key1", "test 1");
    cache.put("key2", "test 2");
    cache.put("key3", "test 3");
    cache.put("key4", "test 4");

    final StringWriter writer = new StringWriter();
    cache.write(writer);

    final JaipPromptResultCache cache2 = new JaipPromptResultCache();
    cache2.read(new StringReader(writer.toString()));

    assertEquals(cache.size(), cache2.size());
  }


}