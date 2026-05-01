package com.backpapp.hanative.domain.repository

import kotlinx.coroutines.flow.Flow

interface ActiveDashboardRepository {
    fun observeActiveDashboardId(): Flow<String?>
    suspend fun setActiveDashboardId(dashboardId: String?): Result<Unit>
}
