package com.backpapp.hanative.data.remote

import com.backpapp.hanative.data.remote.entities.AuthMessageDto
import com.backpapp.hanative.data.remote.entities.CallServiceCommandDto
import com.backpapp.hanative.data.remote.entities.EntityStateDto
import com.backpapp.hanative.data.remote.entities.GetStatesCommandDto
import com.backpapp.hanative.data.remote.entities.ResultDto
import com.backpapp.hanative.data.remote.entities.StateChangedDataDto
import com.backpapp.hanative.data.remote.entities.SubscribeEventsCommandDto
import com.backpapp.hanative.data.remote.entities.TargetDto
import com.backpapp.hanative.domain.model.HaEvent
import com.backpapp.hanative.domain.repository.HaRawEntityState
import com.backpapp.hanative.domain.repository.HaWebSocketClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KtorHaWebSocketClient(
    private val httpClient: HttpClient,
) : HaWebSocketClient {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _events = MutableSharedFlow<HaEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    private val resultsMutex = Mutex()
    private val pendingResults = mutableMapOf<Int, Channel<ResultDto>>()

    private val counterMutex = Mutex()
    private var counter = 1
    private suspend fun nextId(): Int = counterMutex.withLock { counter++ }

    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun connect(serverUrl: String, accessToken: String) {
        session?.close()

        val wsUrl = when {
            serverUrl.startsWith("https://") -> "wss://" + serverUrl.removePrefix("https://")
            serverUrl.startsWith("http://") -> "ws://" + serverUrl.removePrefix("http://")
            else -> serverUrl
        }.trimEnd('/') + "/api/websocket"

        val subscribeId = nextId()
        val newSession = httpClient.webSocketSession(wsUrl)
        session = newSession

        newSession.launch {
            for (frame in newSession.incoming) {
                if (frame is Frame.Text) handleFrame(frame.readText(), newSession, accessToken, subscribeId)
            }
            _isConnected.value = false
            _events.emit(HaEvent.ConnectionLost)
            cancelPendingResults()
        }
    }

    private suspend fun handleFrame(
        text: String,
        session: DefaultClientWebSocketSession,
        accessToken: String,
        subscribeId: Int,
    ) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (obj["type"]?.jsonPrimitive?.content) {
            "auth_required" -> {
                val authMsg = json.encodeToString(
                    AuthMessageDto.serializer(),
                    AuthMessageDto(accessToken = accessToken),
                )
                session.send(Frame.Text(authMsg))
            }
            "auth_ok" -> {
                _isConnected.value = true
                val subscribeCmd = json.encodeToString(
                    SubscribeEventsCommandDto.serializer(),
                    SubscribeEventsCommandDto(id = subscribeId),
                )
                session.send(Frame.Text(subscribeCmd))
            }
            "auth_invalid" -> {
                _events.emit(HaEvent.ConnectionError(IllegalStateException("Auth invalid")))
                session.close()
            }
            "event" -> handleEvent(obj)
            "result" -> handleResult(obj)
        }
    }

    private suspend fun handleEvent(obj: JsonObject) {
        val event = obj["event"]?.jsonObject ?: return
        if (event["event_type"]?.jsonPrimitive?.content != "state_changed") return
        runCatching {
            val data = json.decodeFromJsonElement<StateChangedDataDto>(event["data"]!!)
            val newState = data.newState ?: return
            _events.emit(
                HaEvent.StateChanged(
                    entityId = newState.entityId,
                    state = newState.state,
                    attributes = newState.attributes,
                    lastChanged = Instant.parse(newState.lastChanged),
                    lastUpdated = Instant.parse(newState.lastUpdated),
                )
            )
        }
    }

    private suspend fun handleResult(obj: JsonObject) {
        val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return
        val result = runCatching { json.decodeFromJsonElement<ResultDto>(obj) }.getOrNull() ?: return
        resultsMutex.withLock {
            pendingResults[id]?.trySend(result).also { sendResult ->
                if (sendResult?.isFailure == true) {
                    println("WARN: result dropped for id=$id (channel full or closed)")
                }
            }
        }
    }

    override fun events(): Flow<HaEvent> = _events.asSharedFlow()

    override suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        serviceData: Map<String, Any?>,
    ): Result<Unit> {
        val id = nextId()
        val resultChannel = Channel<ResultDto>(1)
        resultsMutex.withLock { pendingResults[id] = resultChannel }

        val target = entityId?.let { TargetDto(entityId = it) }
        val serviceDataJson = json.encodeToJsonElement(MapAnySerializer, serviceData).jsonObject
        val cmd = json.encodeToString(
            CallServiceCommandDto.serializer(),
            CallServiceCommandDto(
                id = id,
                domain = domain,
                service = service,
                serviceData = serviceDataJson,
                target = target,
            ),
        )
        session?.send(Frame.Text(cmd)) ?: run {
            resultsMutex.withLock { pendingResults.remove(id) }
            return Result.failure(IllegalStateException("Not connected"))
        }

        return try {
            val result = withTimeout(30_000) { resultChannel.receive() }
            resultsMutex.withLock { pendingResults.remove(id) }
            if (result.success) Result.success(Unit)
            else Result.failure(IllegalStateException(result.error?.message ?: "Service call failed"))
        } catch (e: Exception) {
            resultsMutex.withLock { pendingResults.remove(id) }
            Result.failure(e)
        }
    }

    override suspend fun getStates(): Result<List<HaRawEntityState>> {
        val id = nextId()
        val resultChannel = Channel<ResultDto>(1)
        resultsMutex.withLock { pendingResults[id] = resultChannel }

        val cmd = json.encodeToString(GetStatesCommandDto.serializer(), GetStatesCommandDto(id = id))
        session?.send(Frame.Text(cmd)) ?: run {
            resultsMutex.withLock { pendingResults.remove(id) }
            return Result.failure(IllegalStateException("Not connected"))
        }

        return try {
            val result = withTimeout(30_000) { resultChannel.receive() }
            resultsMutex.withLock { pendingResults.remove(id) }
            if (!result.success) return Result.failure(IllegalStateException("get_states failed"))

            val states = (result.result as? JsonArray)?.mapNotNull { el ->
                runCatching { json.decodeFromJsonElement<EntityStateDto>(el) }.getOrNull()
            }?.map { dto ->
                HaRawEntityState(
                    entityId = dto.entityId,
                    state = dto.state,
                    attributes = dto.attributes,
                    lastChanged = Instant.parse(dto.lastChanged),
                    lastUpdated = Instant.parse(dto.lastUpdated),
                )
            } ?: emptyList()

            Result.success(states)
        } catch (e: Exception) {
            resultsMutex.withLock { pendingResults.remove(id) }
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        session?.close()
        session = null
        _isConnected.value = false
        cancelPendingResults()
    }

    private suspend fun cancelPendingResults() {
        resultsMutex.withLock {
            pendingResults.values.forEach { it.close() }
            pendingResults.clear()
        }
    }
}
