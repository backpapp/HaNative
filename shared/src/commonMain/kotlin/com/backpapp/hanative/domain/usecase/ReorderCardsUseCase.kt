package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.repository.DashboardRepository

class ReorderCardsUseCase(private val repository: DashboardRepository) {
    suspend operator fun invoke(dashboardId: String, cardIds: List<String>): Result<Unit> =
        repository.reorderCards(dashboardId, cardIds)
}
