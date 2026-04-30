package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.repository.DashboardRepository

class RemoveCardUseCase(private val repository: DashboardRepository) {
    suspend operator fun invoke(cardId: String): Result<Unit> =
        repository.removeCard(cardId)
}
