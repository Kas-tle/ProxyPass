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

package com.valaphee.service.minecraftservices.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.util.UUID

/**
 * @author Kevin Ludwig
 */
data class SessionStartRequest(
    @JsonProperty("PreviousSession") val previousSession: UUID,
    @JsonProperty("SessionContext") val sessionContext: String,
    @JsonProperty("SessionId") val sessionId: UUID
)

/**
 * @author Kevin Ludwig
 */
data class SessionStartResponse(
    val result: Result
) {
    data class Result(
        val id: UUID,
        val continuationToken: String,
        val messages: List<Any?>,
        val reportFrequency: Int
    )
}

suspend fun HttpClient.startSession(uri: String = "https://messaging.mktpl.minecraft-services.net", authorization: String, request: SessionStartRequest) = post("$uri/api/v1.0/session/start") {
    header(HttpHeaders.Authorization, authorization)
    contentType(ContentType.Application.Json)
    setBody(request)
}.body<SessionStartResponse>()
