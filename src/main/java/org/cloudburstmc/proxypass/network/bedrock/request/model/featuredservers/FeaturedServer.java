package org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonElement;
import net.lenni0451.commons.gson.elements.GsonObject;

import java.util.ArrayList;
import java.util.List;

@Value
public class FeaturedServer {
    public static FeaturedServer fromJson(final JsonObject json) {
        return fromJson(new GsonObject(json));
    }

    public static FeaturedServer fromJson(final GsonObject json) {
        final List<String> platforms = new ArrayList<>();
        if (json.hasArray("Platforms")) {
            for (GsonElement e : json.reqArray("Platforms")) platforms.add(e.asString());
        }

        final List<String> tags = new ArrayList<>();
        if (json.hasArray("Tags")) {
            for (GsonElement e : json.reqArray("Tags")) tags.add(e.asString());
        }

        final List<ServerImage> images = new ArrayList<>();
        if (json.hasArray("Images")) {
            for (GsonElement e : json.reqArray("Images")) images.add(ServerImage.fromJson(e.asObject()));
        }

        return new FeaturedServer(
                json.getString("Id", null),
                json.getString("Type", null),
                json.optObject("Title").map(LocalizedText::fromJson).orElse(null),
                json.optObject("Description").map(LocalizedText::fromJson).orElse(null),
                json.getString("ContentType", null),
                platforms,
                tags,
                json.getString("CreationDate", null),
                json.getString("LastModifiedDate", null),
                json.getString("StartDate", null),
                images,
                json.optObject("DisplayProperties").map(DisplayProperties::fromJson).orElse(null),
                json.optObject("CreatorEntity").map(CreatorEntity::fromJson).orElse(null),
                json.optObject("CreatorEntityKey").map(CreatorEntity::fromJson).orElse(null)
        );
    }

    public static JsonObject toJson(final FeaturedServer server) {
        final JsonObject json = new JsonObject();
        if (server.id != null) json.addProperty("Id", server.id);
        if (server.type != null) json.addProperty("Type", server.type);
        if (server.title != null) json.add("Title", LocalizedText.toJson(server.title));
        if (server.description != null) json.add("Description", LocalizedText.toJson(server.description));
        if (server.contentType != null) json.addProperty("ContentType", server.contentType);

        final JsonArray platforms = new JsonArray();
        server.platforms.forEach(platforms::add);
        json.add("Platforms", platforms);

        final JsonArray tags = new JsonArray();
        server.tags.forEach(tags::add);
        json.add("Tags", tags);

        if (server.creationDate != null) json.addProperty("CreationDate", server.creationDate);
        if (server.lastModifiedDate != null) json.addProperty("LastModifiedDate", server.lastModifiedDate);
        if (server.startDate != null) json.addProperty("StartDate", server.startDate);
        
        json.add("Contents", new JsonArray());
        json.add("ItemReferences", new JsonArray());

        final JsonArray images = new JsonArray();
        for (ServerImage img : server.images) images.add(ServerImage.toJson(img));
        json.add("Images", images);

        if (server.displayProperties != null) {
            json.add("DisplayProperties", DisplayProperties.toJson(server.displayProperties));
        }
        
        json.addProperty("IsStackable", false);
        json.addProperty("IsHydrated", false);

        if (server.creatorEntity != null) json.add("CreatorEntity", CreatorEntity.toJson(server.creatorEntity));
        if (server.creatorEntityKey != null) json.add("CreatorEntityKey", CreatorEntity.toJson(server.creatorEntityKey));
        
        json.add("Keywords", new JsonObject());

        return json;
    }

    String id;
    String type;
    LocalizedText title;
    LocalizedText description;
    String contentType;
    List<String> platforms;
    List<String> tags;
    String creationDate;
    String lastModifiedDate;
    String startDate;
    List<ServerImage> images;
    DisplayProperties displayProperties;
    CreatorEntity creatorEntity;
    CreatorEntity creatorEntityKey;
}
