package com.backpapp.hanative.data.remote.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class EventResponseDto(
    val id: Int,
    val type: String,
    val event: EventDto,
)

@Serializable
data class EventDto(
    @SerialName("event_type") val eventType: String,
    @SerialName("time_fired") val timeFired: String,
    val origin: String,
    val data: JsonObject,
    val context: ContextDto? = null,
)

@Serializable
data class StateChangedDataDto(
    @SerialName("entity_id") val entityId: String,
    @SerialName("old_state") val oldState: EntityStateDto? = null,
    @SerialName("new_state") val newState: EntityStateDto? = null,
)
