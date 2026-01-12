package org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SessionGetResult {
    private MembersInfo membersInfo;
    private SessionConstants constants;
    private SessionProperties properties;
    private JsonObject servers;
    private Map<String, SessionMember> members;
    private String correlationId;
    private int contractVersion;
    private String branch;
    private int changeNumber;
    private String startTime;

    @Data
    public static class MembersInfo {
        private int first;
        private int next;
        private int count;
        private int accepted;
        private int active;
    }

    @Data
    public static class SessionConstants {
        private SessionSystemConstants system;
        private JsonObject custom;
    }

    @Data
    public static class SessionSystemConstants {
        private int readyRemovalTimeout;
        private int reservedRemovalTimeout;
        private int sessionEmptyTimeout;
        private int inactiveRemovalTimeout;
        private int version;
        private int maxMembersCount;
        private String visibility;
        private SessionCapabilities capabilities;
        private String inviteProtocol;
        private MemberInitialization memberInitialization;
    }

    @Data
    public static class SessionCapabilities {
        private boolean connectivity;
        private boolean connectionRequiredForActiveMembers;
        private boolean gameplay;
        private boolean crossPlay;
        private boolean userAuthorizationStyle;
    }

    @Data
    public static class MemberInitialization {
        private int membersNeededToStart;
    }

    @Data
    public static class SessionProperties {
        private SessionSystemProperties system;
        private SessionCustomProperties custom;
    }

    @Data
    public static class SessionSystemProperties {
        private String joinRestriction;
        private String readRestriction;
        private List<String> turn;
    }

    @Data
    public static class SessionCustomProperties {
        private String hostName;
        private String ownerId;
        private String worldName;
        private String version;

        @SerializedName("MemberCount")
        private int memberCount;

        @SerializedName("MaxMemberCount")
        private int maxMemberCount;

        @SerializedName("Joinability")
        private String joinability;

        private String rakNetGUID;
        private String worldType;
        private int protocol;

        @SerializedName("BroadcastSetting")
        private int broadcastSetting;

        @SerializedName("OnlineCrossPlatformGame")
        private boolean onlineCrossPlatformGame;

        @SerializedName("CrossPlayDisabled")
        private boolean crossPlayDisabled;

        @SerializedName("TitleId")
        private long titleId;

        @SerializedName("TransportLayer")
        private int transportLayer;

        @SerializedName("LanGame")
        private boolean lanGame;

        private boolean isHardcore;
        private boolean isEditorWorld;
        private String levelId;

        @SerializedName("SupportedConnections")
        private List<SupportedConnection> supportedConnections;
    }

    @Data
    public static class SupportedConnection {
        @SerializedName("ConnectionType")
        private int connectionType;

        @SerializedName("HostIpAddress")
        private String hostIpAddress;

        @SerializedName("HostPort")
        private int hostPort;

        @SerializedName("NetherNetId")
        private String netherNetId;
    }

    @Data
    public static class SessionMember {
        private int next;
        private String joinTime;
        private MemberConstants constants;
        private MemberProperties properties;
        private String gamertag;
        private String activeTitleId;
    }

    @Data
    public static class MemberConstants {
        private MemberSystemConstants system;
        private JsonObject custom;
    }

    @Data
    public static class MemberSystemConstants {
        private boolean initialize;
        private String xuid;
        private int index;
    }

    @Data
    public static class MemberProperties {
        private MemberSystemProperties system;
        private JsonObject custom;
    }

    @Data
    public static class MemberSystemProperties {
        private Subscription subscription;
        private boolean active;
        private String connection;
    }

    @Data
    public static class Subscription {
        private String id;
        private List<String> changeTypes;
    }
}