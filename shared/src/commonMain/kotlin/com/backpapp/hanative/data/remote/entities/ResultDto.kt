package com.backpapp.hanative.data.remote.entities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ResultDto(
    val id: Int,
    val type: String,
    val success: Boolean,
    val result: JsonElement? = null,
    val error: ErrorDto? = null,
)

@Serializable
data class ErrorDto(
    val code: String,
    val message: String,
)
