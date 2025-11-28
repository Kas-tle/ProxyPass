package org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers;

import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonObject;

import java.util.HashMap;
import java.util.Map;

@Value
public class LocalizedText {
    public static LocalizedText fromJson(final JsonObject json) {
        return fromJson(new GsonObject(json));
    }

    public static LocalizedText fromJson(final GsonObject json) {
        final Map<String, String> translations = new HashMap<>();
        
        for (String key : json.keySet()) {
            translations.put(key, json.getString(key));
        }

        String neutral = translations.get("neutral");
        if (neutral == null) {
            neutral = translations.getOrDefault("NEUTRAL", "");
        }

        return new LocalizedText(neutral, translations);
    }

    public static JsonObject toJson(final LocalizedText text) {
        final JsonObject json = new JsonObject();
        for (Map.Entry<String, String> entry : text.translations.entrySet()) {
            json.addProperty(entry.getKey(), entry.getValue());
        }
        return json;
    }

    String neutral;
    Map<String, String> translations;
}
