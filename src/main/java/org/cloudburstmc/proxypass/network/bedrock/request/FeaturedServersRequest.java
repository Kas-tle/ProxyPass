package org.cloudburstmc.proxypass.network.bedrock.request;

import net.lenni0451.commons.gson.elements.GsonObject;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.HttpHeaders;
import net.lenni0451.commons.httpclient.requests.impl.PostRequest;
import net.raphimc.minecraftauth.bedrock.model.MinecraftSession;
import net.raphimc.minecraftauth.bedrock.responsehandler.MinecraftResponseHandler;
import org.cloudburstmc.proxypass.network.bedrock.request.model.FeaturedServers;

import java.io.IOException;
import java.net.MalformedURLException;

public class FeaturedServersRequest extends PostRequest implements MinecraftResponseHandler<FeaturedServers> {
    public FeaturedServersRequest(MinecraftSession session) throws MalformedURLException {
        super("https://gatherings.franchise.minecraft-services.net/api/v2.0/discovery/blob/client");
        this.setHeader(HttpHeaders.AUTHORIZATION, session.getAuthorizationHeader());
    }

    @Override
    public FeaturedServers handle(final HttpResponse response, final GsonObject json) throws IOException {
        return FeaturedServers.fromJson(json);
    }
}
