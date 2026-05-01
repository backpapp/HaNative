package com.backpapp.hanative.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.backpapp.hanative.HaNativeDatabase
import com.backpapp.hanative.domain.model.Dashboard
import com.backpapp.hanative.domain.model.DashboardCard
import com.backpapp.hanative.domain.repository.DashboardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DashboardRepositoryImpl(
    private val database: HaNativeDatabase,
) : DashboardRepository {

    private val dbMutex = Mutex()

    override fun getDashboards(): Flow<List<Dashboard>> = combine(
        database.dashboardQueries.selectAllDashboards()
            .asFlow()
            .mapToList(Dispatchers.Default),
        database.dashboardCardQueries.selectAllCards()
            .asFlow()
            .mapToList(Dispatchers.Default),
    ) { dashboards, cards ->
        val cardsByDashboard = cards.groupBy { it.dashboard_id }
        dashboards.map { d ->
            val sortedCards = (cardsByDashboard[d.id] ?: emptyList())
                .sortedBy { it.position }
                .map { c ->
                    DashboardCard(
                        id = c.id,
                        dashboardId = c.dashboard_id,
                        entityId = c.entity_id,
                        position = c.position.toInt(),
                        config = c.config,
                    )
                }
            Dashboard(
                id = d.id,
                name = d.name,
                position = d.position.toInt(),
                createdAt = d.created_at,
                cards = sortedCards,
            )
        }
    }

    override suspend fun saveDashboard(dashboard: Dashboard): Result<Unit> = runCatchingCancellable {
        require(dashboard.id.isNotBlank()) { "dashboard.id must not be blank" }
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardQueries.transaction {
                    val existing = database.dashboardQueries
                        .selectDashboardById(dashboard.id)
                        .executeAsOneOrNull()
                    if (existing == null) {
                        database.dashboardQueries.insertDashboard(
                            id = dashboard.id,
                            name = dashboard.name,
                            position = dashboard.position.toLong(),
                            created_at = dashboard.createdAt,
                        )
                    } else {
                        database.dashboardQueries.updateDashboard(
                            name = dashboard.name,
                            position = dashboard.position.toLong(),
                            id = dashboard.id,
                        )
                    }
                }
            }
        }
    }

    override suspend fun renameDashboard(dashboardId: String, name: String): Result<Unit> = runCatchingCancellable {
        require(dashboardId.isNotBlank()) { "dashboardId must not be blank" }
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "name must not be blank" }
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardQueries.updateDashboardName(name = trimmed, id = dashboardId)
            }
        }
    }

    override suspend fun deleteDashboard(dashboardId: String): Result<Unit> = runCatchingCancellable {
        require(dashboardId.isNotBlank()) { "dashboardId must not be blank" }
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardCardQueries.transaction {
                    database.dashboardCardQueries.deleteCardsByDashboard(dashboardId)
                    database.dashboardQueries.deleteDashboard(dashboardId)
                }
            }
        }
    }

    override suspend fun addCard(card: DashboardCard): Result<Unit> = runCatchingCancellable {
        require(card.id.isNotBlank()) { "card.id must not be blank" }
        require(card.dashboardId.isNotBlank()) { "card.dashboardId must not be blank" }
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardCardQueries.insertOrReplaceCard(
                    id = card.id,
                    dashboard_id = card.dashboardId,
                    entity_id = card.entityId,
                    position = card.position.toLong(),
                    config = card.config,
                )
            }
        }
    }

    override suspend fun removeCard(cardId: String): Result<Unit> = runCatchingCancellable {
        require(cardId.isNotBlank()) { "cardId must not be blank" }
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardCardQueries.transaction {
                    val card = database.dashboardCardQueries.selectCardById(cardId).executeAsOneOrNull()
                        ?: return@transaction
                    val dashboardId = card.dashboard_id
                    database.dashboardCardQueries.deleteCard(cardId)
                    val remaining = database.dashboardCardQueries
                        .selectCardsByDashboard(dashboardId)
                        .executeAsList()
                    remaining.forEachIndexed { index, c ->
                        if (c.position.toInt() != index) {
                            database.dashboardCardQueries.updateCardPositionInDashboard(
                                position = index.toLong(),
                                id = c.id,
                                dashboard_id = dashboardId,
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun reorderCards(
        dashboardId: String,
        cardIds: List<String>,
    ): Result<Unit> = runCatchingCancellable {
        require(dashboardId.isNotBlank()) { "dashboardId must not be blank" }
        require(cardIds.isNotEmpty()) { "cardIds must not be empty" }
        require(cardIds.toSet().size == cardIds.size) { "cardIds must not contain duplicates" }
        withContext(Dispatchers.Default) {
            dbMutex.withLock {
                database.dashboardCardQueries.transaction {
                    val existingIds = database.dashboardCardQueries
                        .selectCardsByDashboard(dashboardId)
                        .executeAsList()
                        .map { it.id }
                        .toSet()
                    require(existingIds == cardIds.toSet()) {
                        "cardIds must exactly match cards belonging to dashboardId=$dashboardId"
                    }
                    cardIds.forEachIndexed { index, cardId ->
                        database.dashboardCardQueries.updateCardPositionInDashboard(
                            position = index.toLong(),
                            id = cardId,
                            dashboard_id = dashboardId,
                        )
                    }
                }
            }
        }
    }

    private inline fun <R> runCatchingCancellable(block: () -> R): Result<R> = try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
