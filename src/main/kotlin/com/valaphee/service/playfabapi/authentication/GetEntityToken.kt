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

package com.valaphee.service.playfabapi.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import com.valaphee.service.playfabapi.EntityToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * @author Kevin Ludwig
 */
data class GetEntityTokenRequest(
    @JsonProperty("Id") val id: String,
    @JsonProperty("Type") val type: String
)

/**
 * @author Kevin Ludwig
 */
data class GetEntityTokenResponse(
    @JsonProperty("data") val data: EntityToken,
)

suspend fun HttpClient.getEntityToken(titleId: String, request: GetEntityTokenRequest) = post("https://$titleId.playfabapi.com/Authentication/GetEntityToken?sdk=XPlatCppSdk-3.6.190304") {
    contentType(ContentType.Application.Json)
    setBody(request)
}.body<GetEntityTokenResponse>()
