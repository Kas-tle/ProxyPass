package org.cloudburstmc.proxypass.network.bedrock.request.model.experience;

import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonObject;

@Value
public class ExperienceResult {
    public static ExperienceResult fromJson(final JsonObject json) {
        return fromJson(new GsonObject(json));
    }

    public static ExperienceResult fromJson(final GsonObject json) {
        return new ExperienceResult(
                json.getString("networkProtocol", "Default"),
                json.reqString("ipV4Address"),
                json.reqInt("port")
        );
    }

    public static JsonObject toJson(final ExperienceResult result) {
        final JsonObject json = new JsonObject();
        json.addProperty("networkProtocol", result.networkProtocol);
        json.addProperty("ipV4Address", result.ipV4Address);
        json.addProperty("port", result.port);
        return json;
    }

    String networkProtocol;
    String ipV4Address;
    int port;
}
