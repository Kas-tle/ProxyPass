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

package com.valaphee.service.live

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * @author Kevin Ludwig
 */
data class OAuth20Error(
    @JsonProperty("error") val error: String,
    @JsonProperty("error_description") val errorDescription: String,
    @JsonProperty("correlation_id") val correlationId: UUID
)