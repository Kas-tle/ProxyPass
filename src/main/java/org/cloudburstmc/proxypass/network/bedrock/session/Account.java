package org.cloudburstmc.proxypass.network.bedrock.session;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;

@Accessors(fluent = true)
@Data
@AllArgsConstructor
// adapted from https://github.com/ViaVersion/ViaProxy/blob/ca40e290092d99abd842f8cce645d8db407de105/src/main/java/net/raphimc/viaproxy/saves/impl/accounts/BedrockAccount.java#L29-L101
public class Account {
    private BedrockAuthManager authManager;

    public Account(JsonObject jsonObject, HttpClient httpClient, String gameVersion) throws Exception {
        this.authManager = BedrockAuthManager.fromJson(httpClient, gameVersion, jsonObject);
    }

    public JsonObject toJson() {
        JsonObject jsonObject = BedrockAuthManager.toJson(this.authManager);
        return jsonObject;
    }

    public boolean refresh() throws Exception {
        authManager.getMinecraftSession().refresh();
        authManager.getMinecraftCertificateChain().refresh();
        authManager.getMinecraftMultiplayerToken().refresh();
        return true;
    }
}
