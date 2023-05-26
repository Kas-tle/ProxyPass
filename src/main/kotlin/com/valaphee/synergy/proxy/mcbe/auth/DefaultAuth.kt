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

package com.valaphee.synergy.proxy.mcbe.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valaphee.service.live.OAuth20Authenticator
import com.valaphee.service.xbl.DeviceAuthResponse
import com.valaphee.service.xbl.Signature
import com.valaphee.service.xbl.UserAuthResponse
import com.valaphee.service.xbl.toUnsignedByteArray
import com.valaphee.synergy.HttpClient
import com.valaphee.synergy.ObjectMapper
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.encodeBase64
import kotlinx.coroutines.runBlocking
import org.jose4j.jws.JsonWebSignature
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

/**
 * @author Kevin Ludwig
 */
class DefaultAuth(
    private val keyPair: KeyPair
) : Auth {
    lateinit var version: String
    private var _authJws: String? = null
    override val jws: String
        get() = _authJws ?: runBlocking {
            val authRootKey = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE8ELkixyLcwlZryUQcu1TvPOmI2B7vX83ndnWRUaXm74wFfa5f/lwQNTfrLVHa2PmenpGI6JhIMUJaWZrjmMj90NoKNFSNBuKdm8rYiXsfaz3K36x/1U26HpG0ZxK/V1V"
            val authJwsChain = HttpClient.post("https://multiplayer.minecraft.net/authentication") {
                header("User-Agent", "MCPE/Android")
                header("Client-Version", version)
                header("Authorization", authorization)
                contentType(ContentType.Application.Json)
                setBody(mapOf("identityPublicKey" to keyPair.public.encoded.encodeBase64()))
            }.body<Map<*, *>>()["chain"] as List<*>
            val authUserJws = JsonWebSignature().apply {
                setHeader("alg", "ES384")
                setHeader("x5u", keyPair.public.encoded.encodeBase64())
                val iat = LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond()
                payload = jacksonObjectMapper().writeValueAsString(
                    mapOf(
                        "certificateAuthority" to true,
                        "exp" to iat + 172800,
                        "identityPublicKey" to authRootKey,
                        "nbf" to iat - 60
                    )
                )
                key = keyPair.private
            }.compactSerialization
            ObjectMapper.writeValueAsString(mapOf("chain" to listOf(authUserJws) + authJwsChain)).also { _authJws = it }
        }
    
        fun getJwsAsList(): List<String> {
            val jwsAsMap = ObjectMapper.readValue(jws, Map::class.java)
            return jwsAsMap["chain"] as List<String>
        }

    companion object {
        private val authorization = runBlocking {
            OAuth20Authenticator().apply { run() }.accessToken?.let {
                val deviceKeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
                val devicePublicKeyW = (deviceKeyPair.public as ECPublicKey).w
                val httpClient = HttpClient.config { install(Signature) { keyPair = deviceKeyPair } }
                val deviceAuthResponse = httpClient.post("https://device.auth.xboxlive.com/device/authenticate") {
                    header("X-Xbl-Contract-Version", "1")
                    contentType(ContentType.Application.Json)
                    setBody(
                        mapOf(
                            "RelyingParty" to "http://auth.xboxlive.com",
                            "TokenType" to "JWT",
                            "Properties" to mapOf(
                                "AuthMethod" to "ProofOfPossession",
                                "Id" to "{${UUID.randomUUID()}}",
                                "DeviceType" to "Android",
                                "Version" to "10",
                                "ProofKey" to mapOf(
                                    "crv" to "P-256",
                                    "alg" to "ES256",
                                    "use" to "sig",
                                    "kty" to "EC",
                                    "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(devicePublicKeyW.affineX.abs().toUnsignedByteArray()),
                                    "y" to Base64.getUrlEncoder().withoutPadding().encodeToString(devicePublicKeyW.affineY.abs().toUnsignedByteArray())
                                )
                            )
                        )
                    )
                }.body<DeviceAuthResponse>()
                val userAuthResponse = httpClient.post("https://sisu.xboxlive.com/authorize") {
                    header("X-Xbl-Contract-Version", "1")
                    contentType(ContentType.Application.Json)
                    setBody(
                        mapOf(
                            "AccessToken" to "t=$it",
                            "AppId" to "0000000048183522",
                            "deviceToken" to deviceAuthResponse.token,
                            "Sandbox" to "RETAIL",
                            "UseModernGamertag" to true,
                            "SiteName" to "user.auth.xboxlive.com",
                            "RelyingParty" to "https://multiplayer.minecraft.net/",
                            "ProofKey" to mapOf(
                                "crv" to "P-256",
                                "alg" to "ES256",
                                "use" to "sig",
                                "kty" to "EC",
                                "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(devicePublicKeyW.affineX.abs().toUnsignedByteArray()),
                                "y" to Base64.getUrlEncoder().withoutPadding().encodeToString(devicePublicKeyW.affineY.abs().toUnsignedByteArray())
                            )
                        )
                    )
                }.body<UserAuthResponse>()
                "XBL3.0 x=${userAuthResponse.authorizationToken.claim.userInfo[0].userHash};${userAuthResponse.authorizationToken.token}"
            } ?: error("")
        }
    }
}