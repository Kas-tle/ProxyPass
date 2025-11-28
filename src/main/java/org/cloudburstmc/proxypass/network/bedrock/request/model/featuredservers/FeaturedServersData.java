package org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonElement;
import net.lenni0451.commons.gson.elements.GsonObject;

import java.util.ArrayList;
import java.util.List;

@Value
public class FeaturedServersData {
    public static FeaturedServersData fromJson(final JsonObject json) {
        return fromJson(new GsonObject(json));
    }

    public static FeaturedServersData fromJson(final GsonObject json) {
        final List<FeaturedServer> items = new ArrayList<>();
        if (json.hasArray("Items")) {
            for (final GsonElement element : json.reqArray("Items")) {
                items.add(FeaturedServer.fromJson(element.asObject()));
            }
        }

        return new FeaturedServersData(
                json.getInt("Count", 0),
                items,
                json.getString("ConfigurationName", null)
        );
    }

    public static JsonObject toJson(final FeaturedServersData data) {
        final JsonObject json = new JsonObject();
        json.addProperty("Count", data.count);

        final JsonArray itemsArray = new JsonArray();
        for (final FeaturedServer item : data.items) {
            itemsArray.add(FeaturedServer.toJson(item));
        }
        json.add("Items", itemsArray);

        if (data.configurationName != null) {
            json.addProperty("ConfigurationName", data.configurationName);
        }
        return json;
    }

    int count;
    List<FeaturedServer> items;
    String configurationName;
}
