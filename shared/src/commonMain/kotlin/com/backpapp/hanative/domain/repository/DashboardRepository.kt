package com.backpapp.hanative.domain.repository

import com.backpapp.hanative.domain.model.Dashboard
import com.backpapp.hanative.domain.model.DashboardCard
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun getDashboards(): Flow<List<Dashboard>>
    suspend fun saveDashboard(dashboard: Dashboard): Result<Unit>
    suspend fun deleteDashboard(dashboardId: String): Result<Unit>
    suspend fun addCard(card: DashboardCard): Result<Unit>
    suspend fun removeCard(cardId: String): Result<Unit>
    suspend fun reorderCards(dashboardId: String, cardIds: List<String>): Result<Unit>
}
