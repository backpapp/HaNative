package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.repository.DashboardRepository

class RenameDashboardUseCase(private val repository: DashboardRepository) {
    suspend operator fun invoke(dashboardId: String, name: String): Result<Unit> =
        repository.renameDashboard(dashboardId, name)
}
