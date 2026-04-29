package com.backpapp.hanative.data.remote

import com.backpapp.hanative.domain.repository.HaWebSocketClient
import com.backpapp.hanative.platform.AppLifecycleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServerManager(
    private val webSocketClient: HaWebSocketClient,
    private val authRepository: AuthenticationRepositoryImpl,
    private val lifecycleObserver: AppLifecycleObserver,
    private val reconnectManager: HaReconnectManager,
    private val scope: CoroutineScope,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var lanUrl: String? = null
    private var cloudUrl: String? = null
    private var reconnectJob: Job? = null

    fun initialize(lanUrl: String, cloudUrl: String? = null) {
        require(cloudUrl == null || cloudUrl.startsWith("https://")) {
            "Nabu Casa cloud URL must use HTTPS (NFR7): $cloudUrl"
        }
        this.lanUrl = lanUrl
        this.cloudUrl = cloudUrl
        lifecycleObserver.onForeground { triggerReconnect() }
        scope.launch {
            webSocketClient.isConnected.collect { connected ->
                if (connected) {
                    reconnectJob?.cancel()
                    reconnectManager.reset()
                    _connectionState.value = ConnectionState.Connected
                } else if (_connectionState.value == ConnectionState.Connected) {
                    scheduleReconnect()
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
        val lan = lanUrl ?: return
        _connectionState.value = ConnectionState.Reconnecting
        val token = runCatching { authRepository.getValidToken() }.getOrNull() ?: return

        val lanOk = runCatching { webSocketClient.connect(lan, token) }.isSuccess
        if (lanOk) {
            reconnectManager.reset()
            return
        }

        val cloud = cloudUrl
        if (cloud != null) {
            val cloudOk = runCatching { webSocketClient.connect(cloud, token) }.isSuccess
            if (cloudOk) {
                reconnectManager.reset()
                return
            }
        }

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        _connectionState.value = ConnectionState.Reconnecting
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (_connectionState.value != ConnectionState.Connected) {
                reconnectManager.waitThenAttempt { attemptConnect() }
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
