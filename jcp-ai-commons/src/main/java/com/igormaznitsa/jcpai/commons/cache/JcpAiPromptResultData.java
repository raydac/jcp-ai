package com.igormaznitsa.jcpai.commons.cache;

import static java.util.Objects.requireNonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
    final JsonArray array = new Gson().fromJson(reader, JsonArray.class);
    for (int i = 0; i < array.size(); i++) {
      final JcpAiCacheRecord record = new JcpAiCacheRecord(array.get(i).getAsJsonObject());
      this.records.put(record.getKey(), record);
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
    final JsonArray array = new JsonArray();
    this.records.values().stream()
        .sorted(Comparator.comparing(JcpAiCacheRecord::getKey))
        .filter(filter)
        .map(
            JcpAiCacheRecord::toJsonObject)
        .forEach(array::add);
    writer.append(new GsonBuilder().setPrettyPrinting().create().toJson(array));
    writer.flush();
  }

  public Stream<JcpAiCacheRecord> stream() {
    return this.records.values().stream();
  }
}
