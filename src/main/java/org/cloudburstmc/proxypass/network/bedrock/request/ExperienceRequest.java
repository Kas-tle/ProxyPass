package org.cloudburstmc.proxypass.network.bedrock.request;

import com.google.gson.JsonObject;
import net.lenni0451.commons.gson.elements.GsonObject;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.HttpHeaders;
import net.lenni0451.commons.httpclient.requests.impl.PostRequest;
import net.raphimc.minecraftauth.bedrock.model.MinecraftSession;
import net.raphimc.minecraftauth.bedrock.responsehandler.MinecraftResponseHandler;
import net.raphimc.minecraftauth.util.http.content.JsonContent;
import org.cloudburstmc.proxypass.network.bedrock.request.model.Experience;

import java.net.MalformedURLException;

public class ExperienceRequest extends PostRequest implements MinecraftResponseHandler<Experience> {
    public ExperienceRequest(MinecraftSession session, String experienceId) throws MalformedURLException {
        super("https://gatherings.franchise.minecraft-services.net/api/v2.0/join/experience");
        final JsonObject postData = new JsonObject();
        postData.addProperty("experienceId", experienceId);

        this.setContent(new JsonContent(postData));
        this.setHeader(HttpHeaders.AUTHORIZATION, session.getAuthorizationHeader());
    }

    @Override
    public Experience handle(final HttpResponse response, final GsonObject json) {
        return Experience.fromJson(json);
    }
}
