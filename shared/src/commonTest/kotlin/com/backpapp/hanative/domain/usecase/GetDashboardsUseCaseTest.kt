package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.Dashboard
import com.backpapp.hanative.domain.model.DashboardCard
import com.backpapp.hanative.domain.repository.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetDashboardsUseCaseTest {

    private val card = DashboardCard("c1", "d1", "light.living_room", 0, "{}")
    private val dashboard = Dashboard("d1", "Home", 0, 1000L, listOf(card))

    private fun fakeRepo(dashboards: List<Dashboard> = emptyList()): DashboardRepository =
        object : DashboardRepository {
            override fun getDashboards(): Flow<List<Dashboard>> = flowOf(dashboards)
            override suspend fun saveDashboard(dashboard: Dashboard): Result<Unit> = Result.success(Unit)
            override suspend fun deleteDashboard(dashboardId: String): Result<Unit> = Result.success(Unit)
            override suspend fun addCard(card: DashboardCard): Result<Unit> = Result.success(Unit)
            override suspend fun removeCard(cardId: String): Result<Unit> = Result.success(Unit)
            override suspend fun reorderCards(dashboardId: String, cardIds: List<String>): Result<Unit> = Result.success(Unit)
        }

    @Test
    fun `invoke returns empty list when no dashboards`() = runTest {
        val useCase = GetDashboardsUseCase(fakeRepo())
        assertEquals(emptyList(), useCase().first())
    }

    @Test
    fun `invoke returns dashboards with cards`() = runTest {
        val useCase = GetDashboardsUseCase(fakeRepo(listOf(dashboard)))
        val result = useCase().first()
        assertEquals(1, result.size)
        assertEquals(dashboard, result[0])
        assertEquals(1, result[0].cards.size)
        assertEquals(card, result[0].cards[0])
    }

    @Test
    fun `invoke returns all dashboards`() = runTest {
        val d2 = Dashboard("d2", "Away", 1, 2000L, emptyList())
        val useCase = GetDashboardsUseCase(fakeRepo(listOf(dashboard, d2)))
        val result = useCase().first()
        assertEquals(2, result.size)
        assertEquals("d1", result[0].id)
        assertEquals("d2", result[1].id)
    }
}
