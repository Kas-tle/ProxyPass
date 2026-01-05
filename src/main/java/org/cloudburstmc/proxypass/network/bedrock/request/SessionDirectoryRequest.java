package org.cloudburstmc.proxypass.network.bedrock.request;

import net.lenni0451.commons.gson.elements.GsonObject;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.HttpHeaders;
import net.lenni0451.commons.httpclient.requests.impl.PutRequest;
import net.raphimc.minecraftauth.bedrock.responsehandler.MinecraftResponseHandler;
import net.raphimc.minecraftauth.util.http.content.JsonContent;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;
import org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory.SessionRequestData;

import java.net.MalformedURLException;

public class SessionDirectoryRequest extends PutRequest implements MinecraftResponseHandler<GsonObject> {

    public SessionDirectoryRequest(XblXstsToken token, String scid, String template, String sessionName, SessionRequestData data) throws MalformedURLException {
        super(String.format("https://sessiondirectory.xboxlive.com/serviceconfigs/%s/sessionTemplates/%s/sessions/%s", scid, template, sessionName));
        
        this.setContent(new JsonContent(data.toJson()));
        this.setHeader(HttpHeaders.AUTHORIZATION, token.getAuthorizationHeader());
        this.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        this.setHeader("x-xbl-contract-version", "107");
    }

    @Override
    public GsonObject handle(HttpResponse response, GsonObject json) {
        return json;
    }
}