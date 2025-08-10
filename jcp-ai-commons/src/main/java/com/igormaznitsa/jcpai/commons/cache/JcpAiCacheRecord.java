package com.igormaznitsa.jcpai.commons.cache;

import static java.util.Objects.requireNonNull;

import com.google.gson.JsonObject;
import java.time.Instant;

public class JcpAiCacheRecord {
  private Instant instant;
  private String key;
  private String result;
  private String fileName;
  private int line;
  private long sinceUse;

  public JcpAiCacheRecord() {

  }

  public JcpAiCacheRecord(final JsonObject jsonObject) {
    this.instant = Instant.parse(jsonObject.get("instant").getAsString());
    this.key = jsonObject.get("key").getAsString();
    this.result = jsonObject.get("result").getAsString();
    this.fileName = jsonObject.get("fileName").getAsString();
    this.line = jsonObject.get("line").getAsInt();
    this.sinceUse = jsonObject.has("sinceUse") ? jsonObject.get("sinceUse").getAsLong() : 0L;
  }

  public JsonObject toJsonObject() {
    final JsonObject result = new JsonObject();
    result.addProperty("instant", this.instant.toString());
    result.addProperty("key", this.key);
    result.addProperty("fileName", this.fileName);
    result.addProperty("line", this.line);
    result.addProperty("result", this.result);
    result.addProperty("sinceUse", this.sinceUse);
    return result;
  }

  public long getSinceUse() {
    return this.sinceUse;
  }

  public void setSinceUse(final long value) {
    this.sinceUse = value;
  }

  public String getFileName() {
    return this.fileName;
  }

  public void setFileName(final String fileName) {
    this.fileName = requireNonNull(fileName);
  }

  public int getLine() {
    return this.line;
  }

  public void setLine(final int line) {
    this.line = line;
  }

  public Instant getInstant() {
    return this.instant;
  }

  public void setInstant(Instant instant) {
    this.instant = requireNonNull(instant);
  }

  public String getKey() {
    return this.key;
  }

  public void setKey(String key) {
    this.key = requireNonNull(key);
  }

  public String getResult() {
    return this.result;
  }

  public void setResult(String result) {
    this.result = requireNonNull(result);
  }
}
