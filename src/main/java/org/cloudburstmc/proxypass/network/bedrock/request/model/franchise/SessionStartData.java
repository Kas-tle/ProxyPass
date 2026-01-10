package org.cloudburstmc.proxypass.network.bedrock.request.model.franchise;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionStartData {
    private final String deviceId;
    private final String playFabSessionTicket;
    private final String gameVersion; // e.g. "1.21.20"

    public JsonObject toJson() {
        JsonObject root = new JsonObject();

        // Device Object
        JsonObject device = new JsonObject();
        device.addProperty("applicationType", "MinecraftPE");
        device.add("capabilities", new JsonArray()); // Empty array []
        device.addProperty("gameVersion", gameVersion);
        device.addProperty("id", deviceId);
        device.addProperty("memory", "8589934592"); // 8GB
        device.addProperty("platform", "Windows10");
        device.addProperty("playFabTitleId", "20CA2");
        device.addProperty("storePlatform", "uwp.store");
        device.add("treatmentOverrides", JsonNull.INSTANCE); // Explicit null
        device.addProperty("type", "Windows10");
        root.add("device", device);

        // User Object
        JsonObject user = new JsonObject();
        user.addProperty("language", "en");
        user.addProperty("languageCode", "en-US");
        user.addProperty("regionCode", "US");
        user.addProperty("token", playFabSessionTicket);
        user.addProperty("tokenType", "PlayFab");
        root.add("user", user);

        return root;
    }
}
