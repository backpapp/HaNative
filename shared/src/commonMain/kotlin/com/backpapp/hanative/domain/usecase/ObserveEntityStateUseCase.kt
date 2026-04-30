package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.repository.EntityRepository
import kotlinx.coroutines.flow.Flow

class ObserveEntityStateUseCase(private val repository: EntityRepository) {
    operator fun invoke(entityId: String): Flow<HaEntity?> =
        repository.observeEntity(entityId)
}
