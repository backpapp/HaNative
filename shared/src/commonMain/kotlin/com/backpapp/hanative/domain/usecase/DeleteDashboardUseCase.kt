package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.repository.DashboardRepository

class DeleteDashboardUseCase(private val repository: DashboardRepository) {
    suspend operator fun invoke(dashboardId: String): Result<Unit> =
        repository.deleteDashboard(dashboardId)
}
