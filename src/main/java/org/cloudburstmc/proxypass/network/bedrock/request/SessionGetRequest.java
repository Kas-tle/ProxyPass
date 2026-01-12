package org.cloudburstmc.proxypass.network.bedrock.request;

import com.google.gson.Gson;
import net.lenni0451.commons.gson.elements.GsonObject;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.HttpHeaders;
import net.lenni0451.commons.httpclient.requests.impl.GetRequest;
import net.raphimc.minecraftauth.bedrock.responsehandler.MinecraftResponseHandler;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;
import org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory.SessionGetResult;

import java.net.MalformedURLException;

public class SessionGetRequest extends GetRequest implements MinecraftResponseHandler<SessionGetResult> {

    public SessionGetRequest(XblXstsToken token, String scid, String templateName, String sessionName) throws MalformedURLException {
        super(String.format("https://sessiondirectory.xboxlive.com/serviceconfigs/%s/sessionTemplates/%s/sessions/%s", scid, templateName, sessionName));

        this.setHeader(HttpHeaders.AUTHORIZATION, token.getAuthorizationHeader());
        this.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        this.setHeader("x-xbl-contract-version", "107");
    }

    @Override
    public SessionGetResult handle(HttpResponse response, GsonObject json) {
        return new Gson().fromJson(json.toString(), SessionGetResult.class);
    }
}