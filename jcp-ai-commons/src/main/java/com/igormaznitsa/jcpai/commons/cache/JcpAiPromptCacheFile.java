package com.igormaznitsa.jcpai.commons.cache;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class JcpAiPromptCacheFile {

  private final Path path;
  private final JcpAiPromptResultData cache;

  public JcpAiPromptCacheFile(final Path path) throws IOException {
    this.path = requireNonNull(path);
    String content = "[]";
    if (Files.isDirectory(this.path)) {
      throw new IOException("Required a file but found a directory: " + this.path);
    } else if (Files.isRegularFile(this.path)) {
      content = Files.readString(this.path, StandardCharsets.UTF_8);
    }
    this.cache = new JcpAiPromptResultData();
    this.cache.read(new StringReader(content));
  }

  public boolean isChanged() {
    return this.cache.isChanged();
  }

  public void markChanged() {
    this.cache.setChange(true);
  }

  public JcpAiPromptResultData getCache() {
    return this.cache;
  }

  public Stream<JcpAiCacheRecord> stream() {
    return this.cache.stream();
  }

  public boolean flush(final Predicate<JcpAiCacheRecord> filter) throws IOException {
    if (this.cache.isChanged()) {
      final StringWriter writer = new StringWriter(16384);
      this.cache.write(writer, filter);
      Files.writeString(this.path, writer.toString(), StandardCharsets.UTF_8,
          StandardOpenOption.CREATE);
      return true;
    }
    return false;
  }

  public Path getPath() {
    return this.path;
  }

}
