package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.DashboardCard
import com.backpapp.hanative.domain.repository.DashboardRepository

class AddCardUseCase(private val repository: DashboardRepository) {
    suspend operator fun invoke(card: DashboardCard): Result<Unit> =
        repository.addCard(card)
}
