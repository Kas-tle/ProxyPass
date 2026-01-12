package org.cloudburstmc.proxypass.network.bedrock.request;

import com.google.gson.Gson;
import net.lenni0451.commons.gson.elements.GsonObject;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.HttpHeaders;
import net.lenni0451.commons.httpclient.requests.impl.PostRequest;
import net.raphimc.minecraftauth.bedrock.responsehandler.MinecraftResponseHandler;
import net.raphimc.minecraftauth.util.http.content.JsonContent;
import net.raphimc.minecraftauth.xbl.model.XblXstsToken;
import org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory.SessionHandleQueryData;
import org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory.SessionHandleQueryResult;

import java.net.MalformedURLException;

public class SessionHandleQueryRequest extends PostRequest implements MinecraftResponseHandler<SessionHandleQueryResult> {

    public SessionHandleQueryRequest(XblXstsToken token, SessionHandleQueryData data) throws MalformedURLException {
        super("https://sessiondirectory.xboxlive.com/handles/query?include=relatedInfo,customProperties");

        this.setContent(new JsonContent(data.toJson()));
        this.setHeader(HttpHeaders.AUTHORIZATION, token.getAuthorizationHeader());
        this.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        this.setHeader("x-xbl-contract-version", "107");
    }

    @Override
    public SessionHandleQueryResult handle(HttpResponse response, GsonObject json) {
        return new Gson().fromJson(json.toString(), SessionHandleQueryResult.class);
    }
}