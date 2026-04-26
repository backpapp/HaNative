package com.backpapp.hanative.data.remote.entities

import com.backpapp.hanative.data.remote.MapAnySerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntityStateDto(
    @SerialName("entity_id") val entityId: String,
    val state: String,
    @Serializable(with = MapAnySerializer::class)
    val attributes: Map<String, Any?> = emptyMap(),
    @SerialName("last_changed") val lastChanged: String,
    @SerialName("last_updated") val lastUpdated: String,
    val context: ContextDto? = null,
)

@Serializable
data class ContextDto(
    val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("user_id") val userId: String? = null,
)
