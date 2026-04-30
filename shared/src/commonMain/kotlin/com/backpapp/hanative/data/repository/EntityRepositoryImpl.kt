package com.backpapp.hanative.data.repository

import com.backpapp.hanative.Entity_state
import com.backpapp.hanative.HaNativeDatabase
import com.backpapp.hanative.data.remote.KtorHaWebSocketClient
import com.backpapp.hanative.data.remote.MapAnySerializer
import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.model.HaEvent
import com.backpapp.hanative.domain.repository.EntityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class EntityRepositoryImpl(
    private val webSocketClient: KtorHaWebSocketClient,
    private val database: HaNativeDatabase,
    private val scope: CoroutineScope,
) : EntityRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun encodeAttributes(attrs: Map<String, Any?>): String =
        json.encodeToString(MapAnySerializer, attrs)

    private fun decodeAttributes(raw: String): Map<String, Any?> =
        json.decodeFromString(MapAnySerializer, raw)

    private val _entities = MutableStateFlow<List<HaEntity>>(
        database.entityStateQueries.selectAllEntityStates().executeAsList().map { it.toDomain() }
    )
    override val entities: StateFlow<List<HaEntity>> = _entities.asStateFlow()

    init {
        scope.launch { collectEvents() }
    }

    private suspend fun collectEvents() {
        webSocketClient.events().collect { event ->
            when (event) {
                is HaEvent.StateChanged -> upsertAndEmit(event)
                is HaEvent.ConnectionLost -> { /* cache remains visible; stale handling at ViewModel layer */ }
                is HaEvent.ConnectionError -> { /* same */ }
            }
        }
    }

    private fun upsertAndEmit(event: HaEvent.StateChanged) {
        database.entityStateQueries.upsertEntityState(
            entity_id = event.entityId,
            domain = event.entityId.substringBefore("."),
            state = event.state,
            attributes = encodeAttributes(event.attributes),
            last_updated = event.lastUpdated,
        )
        val updated = database.entityStateQueries.selectAllEntityStates()
            .executeAsList().map { it.toDomain() }
        _entities.value = updated
    }

    override fun observeEntity(entityId: String): Flow<HaEntity?> =
        entities.map { list -> list.firstOrNull { it.entityId == entityId } }

    override suspend fun callService(
        domain: String,
        service: String,
        entityId: String?,
        serviceData: Map<String, Any?>,
    ): Result<Unit> = webSocketClient.callService(domain, service, entityId, serviceData)

    suspend fun refreshFromWebSocket() {
        webSocketClient.getStates().onSuccess { rawStates ->
            database.entityStateQueries.transaction {
                rawStates.forEach { raw ->
                    database.entityStateQueries.upsertEntityState(
                        entity_id = raw.entityId,
                        domain = raw.entityId.substringBefore("."),
                        state = raw.state,
                        attributes = encodeAttributes(raw.attributes),
                        last_updated = raw.lastUpdated,
                    )
                }
            }
            val all = database.entityStateQueries.selectAllEntityStates()
                .executeAsList().map { it.toDomain() }
            _entities.value = all
        }
    }

    private fun Entity_state.toDomain(): HaEntity =
        HaEntity(
            entityId = entity_id,
            state = state,
            attributes = decodeAttributes(attributes),
            lastChanged = last_updated,
            lastUpdated = last_updated,
        )
}
