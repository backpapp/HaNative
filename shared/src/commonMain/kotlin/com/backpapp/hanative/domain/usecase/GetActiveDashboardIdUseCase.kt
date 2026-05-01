package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.repository.ActiveDashboardRepository
import kotlinx.coroutines.flow.Flow

class GetActiveDashboardIdUseCase(private val repo: ActiveDashboardRepository) {
    operator fun invoke(): Flow<String?> = repo.observeActiveDashboardId()
}
