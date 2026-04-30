package com.backpapp.hanative.data.remote

import kotlin.concurrent.Volatile
import com.backpapp.hanative.domain.repository.EntityRepository
import com.backpapp.hanative.domain.repository.HaWebSocketClient
import com.backpapp.hanative.platform.AppLifecycleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val MAX_RECONNECT_ATTEMPTS = 10
private const val CONNECT_TIMEOUT_MS = 10_000L

class ServerManager(
    private val webSocketClient: HaWebSocketClient,
    private val authRepository: AuthenticationRepositoryImpl,
    private val lifecycleObserver: AppLifecycleObserver,
    private val reconnectManager: HaReconnectManager,
    private val entityRepository: EntityRepository,
    private val scope: CoroutineScope,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var lanUrl: String? = null
    private var cloudUrl: String? = null
    private var reconnectJob: Job? = null
    @Volatile private var attemptCount = 0

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
                    attemptCount = 0
                    _connectionState.value = ConnectionState.Connected
                    scope.launch { entityRepository.refresh() }
                } else if (_connectionState.value == ConnectionState.Connected) {
                    scheduleReconnect()
                }
            }
        }
    }

    fun connect() {
        scope.launch {
            attemptConnect()
            if (_connectionState.value == ConnectionState.Reconnecting) {
                scheduleReconnect()
            }
        }
    }

    private fun triggerReconnect() {
        if (_connectionState.value != ConnectionState.Connected) {
            scheduleReconnect()
        }
    }

    private suspend fun attemptConnect() {
        val lan = lanUrl ?: return
        _connectionState.value = ConnectionState.Reconnecting
        val token = runCatching { authRepository.getValidToken() }.getOrNull() ?: run {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        val lanOk = runCatching {
            withTimeout(CONNECT_TIMEOUT_MS) { webSocketClient.connect(lan, token) }
        }.isSuccess
        if (lanOk) {
            reconnectManager.reset()
            return
        }

        val cloud = cloudUrl
        if (cloud != null) {
            val cloudOk = runCatching {
                withTimeout(CONNECT_TIMEOUT_MS) { webSocketClient.connect(cloud, token) }
            }.isSuccess
            if (cloudOk) {
                reconnectManager.reset()
                return
            }
        }
        // Both failed — caller handles retry via scheduleReconnect
    }

    private fun scheduleReconnect() {
        _connectionState.value = ConnectionState.Reconnecting
        attemptCount = 0
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (_connectionState.value == ConnectionState.Reconnecting) {
                if (attemptCount >= MAX_RECONNECT_ATTEMPTS) {
                    _connectionState.value = ConnectionState.Failed
                    return@launch
                }
                attemptCount++
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
        object Failed : ConnectionState()
    }
}
