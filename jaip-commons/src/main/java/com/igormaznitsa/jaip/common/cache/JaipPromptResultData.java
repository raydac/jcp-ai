package com.igormaznitsa.jaip.common.cache;

import static java.util.Objects.requireNonNull;

import com.google.gson.FormattingStyle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

public class JaipPromptResultData {

  private static final Gson GSON = new GsonBuilder()
      .setFormattingStyle(FormattingStyle.PRETTY)
      .registerTypeAdapter(Instant.class, InstantJsonSerde.INSTANCE)
      .registerTypeAdapter(UUID.class, UuidJsonSerde.INSTANCE)
      .create();

  private final LinkedHashMap<String, JaipCacheRecord> records = new LinkedHashMap<>();
  private boolean changed;

  public JaipPromptResultData() throws IOException {
  }

  public synchronized int size() {
    return this.records.size();
  }

  public synchronized void read(final Reader reader) {
    this.records.clear();
    final JaipCacheRecord[] array = GSON.fromJson(reader, JaipCacheRecord[].class);
    for (final JaipCacheRecord r : array) {
      this.records.put(r.getKey(), r);
    }
    this.changed = false;
  }

  public synchronized String find(final String key) {
    final JaipCacheRecord record = this.records.get(requireNonNull(key));
    return record == null ? null : record.getResult();
  }

  public synchronized void put(final String key, final String fileName, final int line, final String response) {
    this.changed = true;

    final JaipCacheRecord newRecord = new JaipCacheRecord();
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

  public synchronized void write(final Writer writer) throws IOException {
    final JaipCacheRecord[] array = this.records.values().toArray(JaipCacheRecord[]::new);
    GSON.toJson(array, writer);
    writer.flush();
  }
}
