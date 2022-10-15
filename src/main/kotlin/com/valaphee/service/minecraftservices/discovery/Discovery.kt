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

package com.valaphee.service.minecraftservices.discovery

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * @author Kevin Ludwig
 */
data class DiscoveryResponse(
    @JsonProperty("serviceEnvironments") val serviceEnvironments: Map<String, Map<String, ServiceEnvironment>>,
    @JsonProperty("supportedEnvironments") val supportedEnvironments: Map<String, List<String>>
) {
    data class ServiceEnvironment(
        @JsonProperty("serviceUri") val serviceUri: String,
        @JsonProperty("stunUri") val stunUri: String?,
        @JsonProperty("turnUri") val turnUri: String?,
        @JsonProperty("issuer") val issuer: String?,
        @JsonProperty("playfabTitleId") val playFabTitleId: String?,
        @JsonProperty("eduPlayFabTitleId") val eduPlayFabTitleId: String?
    )
}

suspend fun HttpClient.discover(version: String) = get("https://client.discovery.minecraft-services.net/api/v1.0/discovery/MinecraftPE/builds/${version}").body<DiscoveryResponse>()
