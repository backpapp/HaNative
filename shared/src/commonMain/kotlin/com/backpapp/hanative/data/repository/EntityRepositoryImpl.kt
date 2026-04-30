package com.backpapp.hanative.data.repository

import com.backpapp.hanative.Entity_state
import com.backpapp.hanative.HaNativeDatabase
import com.backpapp.hanative.data.remote.KtorHaWebSocketClient
import com.backpapp.hanative.data.remote.MapAnySerializer
import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.model.HaEvent
import com.backpapp.hanative.domain.repository.EntityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class EntityRepositoryImpl(
    private val webSocketClient: KtorHaWebSocketClient,
    private val database: HaNativeDatabase,
    private val scope: CoroutineScope,
) : EntityRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val dbMutex = Mutex()

    private fun encodeAttributes(attrs: Map<String, Any?>): String =
        json.encodeToString(MapAnySerializer, attrs)

    private fun decodeAttributes(raw: String): Map<String, Any?> =
        runCatching { json.decodeFromString(MapAnySerializer, raw) }.getOrDefault(emptyMap())

    private val _entities = MutableStateFlow<List<HaEntity>>(emptyList())
    override val entities: StateFlow<List<HaEntity>> = _entities.asStateFlow()

    init {
        scope.launch {
            val cached = withContext(Dispatchers.Default) {
                runCatching {
                    database.entityStateQueries.selectAllEntityStates().executeAsList().map { it.toDomain() }
                }.getOrDefault(emptyList())
            }
            _entities.value = cached
            collectEvents()
        }
    }

    private suspend fun collectEvents() {
        webSocketClient.events()
            .catch { /* flow-level error: collection ends; init coroutine exits normally */ }
            .collect { event ->
                runCatching {
                    when (event) {
                        is HaEvent.StateChanged -> upsertAndEmit(event)
                        is HaEvent.ConnectionLost -> { /* cache stays visible; ServerManager triggers refresh on reconnect */ }
                        is HaEvent.ConnectionError -> { /* same */ }
                    }
                }
            }
    }

    private suspend fun upsertAndEmit(event: HaEvent.StateChanged) {
        dbMutex.withLock {
            withContext(Dispatchers.Default) {
                database.entityStateQueries.upsertEntityState(
                    entity_id = event.entityId,
                    domain = event.entityId.substringBefore("."),
                    state = event.state,
                    attributes = encodeAttributes(event.attributes),
                    last_changed = event.lastChanged,
                    last_updated = event.lastUpdated,
                )
                val updated = database.entityStateQueries.selectAllEntityStates()
                    .executeAsList().map { it.toDomain() }
                _entities.value = updated
            }
        }
    }

    override fun observeEntity(entityId: String): Flow<HaEntity?> =
        entities.map { list -> list.firstOrNull { it.entityId == entityId } }
            .distinctUntilChanged()

    override suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        serviceData: Map<String, Any?>,
    ): Result<Unit> = webSocketClient.callService(domain, service, entityId, serviceData)

    override suspend fun refresh(): Result<Unit> {
        val result = webSocketClient.getStates()
        result.onSuccess { rawStates ->
            dbMutex.withLock {
                withContext(Dispatchers.Default) {
                    database.entityStateQueries.transaction {
                        rawStates.forEach { raw ->
                            database.entityStateQueries.upsertEntityState(
                                entity_id = raw.entityId,
                                domain = raw.entityId.substringBefore("."),
                                state = raw.state,
                                attributes = encodeAttributes(raw.attributes),
                                last_changed = raw.lastChanged,
                                last_updated = raw.lastUpdated,
                            )
                        }
                    }
                    val all = database.entityStateQueries.selectAllEntityStates()
                        .executeAsList().map { it.toDomain() }
                    _entities.value = all
                }
            }
        }
        return result.map { }
    }

    private fun Entity_state.toDomain(): HaEntity =
        HaEntity(
            entityId = entity_id,
            state = state,
            attributes = decodeAttributes(attributes),
            lastChanged = last_changed,
            lastUpdated = last_updated,
        )
}
