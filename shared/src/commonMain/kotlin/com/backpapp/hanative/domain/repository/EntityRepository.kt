package com.backpapp.hanative.domain.repository

import com.backpapp.hanative.domain.model.HaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface EntityRepository {
    val entities: StateFlow<List<HaEntity>>

    fun observeEntity(entityId: String): Flow<HaEntity?>

    suspend fun callService(
        domain: String,
        service: String,
        entityId: String? = null,
        serviceData: Map<String, Any?> = emptyMap(),
    ): Result<Unit>
}
