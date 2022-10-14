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

package com.valaphee.service.xbl

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author Kevin Ludwig
 */
class UserAuthResponse(
    @JsonProperty("AuthorizationToken") val authorizationToken: AuthorizationToken
) {
    class AuthorizationToken(
        @JsonProperty("DisplayClaims") val claim: Claim,
        @JsonProperty("Token") val token: String
    ) {
        class Claim(
            @JsonProperty("xui") val userInfo: List<UserInfo>
        ) {
            class UserInfo(
                @JsonProperty("gtg") val userName: String,
                @JsonProperty("xid") val xboxUserId: String,
                @JsonProperty("uhs") val userHash: String
            )
        }
    }
}
