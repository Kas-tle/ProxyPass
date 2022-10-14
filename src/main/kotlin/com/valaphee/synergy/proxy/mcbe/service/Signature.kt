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

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.header
import io.ktor.http.content.OutputStreamContent
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey
import io.ktor.util.KtorDsl
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.toByteArray
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import java.math.BigInteger
import java.security.KeyPair
import java.util.Base64

/**
 * @author Kevin Ludwig
 */
class Signature(
    val keyPair: KeyPair,
) {
    class Config {
        lateinit var keyPair: KeyPair

        fun build() = Signature(keyPair)
    }

    @KtorDsl
    companion object Plugin : HttpClientPlugin<Config, Signature> {
        override val key = AttributeKey<Signature>("Signature")

        override fun prepare(block: Config.() -> Unit) = Config().apply(block).build()

        override fun install(plugin: Signature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) { payload ->
                if (payload is OutputStreamContent) {
                    val now = (System.currentTimeMillis() + 11644473600000L) * 10000L
                    val hash = Unpooled.buffer()
                    try {
                        hash.writeInt(0x01)
                        hash.writeByte(0x00)
                        hash.writeLong(now)
                        hash.writeByte(0x00)
                        hash.writeBytes(context.method.value.toByteArray())
                        hash.writeByte(0x00)
                        hash.writeBytes(context.url.encodedPath.toByteArray())
                        hash.writeByte(0x00)
                        context.headers["Authorization"]?.let { hash.writeBytes(it.toByteArray()) }
                        hash.writeByte(0x00)
                        val bodyChannel = ByteChannel()
                        payload.writeTo(bodyChannel)
                        hash.writeBytes(bodyChannel.readRemaining().readBytes())
                        bodyChannel.close()
                        hash.writeByte(0x00)
                        val signature = (ASN1Sequence.fromByteArray(java.security.Signature.getInstance("SHA256withECDSA").apply {
                            initSign(plugin.keyPair.private)
                            update(ByteBufUtil.getBytes(hash).also { hash.clear() })
                        }.sign()) as ASN1Sequence)
                        hash.writeInt(0x01)
                        hash.writeLong(now)
                        hash.writeBytes((signature.getObjectAt(0) as ASN1Integer).positiveValue.toUnsignedByteArray())
                        hash.writeBytes((signature.getObjectAt(1) as ASN1Integer).positiveValue.toUnsignedByteArray())
                        context.header("Signature", Base64.getEncoder().encodeToString(ByteBufUtil.getBytes(hash)))
                    } finally {
                        hash.release()
                    }
                }
                proceed()
            }
        }
    }
}

fun BigInteger.toUnsignedByteArray(): ByteArray {
    val array = toByteArray()
    if (array[0].toInt() == 0) {
        val newArray = ByteArray(array.size - 1)
        System.arraycopy(array, 1, newArray, 0, newArray.size)
        return newArray
    }
    return array
}
