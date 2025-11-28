package org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonElement;
import net.lenni0451.commons.gson.elements.GsonObject;

import java.util.ArrayList;
import java.util.List;

@Value
public class DisplayProperties {
    public static DisplayProperties fromJson(final JsonObject json) {
        return fromJson(new GsonObject(json));
    }

    public static DisplayProperties fromJson(final GsonObject json) {
        final List<AvailableGame> games = new ArrayList<>();
        if (json.hasArray("availableGames")) {
            for (GsonElement e : json.reqArray("availableGames")) {
                games.add(AvailableGame.fromJson(e.asObject()));
            }
        }

        return new DisplayProperties(
                games,
                json.getString("creatorName", null),
                json.getString("maxClientVersion", null),
                json.getString("minClientVersion", null),
                json.getString("news", null),
                json.getString("newsTitle", null),
                json.getString("originalCreatorId", null),
                json.getInt("port", 19132),
                json.getString("requireXBL", "False"),
                json.getString("storePageId", null),
                json.getString("url", null),
                json.getString("whitelistUrl", null),
                json.getString("allowListUrl", null),
                json.getString("experienceId", null),
                json.getBoolean("isTop", false)
        );
    }

    public static JsonObject toJson(final DisplayProperties props) {
        final JsonObject json = new JsonObject();

        final JsonArray games = new JsonArray();
        for (AvailableGame game : props.availableGames) games.add(AvailableGame.toJson(game));
        json.add("availableGames", games);

        if (props.creatorName != null) json.addProperty("creatorName", props.creatorName);
        if (props.maxClientVersion != null) json.addProperty("maxClientVersion", props.maxClientVersion);
        if (props.minClientVersion != null) json.addProperty("minClientVersion", props.minClientVersion);
        if (props.news != null) json.addProperty("news", props.news);
        if (props.newsTitle != null) json.addProperty("newsTitle", props.newsTitle);
        if (props.originalCreatorId != null) json.addProperty("originalCreatorId", props.originalCreatorId);
        
        json.addProperty("port", props.port);
        if (props.requireXBL != null) json.addProperty("requireXBL", props.requireXBL);
        if (props.storePageId != null) json.addProperty("storePageId", props.storePageId);
        if (props.url != null) json.addProperty("url", props.url);
        if (props.whitelistUrl != null) json.addProperty("whitelistUrl", props.whitelistUrl);
        if (props.allowListUrl != null) json.addProperty("allowListUrl", props.allowListUrl);
        if (props.experienceId != null) json.addProperty("experienceId", props.experienceId);
        
        json.addProperty("isTop", props.isTop);
        return json;
    }

    List<AvailableGame> availableGames;
    String creatorName;
    String maxClientVersion;
    String minClientVersion;
    String news;
    String newsTitle;
    String originalCreatorId;
    int port;
    String requireXBL;
    String storePageId;
    String url;
    String whitelistUrl;
    String allowListUrl;
    String experienceId;
    boolean isTop;
}
