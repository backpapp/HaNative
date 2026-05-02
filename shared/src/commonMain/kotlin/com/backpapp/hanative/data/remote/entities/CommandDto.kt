package com.backpapp.hanative.data.remote.entities

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SubscribeEventsCommandDto(
    val id: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val type: String = "subscribe_events",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) @SerialName("event_type") val eventType: String = "state_changed",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GetStatesCommandDto(
    val id: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val type: String = "get_states",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CallServiceCommandDto(
    val id: Int,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val type: String = "call_service",
    val domain: String,
    val service: String,
    @SerialName("service_data") val serviceData: JsonObject = JsonObject(emptyMap()),
    val target: TargetDto? = null,
)

@Serializable
data class TargetDto(
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("area_id") val areaId: String? = null,
)
