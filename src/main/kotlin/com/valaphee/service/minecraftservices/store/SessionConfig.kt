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

package com.valaphee.service.minecraftservices.store

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import java.util.UUID

/**
 * @author Kevin Ludwig
 */
data class SessionConfigResponse(
    val result: Result
) {
    data class Result(
        val storeFilters: List<StoreFilter>,
        val storeSearch: StoreSearch,
        val upsellQueries: List<UpsellQuery>,
        val binaryUrls: Map<String, String>,
        val knownPages: Map<String, String>,
        val recentlyViewedSize: Int,
        val sidebars: Map<String, SideBar>,
        val feedbackCharacterLimit: Int,
        val platformSkus: List<Any?>
    ) {
        data class StoreFilter(
            val filterType: String,
            val toggles: List<Toggle>,
            val allowOnlyOneSelection: Boolean,
            val showInStore: Boolean,
            val showInInventory: Boolean
        ) {
            data class Toggle(
                val filterId: String?,
                val filterName: String?,
                val filterDefaultState: Boolean,
                val filterLow: Double?,
                val filterHigh: Double?
            )
        }

        data class Query(
            val productIds: List<UUID>,
            val queryContentTypes: List<String>,
            val andTags: List<String>,
            val orTags: List<String>,
            val notTags: List<String>,
            val rarityFilters: List<String>,
            val pieceTypeFilters: List<String>,
            val clientPageSort: ClientPageSort,
            val sortBy: String,
            val sortDirection: SortDirection,
            val searchString: String,
            val topCount: Int,
            val creatorIds: List<String>
        ) {
            enum class ClientPageSort {
                @JsonProperty("random") Random
            }

            enum class SortBy {
                @JsonProperty("relevance") Relevance,
                @JsonProperty("startDate") StartDate
            }

            enum class SortDirection {
                @JsonProperty("ASC") Ascending,
                @JsonProperty("DESC") Descending
            }
        }

        data class StoreSearch(
            val trendingQueries: List<TrendingQuery>
        ) {
            data class TrendingQuery(
                val storeRow: Map<String, Any?>,
                val queries: List<Query>,
                val extraUpsellQueryTags: List<String>,
                val headerComp: HeaderComp,
                val telemetryId: UUID
            ) {
                data class HeaderComp(
                    val headerText: String
                )
            }
        }

        data class UpsellQuery(
            val worldList: WorldList,
            val queries: List<Query>,
            val extraUpsellQueryTags: List<String>,
            val headerComp: HeaderComp,
            val linksTo: String,
            val linksToInfo: LinksToInfo
        ) {
            data class WorldList(
                val navButtonName: String
            )

            data class HeaderComp(
                val headerText: String
            )

            data class LinksToInfo(
                val linksTo: String,
                val linkType: String,
                val displayType: String
            )
        }
    }

    data class SideBar(
        val type: String,
        val navItems: List<NavItem>
    ) {
        data class NavItem(
            val title: String,
            val enableImageBilinear: Boolean,
            val iconPath: String,
            val behaviorType: String,
            val controlId: String,
            val linksTo: String,
            val linksToInfo: LinksToInfo,
            val telemetryId: UUID,
            val children: List<Any?>,
            val showTotalItems: Boolean
        ) {
            data class LinksToInfo(
                val linksTo: String,
                val linkType: String,
                val displayType: String
            )
        }
    }
}

suspend fun HttpClient.configSession(uri: String = "https://store.mktpl.minecraft-services.net", authorization: String) = get("https://store.mktpl.minecraft-services.net/api/v1.0/session/config") { header(HttpHeaders.Authorization, authorization) }.body<SessionConfigResponse>()
