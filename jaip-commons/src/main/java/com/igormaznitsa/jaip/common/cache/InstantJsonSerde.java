package com.igormaznitsa.jaip.common.cache;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.Instant;

public final class InstantJsonSerde implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
  public static final InstantJsonSerde INSTANCE = new InstantJsonSerde();

  private InstantJsonSerde(){

  }

  @Override
  public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    return Instant.parse(json.getAsString());
  }

  @Override
  public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(src.toString());
  }
}
