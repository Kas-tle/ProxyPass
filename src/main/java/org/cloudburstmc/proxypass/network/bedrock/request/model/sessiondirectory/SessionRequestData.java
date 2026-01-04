package org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionRequestData {
    private final String connectionId;
    private final String subscriptionId; // Added: RTA Subscription ID
    private final String xuid;
    private final long netherNetId;
    private final int maxPlayers;
    private final int currentPlayers;
    private final String sessionName;
    private final String version; // e.g. "1.21.30"
    private final int protocol;   // e.g. 712

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        
        JsonObject properties = new JsonObject();
        JsonObject system = new JsonObject();
        system.addProperty("joinRestriction", "followed");
        system.addProperty("readRestriction", "followed");
        system.addProperty("closed", false);
        properties.add("system", system);

        JsonObject custom = new JsonObject();
        custom.addProperty("hostName", sessionName);
        custom.addProperty("worldName", sessionName);
        custom.addProperty("version", version);
        custom.addProperty("MemberCount", Math.max(currentPlayers, 2));
        custom.addProperty("MaxMemberCount", maxPlayers);
        custom.addProperty("Joinability", "joinable_by_friends");
        custom.addProperty("rakNetGUID", "");
        custom.addProperty("worldType", "Survival");
        custom.addProperty("protocol", protocol);
        custom.addProperty("BroadcastSetting", 3);
        custom.addProperty("OnlineCrossPlatformGame", true);
        custom.addProperty("CrossPlayDisabled", false);
        custom.addProperty("TitleId", 0);
        custom.addProperty("TransportLayer", 2);
        custom.addProperty("LanGame", false);
        custom.addProperty("isHardcore", false);
        custom.addProperty("isEditorWorld", false);
        custom.addProperty("levelId", "level");

        // Supported Connections
        JsonArray supportedConnections = new JsonArray();
        JsonObject netherNetConnection = new JsonObject();
        netherNetConnection.addProperty("ConnectionType", 3);
        netherNetConnection.addProperty("HostIpAddress", "");
        netherNetConnection.addProperty("HostPort", 0);
        netherNetConnection.addProperty("NetherNetId", netherNetId);
        supportedConnections.add(netherNetConnection);
        custom.add("SupportedConnections", supportedConnections);

        properties.add("custom", custom);
        root.add("properties", properties);

        // Members
        JsonObject members = new JsonObject();
        JsonObject me = new JsonObject();
        
        // Constants
        JsonObject meConstants = new JsonObject();
        JsonObject meSystemConstants = new JsonObject();
        meSystemConstants.addProperty("xuid", xuid);
        meSystemConstants.addProperty("initialize", true);
        meConstants.add("system", meSystemConstants);
        me.add("constants", meConstants);

        // Properties
        JsonObject meProperties = new JsonObject();
        JsonObject meSystemProperties = new JsonObject();
        meSystemProperties.addProperty("active", true);
        meSystemProperties.addProperty("connection", connectionId);
        
        // Add Subscription Info (Matches bedrock-portal)
        if (subscriptionId != null && !subscriptionId.isEmpty()) {
            JsonObject subscription = new JsonObject();
            subscription.addProperty("id", subscriptionId);
            JsonArray changeTypes = new JsonArray();
            changeTypes.add("everything");
            subscription.add("changeTypes", changeTypes);
            meSystemProperties.add("subscription", subscription);
        }

        meProperties.add("system", meSystemProperties);
        me.add("properties", meProperties);
        
        members.add("me", me);
        root.add("members", members);

        return root;
    }
}
