package com.backpapp.hanative.data.remote

import com.backpapp.hanative.domain.repository.HaWebSocketClient
import com.backpapp.hanative.platform.AppLifecycleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerManager(
    private val webSocketClient: HaWebSocketClient,
    private val authRepository: AuthenticationRepositoryImpl,
    private val lifecycleObserver: AppLifecycleObserver,
    private val scope: CoroutineScope,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var serverUrl: String? = null
    private var reconnectJob: Job? = null

    fun initialize(serverUrl: String) {
        this.serverUrl = serverUrl
        lifecycleObserver.onForeground { triggerReconnect() }
        webSocketClient.isConnected.let { connectedFlow ->
            scope.launch {
                connectedFlow.collect { connected ->
                    if (connected) {
                        reconnectJob?.cancel()
                        _connectionState.value = ConnectionState.Connected
                    } else if (_connectionState.value == ConnectionState.Connected) {
                        scheduleReconnect()
                    }
                }
            }
        }
    }

    fun connect() {
        scope.launch { attemptConnect() }
    }

    private fun triggerReconnect() {
        if (_connectionState.value != ConnectionState.Connected) {
            scope.launch { attemptConnect() }
        }
    }

    private suspend fun attemptConnect() {
        val url = serverUrl ?: return
        _connectionState.value = ConnectionState.Reconnecting
        runCatching {
            val token = authRepository.getValidToken()
            webSocketClient.connect(url, token)
        }.onFailure {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        _connectionState.value = ConnectionState.Reconnecting
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var backoffMs = 1_000L
            while (_connectionState.value != ConnectionState.Connected) {
                delay(backoffMs)
                attemptConnect()
                backoffMs = minOf(backoffMs * 2, 30_000L)
            }
        }
    }

    suspend fun disconnect() {
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState.Disconnected
        webSocketClient.disconnect()
    }

    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Reconnecting : ConnectionState()
        object Disconnected : ConnectionState()
    }
}
