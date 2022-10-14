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

package com.valaphee.synergy.proxy.mcbe.service

import com.valaphee.synergy.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/**
 * @author Kevin Ludwig
 */
class OAuth20Authenticator {
    private var oauth20Token: OAuth20Token? = null

    val accessToken get() = oauth20Token?.accessToken

    suspend fun run() {
        val oauth20ConnectRequest = HttpClient.submitForm("https://login.live.com/oauth20_connect.srf", Parameters.build {
            append("client_id", "0000000048183522")
            append("scope", "service::user.auth.xboxlive.com::MBI_SSL")
            append("response_type", "device_code")
        })
        if (oauth20ConnectRequest.status == HttpStatusCode.OK) {
            val oauth20Connect = oauth20ConnectRequest.body<OAuth20Connect>()

            Desktop.getDesktop().browse(URI(oauth20Connect.verificationUri))
            val userCode = StringSelection(oauth20Connect.userCode)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(userCode, userCode)

            var i = 0
            while (i++ < oauth20Connect.expiresIn / oauth20Connect.interval) {
                val oauth20TokenRequest = HttpClient.submitForm("https://login.live.com/oauth20_token.srf", Parameters.build {
                    append("client_id", "0000000048183522")
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    append("device_code", oauth20Connect.deviceCode)
                })
                if (oauth20TokenRequest.status == HttpStatusCode.OK) {
                    oauth20Token = oauth20TokenRequest.body()
                    break
                }
                delay(oauth20Connect.interval * 1000L)
            }
        }
    }
}
