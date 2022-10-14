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

package com.valaphee.service.playfabapi.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.valaphee.service.playfabapi.EntityToken

/**
 * @author Kevin Ludwig
 */
data class LoginWithXboxRequest(
    @JsonProperty("CreateAccount") val createAccount: Boolean,
    @JsonProperty("InfoRequestParameters") val infoRequestParameters: InfoRequestParameters,
    @JsonProperty("TitleId") val titleId: String,
    @JsonProperty("XboxToken") val xboxToken: String
) {
    data class InfoRequestParameters(
        @JsonProperty("PlayerProfile") val playerProfile: Boolean,
        @JsonProperty("UserAccountInfo") val userAccountInfo: Boolean
    )
}

/**
 * @author Kevin Ludwig
 */
data class LoginWithXboxResponse(
    @JsonProperty("data") val data: Data
) {
    data class Data(
        @JsonProperty("SessionTicket") val sessionTicket: String,
        @JsonProperty("PlayFabId") val playFabId: String,
        @JsonProperty("NewlyCreated") val newlyCreated: Boolean,
        @JsonProperty("EntityToken") val entityToken: EntityToken
    )
}
