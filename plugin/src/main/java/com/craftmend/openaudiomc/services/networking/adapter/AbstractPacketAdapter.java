package com.craftmend.openaudiomc.services.networking.adapter;

import com.craftmend.openaudiomc.services.networking.abstracts.AbstractPacketPayload;
import com.google.gson.*;

import java.lang.reflect.Type;

public class AbstractPacketAdapter implements JsonSerializer<AbstractPacketPayload>, JsonDeserializer<AbstractPacketPayload> {

    @Override
    public JsonElement serialize(AbstractPacketPayload src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.add("type", new JsonPrimitive(src.getClass().getSimpleName()));
        result.add("payload", context.serialize(src, src.getClass()));

        return result;
    }

    @Override
    public AbstractPacketPayload deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String type = jsonObject.get("type").getAsString();
        JsonElement element = jsonObject.get("payload");

        try {
            return context.deserialize(element, Class.forName("com.craftmend.openaudiomc.services.networking.payloads." + type));
        } catch (ClassNotFoundException exception) {
            throw new JsonParseException("Unknown element type: " + type, exception);
        }
    }
}