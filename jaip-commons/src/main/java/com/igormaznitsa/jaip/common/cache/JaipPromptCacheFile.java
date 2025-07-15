package com.igormaznitsa.jaip.common.cache;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class JaipPromptCacheFile {
  public static final String PROPERTY_JAIP_PROMPT_CACHE_FILE = "jaip.prompt.cache.file";

  private final Path path;
  private final JaipPromptResultCache cache;

  private JaipPromptCacheFile(final Path path) throws IOException {
    this.path = requireNonNull(path);
    String content = "[]";
    if (Files.isRegularFile(this.path)) {
      content = Files.readString(this.path, StandardCharsets.UTF_8);
    }
    this.cache = new JaipPromptResultCache();
    this.cache.read(new StringReader(content));
  }

  public static JaipPromptCacheFile findAmongSystemProperties() throws IOException {
    final String filePath = System.getProperty(PROPERTY_JAIP_PROMPT_CACHE_FILE, null);
    if (filePath == null) {
      return null;
    } else {
      return new JaipPromptCacheFile(Paths.get(filePath));
    }
  }

  public JaipPromptResultCache getCache() {
    return this.cache;
  }

  public void flush() {
    try {
      if (this.cache.isChanged()) {
        final StringWriter writer = new StringWriter(16384);
        this.cache.write(writer);
        Files.write(this.path, writer.toString().getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE);
      }
    } catch (IOException ex) {
      // ignore
    }
  }

  public Path getPath() {
    return this.path;
  }

}
