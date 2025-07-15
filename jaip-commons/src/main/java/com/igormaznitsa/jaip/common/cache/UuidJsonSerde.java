package com.igormaznitsa.jaip.common.cache;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.util.UUID;

public final class UuidJsonSerde implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
  public static final UuidJsonSerde INSTANCE = new UuidJsonSerde();

  private UuidJsonSerde() {

  }

  @Override
  public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    return UUID.fromString(json.getAsString());
  }

  @Override
  public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(src.toString());
  }
}
