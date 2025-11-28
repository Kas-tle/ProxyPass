package org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers;

import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonObject;

@Value
public class AvailableGame {
    public static AvailableGame fromJson(JsonObject json) { return fromJson(new GsonObject(json)); }
    
    public static AvailableGame fromJson(GsonObject json) {
        return new AvailableGame(
                json.getString("title", null),
                json.getString("subtitle", null),
                json.getString("description", null),
                json.getString("imageTag", null)
        );
    }
    
    public static JsonObject toJson(AvailableGame ag) {
        JsonObject json = new JsonObject();
        if (ag.title != null) json.addProperty("title", ag.title);
        if (ag.subtitle != null) json.addProperty("subtitle", ag.subtitle);
        if (ag.description != null) json.addProperty("description", ag.description);
        if (ag.imageTag != null) json.addProperty("imageTag", ag.imageTag);
        return json;
    }
    
    String title, subtitle, description, imageTag;
}
