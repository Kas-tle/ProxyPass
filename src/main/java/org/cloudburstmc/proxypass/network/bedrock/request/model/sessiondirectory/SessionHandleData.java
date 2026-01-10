package org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory;

import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionHandleData {
    private final String scid;
    private final String templateName;
    private final String sessionName;

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("type", "activity");

        JsonObject sessionRef = new JsonObject();
        sessionRef.addProperty("scid", scid);
        sessionRef.addProperty("templateName", templateName);
        sessionRef.addProperty("name", sessionName);
        root.add("sessionRef", sessionRef);

        return root;
    }
}
