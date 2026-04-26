package com.backpapp.hanative.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
object MapAnySerializer : KSerializer<Map<String, Any?>> {

    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("MapAny", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Map<String, Any?>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("MapAnySerializer requires JsonEncoder")
        jsonEncoder.encodeJsonElement(value.toJsonObject())
    }

    override fun deserialize(decoder: Decoder): Map<String, Any?> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("MapAnySerializer requires JsonDecoder")
        return jsonDecoder.decodeJsonElement().jsonObject.toAnyMap()
    }

    private fun Map<String, Any?>.toJsonObject(): JsonObject = buildJsonObject {
        forEach { (key, value) -> put(key, value.toJsonElement()) }
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is List<*> -> JsonArray(map { it.toJsonElement() })
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            JsonObject((this as Map<String, Any?>).mapValues { (_, v) -> v.toJsonElement() })
        }
        else -> JsonPrimitive(toString())
    }

    private fun JsonObject.toAnyMap(): Map<String, Any?> =
        mapValues { (_, v) -> v.toAny() }

    private fun JsonElement.toAny(): Any? = when (this) {
        JsonNull -> null
        is JsonPrimitive -> when {
            isString -> content
            content == "true" -> true
            content == "false" -> false
            else -> content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
        }
        is JsonArray -> map { it.toAny() }
        is JsonObject -> toAnyMap()
    }
}
