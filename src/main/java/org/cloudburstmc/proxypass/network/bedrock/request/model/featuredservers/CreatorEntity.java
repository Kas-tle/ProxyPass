package org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers;

import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonObject;

@Value
public class CreatorEntity {
    public static CreatorEntity fromJson(JsonObject json) { return fromJson(new GsonObject(json)); }
    
    public static CreatorEntity fromJson(GsonObject json) {
        return new CreatorEntity(
                json.getString("Id", null),
                json.getString("Type", null),
                json.getString("TypeString", null)
        );
    }
    
    public static JsonObject toJson(CreatorEntity ce) {
        JsonObject json = new JsonObject();
        if (ce.id != null) json.addProperty("Id", ce.id);
        if (ce.type != null) json.addProperty("Type", ce.type);
        if (ce.typeString != null) json.addProperty("TypeString", ce.typeString);
        return json;
    }
    
    String id, type, typeString;
}
