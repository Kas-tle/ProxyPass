package org.cloudburstmc.proxypass.network.bedrock.request;

import net.lenni0451.commons.gson.elements.GsonObject;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.requests.impl.PutRequest;
import net.raphimc.minecraftauth.bedrock.model.MinecraftSession;
import net.raphimc.minecraftauth.bedrock.responsehandler.MinecraftResponseHandler;
import net.raphimc.minecraftauth.util.http.content.JsonContent;
import org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory.SessionRequestData;

import java.net.MalformedURLException;

public class SessionDirectoryRequest extends PutRequest implements MinecraftResponseHandler<GsonObject> {

    public SessionDirectoryRequest(MinecraftSession session, String scid, String template, String sessionName, SessionRequestData data) throws MalformedURLException {
        super(String.format("https://sessiondirectory.xboxlive.com/serviceconfigs/%s/sessiontemplates/%s/sessions/%s", scid, template, sessionName));
        
        this.setContent(new JsonContent(data.toJson()));
        this.setHeader("Authorization", session.getAuthorizationHeader());
        this.setHeader("x-xbl-contract-version", "107");
    }

    @Override
    public GsonObject handle(HttpResponse response, GsonObject json) {
        return json;
    }
}