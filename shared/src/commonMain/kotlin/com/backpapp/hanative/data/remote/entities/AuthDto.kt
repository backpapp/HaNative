package com.backpapp.hanative.data.remote.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequiredDto(
    val type: String,
    @SerialName("ha_version") val haVersion: String,
)

@Serializable
data class AuthMessageDto(
    val type: String = "auth",
    @SerialName("access_token") val accessToken: String,
)

@Serializable
data class AuthResultDto(
    val type: String,
    val message: String? = null,
    @SerialName("ha_version") val haVersion: String? = null,
) {
    val isOk: Boolean get() = type == "auth_ok"
}
