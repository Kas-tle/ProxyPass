package org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers;

import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonObject;

@Value
public class ServerImage {
    public static ServerImage fromJson(JsonObject json) { return fromJson(new GsonObject(json)); }
    
    public static ServerImage fromJson(GsonObject json) {
        return new ServerImage(
                json.getString("Tag", null),
                json.getString("Id", null),
                json.getString("Type", null),
                json.getString("Url", null)
        );
    }
    
    public static JsonObject toJson(ServerImage img) {
        JsonObject json = new JsonObject();
        if (img.tag != null) json.addProperty("Tag", img.tag);
        if (img.id != null) json.addProperty("Id", img.id);
        if (img.type != null) json.addProperty("Type", img.type);
        if (img.url != null) json.addProperty("Url", img.url);
        return json;
    }
    
    String tag, id, type, url;
}
