package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.Dashboard
import com.backpapp.hanative.domain.model.DashboardCard
import com.backpapp.hanative.domain.repository.DashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DashboardUseCaseTest {

    private val testDashboard = Dashboard("d1", "Home", 0, 1000L, emptyList())
    private val testCard = DashboardCard("c1", "d1", "light.living_room", 0, "{}")

    private val fakeRepo: DashboardRepository = object : DashboardRepository {
        override fun getDashboards(): Flow<List<Dashboard>> = flowOf(emptyList())
        override suspend fun saveDashboard(dashboard: Dashboard): Result<Unit> = Result.success(Unit)
        override suspend fun renameDashboard(dashboardId: String, name: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteDashboard(dashboardId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addCard(card: DashboardCard): Result<Unit> = Result.success(Unit)
        override suspend fun removeCard(cardId: String): Result<Unit> = Result.success(Unit)
        override suspend fun reorderCards(dashboardId: String, cardIds: List<String>): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun `SaveDashboardUseCase delegates to repository and returns success`() = runTest {
        val result = SaveDashboardUseCase(fakeRepo)(testDashboard)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `DeleteDashboardUseCase delegates to repository and returns success`() = runTest {
        val result = DeleteDashboardUseCase(fakeRepo)("d1")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `RenameDashboardUseCase delegates to repository and returns success`() = runTest {
        val result = RenameDashboardUseCase(fakeRepo)("d1", "Office")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `AddCardUseCase delegates to repository and returns success`() = runTest {
        val result = AddCardUseCase(fakeRepo)(testCard)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `RemoveCardUseCase delegates to repository and returns success`() = runTest {
        val result = RemoveCardUseCase(fakeRepo)("c1")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ReorderCardsUseCase delegates to repository and returns success`() = runTest {
        val result = ReorderCardsUseCase(fakeRepo)("d1", listOf("c1", "c2", "c3"))
        assertTrue(result.isSuccess)
    }
}
