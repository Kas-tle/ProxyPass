/*
 * Copyright (c) 2022, Valaphee.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.valaphee.service.minecraftservices.auth

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * @author Kevin Ludwig
 */
data class SessionStartRequest(
    @JsonProperty("device") val device: Device,
    @JsonProperty("user") val user: User
) {
    data class Device(
        @JsonProperty("applicationType") val applicationType: String,
        @JsonProperty("capabilities") val capabilities: List<String>,
        @JsonProperty("gameVersion") val gameVersion: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("memory") val memory: String,
        @JsonProperty("platform") val platform: String,
        @JsonProperty("playFabTitleId") val playFabTitleId: String,
        @JsonProperty("storePlatform") val storePlatform: String,
        @JsonProperty("treatmentOverrides") val treatmentOverrides: List<String>?,
        @JsonProperty("type") val type: String,
    )

    data class User(
        @JsonProperty("language") val language: String,
        @JsonProperty("languageCode") val languageCode: String,
        @JsonProperty("regionCode") val regionCode: String,
        @JsonProperty("token") val token: String,
        @JsonProperty("tokenType") val tokenType: String
    )
}

/**
 * @author Kevin Ludwig
 */
data class SessionStartResponse(
    @JsonProperty("result") val result: Result
) {
    data class Result(
        @JsonProperty("authorizationHeader") val authorizationHeader: String,
        @JsonProperty("validUntil") val validUntil: String,
        @JsonProperty("treatments") val treatments: List<String>,
        @JsonProperty("configurations") val configurations: Map<String, Configuration>
    ) {
        data class Configuration(
            @JsonProperty("id") val id: String,
            @JsonProperty("parameters") val parameters: Map<String, String>
        )
    }
}

suspend fun HttpClient.startSession(uri: String = "https://authorization-secondary.franchise.minecraft-services.net", request: SessionStartRequest) = post("$uri/api/v1.0/session/start") {
    contentType(ContentType.Application.Json)
    setBody(request)
}.body<SessionStartResponse>()
