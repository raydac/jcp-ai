package com.igormaznitsa.jaip.common.cache;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JaipPromptCacheFile {

  private final Path path;
  private final JaipPromptResultData cache;

  public JaipPromptCacheFile(final Path path) throws IOException {
    this.path = requireNonNull(path);
    String content = "[]";
    if (Files.isDirectory(this.path)) {
      throw new IOException("Required a file but found a directory: " + this.path);
    } else if (Files.isRegularFile(this.path)) {
      content = Files.readString(this.path, StandardCharsets.UTF_8);
    }
    this.cache = new JaipPromptResultData();
    this.cache.read(new StringReader(content));
  }

  public JaipPromptResultData getCache() {
    return this.cache;
  }

  public boolean flush() throws IOException {
    if (this.cache.isChanged()) {
      final StringWriter writer = new StringWriter(16384);
      this.cache.write(writer);
      Files.write(this.path, writer.toString().getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE);
      return true;
    }
    return false;
  }

  public Path getPath() {
    return this.path;
  }

}
