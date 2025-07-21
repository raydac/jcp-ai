package com.igormaznitsa.jcpai.commons.cache;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.UUID;
import org.hisp.dhis.jsontree.JsonBuilder;
import org.hisp.dhis.jsontree.JsonNode;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonValue;

public class JcpAiCacheRecord {
  private UUID uuid;
  private Instant instant;
  private String key;
  private String result;
  private String fileName;
  private int line;

  private static final JsonBuilder.PrettyPrint PRETTY_JSON =
      new JsonBuilder.PrettyPrint(
          2,
          0,
          true,
          true,
          false);

  public JcpAiCacheRecord() {

  }

  public JcpAiCacheRecord(final JsonValue jsonObject) {
    if (jsonObject.isObject()) {
      final JsonObject value = jsonObject.asObject();
      this.uuid = UUID.fromString(value.getString("uuid").string());
      this.instant = Instant.parse(value.getString("instant").string());
      this.key = value.getString("key").string();
      this.result = value.getString("result").string();
      this.fileName = value.getString("fileName").string();
      this.line = value.getNumber("line").intValue();
    } else {
      throw new IllegalArgumentException("Expected JSON object: " + jsonObject);
    }
  }

  public JsonNode toJsonNode() {
    return JsonBuilder.createObject(PRETTY_JSON, x ->
        x.addString("uuid", this.uuid.toString())
            .addString("instant", this.instant.toString())
            .addString("key", this.key)
            .addString("fileName", this.fileName)
            .addNumber("line", this.line)
            .addString("result", this.result)
    );
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
