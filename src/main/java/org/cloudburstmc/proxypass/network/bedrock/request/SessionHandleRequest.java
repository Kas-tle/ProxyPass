package org.cloudburstmc.proxypass.network.bedrock.request;

import net.lenni0451.commons.gson.elements.GsonObject;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.requests.impl.PostRequest;
import net.raphimc.minecraftauth.bedrock.model.MinecraftSession;
import net.raphimc.minecraftauth.bedrock.responsehandler.MinecraftResponseHandler;
import net.raphimc.minecraftauth.util.http.content.JsonContent;
import org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory.SessionHandleData;

import java.net.MalformedURLException;

public class SessionHandleRequest extends PostRequest implements MinecraftResponseHandler<GsonObject> {

    public SessionHandleRequest(MinecraftSession session, SessionHandleData data) throws MalformedURLException {
        super("https://sessiondirectory.xboxlive.com/handles");

        this.setContent(new JsonContent(data.toJson()));
        this.setHeader("Authorization", session.getAuthorizationHeader());
        this.setHeader("x-xbl-contract-version", "107");
    }

    @Override
    public GsonObject handle(HttpResponse response, GsonObject json) {
        return json;
    }
}
