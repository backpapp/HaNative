package com.backpapp.hanative.data.remote

import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.model.HaEvent
import com.backpapp.hanative.domain.repository.EntityRepository
import com.backpapp.hanative.domain.repository.HaRawEntityState
import com.backpapp.hanative.domain.repository.HaWebSocketClient
import com.backpapp.hanative.platform.CredentialStore
import com.backpapp.hanative.platform.LifecycleForegrounder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ServerManagerTest {

    // AC12 — Internet-down-LAN-live (FR29): when LAN reaches HA but cloud URL is
    // null/unreachable, ServerManager must still report Connected. Story 4.8 spec
    // line 169 explicitly requested this verification test.
    @Test
    fun lanLiveWithNullCloudUrlReachesConnected() = runTest {
        // UnconfinedTestDispatcher runs continuations in-place — the isConnected
        // collector inside ServerManager.initialize gets the WS-flipped-true emission
        // synchronously, so connectionState lands on Connected before connect()
        // returns, avoiding the StandardTestDispatcher ordering race where
        // scheduleReconnect can fire before the collector sees the true value.
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val ws = FakeHaWebSocketClient()
        val sm = ServerManager(
            webSocketClient = ws,
            authRepository = AuthenticationRepositoryImpl(FakeCredentialStore("token-abc")),
            lifecycleObserver = NoOpForegrounder(),
            reconnectManager = HaReconnectManager(),
            entityRepository = FakeEntityRepository(),
            scope = scope,
        )
        try {
            sm.initialize(lanUrl = "http://192.168.1.10:8123", cloudUrl = null)
            sm.connect()
            advanceUntilIdle()
            assertEquals(ServerManager.ConnectionState.Connected, sm.connectionState.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun lanReachableEvenIfCloudIsUnreachableStillConnects() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val ws = FakeHaWebSocketClient()
        val sm = ServerManager(
            webSocketClient = ws,
            authRepository = AuthenticationRepositoryImpl(FakeCredentialStore("t")),
            lifecycleObserver = NoOpForegrounder(),
            reconnectManager = HaReconnectManager(),
            entityRepository = FakeEntityRepository(),
            scope = scope,
        )
        try {
            sm.initialize(lanUrl = "http://lan", cloudUrl = "https://cloud.example")
            sm.connect()
            advanceUntilIdle()
            assertEquals(ServerManager.ConnectionState.Connected, sm.connectionState.value)
            assertEquals(listOf("http://lan"), ws.connectsCalledWith)
        } finally {
            scope.cancel()
        }
    }
}

private class FakeHaWebSocketClient : HaWebSocketClient {
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastMessageEpochMs = MutableStateFlow<Long?>(null)
    override val lastMessageEpochMs: StateFlow<Long?> = _lastMessageEpochMs.asStateFlow()

    private val _events = MutableSharedFlow<HaEvent>(extraBufferCapacity = 16)

    val connectsCalledWith = mutableListOf<String>()

    override suspend fun connect(serverUrl: String, accessToken: String) {
        connectsCalledWith += serverUrl
        _isConnected.value = true
    }

    override fun events(): Flow<HaEvent> = _events.asSharedFlow()

    override suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        serviceData: Map<String, Any?>,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getStates(): Result<List<HaRawEntityState>> = Result.success(emptyList())

    override suspend fun disconnect() {
        _isConnected.value = false
    }
}

private class FakeCredentialStore(private val token: String?) : CredentialStore {
    override suspend fun saveToken(token: String) {}
    override suspend fun getToken(): String? = token
    override suspend fun clear() {}
}

private class FakeEntityRepository : EntityRepository {
    override val entities: StateFlow<List<HaEntity>> = MutableStateFlow(emptyList<HaEntity>()).asStateFlow()
    override fun observeEntity(entityId: String): Flow<HaEntity?> = MutableStateFlow<HaEntity?>(null).asStateFlow()
    override suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        serviceData: Map<String, Any?>,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun refresh(): Result<Unit> = Result.success(Unit)
}

private class NoOpForegrounder : LifecycleForegrounder {
    override fun onForeground(callback: () -> Unit) { /* no-op for tests */ }
}
