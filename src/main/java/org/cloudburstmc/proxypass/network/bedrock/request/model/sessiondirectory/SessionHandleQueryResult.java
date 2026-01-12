package org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SessionHandleQueryResult {
    private List<HandleResult> results;

    @Data
    public static class HandleResult {
        private int version;
        private String type;
        private SessionRef sessionRef;
        private String titleId;
        private String ownerXuid;
        private String id;
        private String inviteProtocol;
        private Map<String, GameType> gameTypes;
        private String createTime;
        private RelatedInfo relatedInfo;
        private CustomProperties customProperties;
    }

    @Data
    public static class SessionRef {
        private String scid;
        private String templateName;
        private String name;
    }

    @Data
    public static class GameType {
        private String titleId;
        private String pfn;
        private List<String> boundPfns;
    }

    @Data
    public static class RelatedInfo {
        private String joinRestriction;
        private boolean closed;
        private int maxMembersCount;
        private int membersCount;
        private String visibility;
        private String inviteProtocol;
        private String postedTime;
    }

    @Data
    public static class CustomProperties {
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
}