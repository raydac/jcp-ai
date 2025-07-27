package com.igormaznitsa.jcpai.commons.cache;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.hisp.dhis.jsontree.Json;
import org.hisp.dhis.jsontree.JsonArray;
import org.hisp.dhis.jsontree.JsonMixed;

public class JcpAiPromptResultData {

  private final LinkedHashMap<String, JcpAiCacheRecord> records = new LinkedHashMap<>();
  private boolean changed;

  public JcpAiPromptResultData() throws IOException {
  }

  public synchronized int size() {
    return this.records.size();
  }

  public synchronized void read(final Reader reader) {
    this.records.clear();
    final JsonMixed mixed = JsonMixed.of(reader);
    if (mixed.isArray()) {
      mixed.stream().map(JcpAiCacheRecord::new).forEach(x -> {
        this.records.put(x.getKey(), x);
      });
    }
    this.changed = !this.records.isEmpty();
  }

  public synchronized String find(final String key) {
    final JcpAiCacheRecord record = this.records.get(requireNonNull(key));
    return record == null ? null : record.getResult();
  }

  public synchronized void put(final String key, final String fileName, final int line,
                               final String response) {
    this.changed = true;

    final JcpAiCacheRecord newRecord = new JcpAiCacheRecord();
    newRecord.setUuid(UUID.randomUUID());
    newRecord.setFileName(fileName);
    newRecord.setLine(line);
    newRecord.setInstant(Instant.now());
    newRecord.setKey(requireNonNull(key));
    newRecord.setResult(requireNonNull(response));
    this.records.put(key, newRecord);
  }

  public synchronized void clear() {
    this.records.clear();
  }

  public synchronized boolean isChanged() {
    return this.changed;
  }

  public synchronized void setChange(final boolean changed) {
    this.changed = changed;
  }

  public synchronized void write(final Writer writer, final Predicate<JcpAiCacheRecord> filter)
      throws IOException {
    final JsonArray array = Json.array(x -> this.records.values().stream()
        .filter(filter)
        .map(
            JcpAiCacheRecord::toJsonNode)
        .forEach(x::addElement));
    writer.write(array.toJson());
    writer.flush();
  }

  public Stream<JcpAiCacheRecord> stream() {
    return this.records.values().stream();
  }
}
