package org.cloudburstmc.proxypass.network.bedrock.request.model;

import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonObject;
import org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers.FeaturedServersData;

@Value
public class FeaturedServers {
    public static FeaturedServers fromJson(final JsonObject json) {
        return fromJson(new GsonObject(json));
    }

    public static FeaturedServers fromJson(final GsonObject json) {
        return new FeaturedServers(
                json.reqString("status"),
                json.reqInt("code"),
                FeaturedServersData.fromJson(json.reqObject("data"))
        );
    }

    public static JsonObject toJson(final FeaturedServers response) {
        final JsonObject json = new JsonObject();
        json.addProperty("status", response.status);
        json.addProperty("code", response.code);
        json.add("data", FeaturedServersData.toJson(response.data));
        return json;
    }

    String status;
    int code;
    FeaturedServersData data;
}
