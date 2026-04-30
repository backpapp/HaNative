package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.Dashboard
import com.backpapp.hanative.domain.repository.DashboardRepository

class SaveDashboardUseCase(private val repository: DashboardRepository) {
    suspend operator fun invoke(dashboard: Dashboard): Result<Unit> =
        repository.saveDashboard(dashboard)
}
