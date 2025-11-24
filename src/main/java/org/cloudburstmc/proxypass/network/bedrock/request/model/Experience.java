package org.cloudburstmc.proxypass.network.bedrock.request.model;

import com.google.gson.JsonObject;
import lombok.Value;
import net.lenni0451.commons.gson.elements.GsonObject;
import org.cloudburstmc.proxypass.network.bedrock.request.model.experience.ExperienceResult;

@Value
public class Experience {
    public static Experience fromJson(final JsonObject json) {
        return fromJson(new GsonObject(json));
    }

    public static Experience fromJson(final GsonObject json) {
        return new Experience(
                ExperienceResult.fromJson(json.reqObject("result"))
        );
    }

    public static JsonObject toJson(final Experience response) {
        final JsonObject json = new JsonObject();
        json.add("result", ExperienceResult.toJson(response.result));
        return json;
    }

    ExperienceResult result;
}
