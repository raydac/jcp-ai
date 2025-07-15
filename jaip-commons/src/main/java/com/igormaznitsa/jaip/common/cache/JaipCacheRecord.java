package com.igormaznitsa.jaip.common.cache;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.UUID;

public class JaipCacheRecord {
  private UUID uuid;
  private Instant instant;
  private String key;
  private String result;
  private String fileName;
  private int line;

  public JaipCacheRecord() {

  }

  public UUID getUuid() {
    return this.uuid;
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

  public void setUuid(UUID uid) {
    this.uuid = requireNonNull(uid);
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
