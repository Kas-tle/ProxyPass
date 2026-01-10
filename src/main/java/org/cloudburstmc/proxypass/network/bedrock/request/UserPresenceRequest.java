package org.cloudburstmc.proxypass.network.bedrock.request;

import net.lenni0451.commons.gson.elements.GsonObject;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.HttpHeaders;
import net.lenni0451.commons.httpclient.requests.impl.PostRequest;
import net.raphimc.minecraftauth.bedrock.responsehandler.MinecraftResponseHandler;
import net.raphimc.minecraftauth.util.http.content.JsonContent;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;

import java.net.MalformedURLException;

public class UserPresenceRequest extends PostRequest implements MinecraftResponseHandler<GsonObject> {

    public UserPresenceRequest(XblXstsToken xstsToken, String xuid) throws MalformedURLException {
        super(String.format("https://userpresence.xboxlive.com/users/xuid(%s)/devices/current/titles/current", xuid));
        
        this.setContent(new JsonContent(
                new GsonObject().add("state", "active")
        ));
        this.setHeader(HttpHeaders.AUTHORIZATION, xstsToken.getAuthorizationHeader());
        this.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        this.setHeader("x-xbl-contract-version", "3");
    }

    @Override
    public GsonObject handle(HttpResponse response, GsonObject json) {
        return json;
    }
}