package com.backpapp.hanative.domain.repository

import com.backpapp.hanative.domain.model.HaEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

interface HaWebSocketClient {
    val isConnected: StateFlow<Boolean>

    suspend fun connect(serverUrl: String, accessToken: String)

    fun events(): Flow<HaEvent>

    suspend fun callService(
        domain: String,
        service: String,
        entityId: String? = null,
        serviceData: Map<String, Any?> = emptyMap(),
    ): Result<Unit>

    suspend fun getStates(): Result<List<HaRawEntityState>>

    suspend fun disconnect()
}

data class HaRawEntityState(
    val entityId: String,
    val state: String,
    val attributes: Map<String, Any?>,
    val lastChanged: Instant,
    val lastUpdated: Instant,
)
