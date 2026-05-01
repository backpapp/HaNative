package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.repository.EntityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class GetSortedEntitiesUseCase(private val repository: EntityRepository) {
    operator fun invoke(): Flow<List<HaEntity>> =
        repository.entities
            .map { list ->
                list.asSequence()
                    .filter { it !is HaEntity.Unknown }
                    .sortedByDescending { it.lastUpdated }
                    .toList()
            }
            .distinctUntilChanged()
}
