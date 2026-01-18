/*
 * Copyright 2026 dorkbox, llc
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

@file:OptIn(ExperimentalStdlibApi::class)

package dorkbox.gradlePublish.portal.impl.infrastructure

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(OctetByteArray.Companion::class)
class OctetByteArray(val value: ByteArray) {
    companion object : KSerializer<OctetByteArray> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OctetByteArray", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: OctetByteArray): Unit = encoder.encodeString(value.value.toHexString())
        override fun deserialize(decoder: Decoder): OctetByteArray = OctetByteArray(decoder.decodeString().hexToByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as OctetByteArray
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return "OctetByteArray(${value.toHexString()})"
    }
}
