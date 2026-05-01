package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.repository.ActiveDashboardRepository

class SetActiveDashboardIdUseCase(private val repo: ActiveDashboardRepository) {
    suspend operator fun invoke(dashboardId: String?): Result<Unit> =
        repo.setActiveDashboardId(dashboardId)
}
