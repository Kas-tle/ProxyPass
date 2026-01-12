package org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory;

import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionHandleQueryData {
    private final String scid;
    private final String monikerXuid;

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "activity");
        root.addProperty("scid", scid);

        JsonObject owners = new JsonObject();
        JsonObject people = new JsonObject();
        people.addProperty("moniker", "people");
        people.addProperty("monikerXuid", monikerXuid);
        owners.add("people", people);
        root.add("owners", owners);

        return root;
    }
}