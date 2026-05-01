package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.repository.EntityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GetSortedEntitiesUseCaseTest {

    private fun ts(ms: Long): Instant = Instant.fromEpochMilliseconds(ms)

    private fun light(id: String, lastUpdated: Long, lastChanged: Long = lastUpdated): HaEntity =
        HaEntity.Light(id, "on", emptyMap(), ts(lastChanged), ts(lastUpdated))

    private fun unknown(id: String, lastUpdated: Long): HaEntity =
        HaEntity.Unknown(id, "active", emptyMap(), ts(lastUpdated), ts(lastUpdated), domain = "vacuum")

    private fun fakeRepo(seed: List<HaEntity>, source: MutableStateFlow<List<HaEntity>>? = null): EntityRepository {
        val flow = source ?: MutableStateFlow(seed)
        return object : EntityRepository {
            override val entities: StateFlow<List<HaEntity>> = flow
            override fun observeEntity(entityId: String): Flow<HaEntity?> = flowOf(null)
            override suspend fun callService(
                domain: String, service: String, entityId: String?, serviceData: Map<String, Any?>,
            ): Result<Unit> = Result.success(Unit)
            override suspend fun refresh(): Result<Unit> = Result.success(Unit)
        }
    }

    @Test
    fun excludesUnknownEntities() = runTest {
        val items = listOf(
            light("light.a", 2_000L),
            unknown("vacuum.x", 5_000L),
            light("light.b", 1_000L),
        )
        val out = GetSortedEntitiesUseCase(fakeRepo(items)).invoke().first()
        assertEquals(listOf("light.a", "light.b"), out.map { it.entityId })
        assertTrue(out.none { it is HaEntity.Unknown })
    }

    @Test
    fun sortsByLastUpdatedDescending() = runTest {
        val items = listOf(
            light("light.old", lastUpdated = 1_000L),
            light("light.new", lastUpdated = 9_000L),
            light("light.mid", lastUpdated = 5_000L),
        )
        val out = GetSortedEntitiesUseCase(fakeRepo(items)).invoke().first()
        assertEquals(listOf("light.new", "light.mid", "light.old"), out.map { it.entityId })
    }

    @Test
    fun usesLastUpdatedNotLastChanged() = runTest {
        // Pinging entity: lastChanged is older than lastUpdated, should still bubble up.
        val pinging = light("light.pinging", lastUpdated = 9_000L, lastChanged = 1_000L)
        val recentlyChanged = light("light.flipped", lastUpdated = 5_000L, lastChanged = 5_000L)
        val out = GetSortedEntitiesUseCase(fakeRepo(listOf(recentlyChanged, pinging))).invoke().first()
        assertEquals(listOf("light.pinging", "light.flipped"), out.map { it.entityId })
    }

    @Test
    fun collapsesConsecutiveDuplicateMappedEmissions() = runTest {
        val source = MutableStateFlow(listOf(light("light.a", 1_000L)))
        val useCase = GetSortedEntitiesUseCase(fakeRepo(emptyList(), source))
        val seen = mutableListOf<List<HaEntity>>()
        val job = launch { useCase.invoke().toList(seen) }
        runCurrent()
        // Equal post-map content (re-ordered input that sorts identically) — distinctUntilChanged collapses.
        source.value = listOf(light("light.a", 1_000L))
        runCurrent()
        // Genuinely new content — should emit.
        source.value = listOf(light("light.a", 1_000L), light("light.b", 2_000L))
        runCurrent()
        job.cancel()
        assertEquals(2, seen.size, "expected two distinct emissions, got: $seen")
        assertEquals(1, seen[0].size)
        assertEquals(2, seen[1].size)
    }
}
