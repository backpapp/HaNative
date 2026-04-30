package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.repository.EntityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveEntityStateUseCaseTest {

    private val ts = Instant.fromEpochMilliseconds(0L)
    private val light = HaEntity("light.living_room", "on", emptyMap(), ts, ts)
    private val switch = HaEntity("switch.outlet", "off", emptyMap(), ts, ts)

    private fun fakeRepo(entities: List<HaEntity> = emptyList()): EntityRepository =
        object : EntityRepository {
            override val entities: StateFlow<List<HaEntity>> = MutableStateFlow(entities)
            override fun observeEntity(entityId: String): Flow<HaEntity?> =
                flowOf(entities.firstOrNull { it.entityId == entityId })
            override suspend fun callService(
                domain: String, service: String, entityId: String?, serviceData: Map<String, Any?>
            ): Result<Unit> = Result.success(Unit)
            override suspend fun refresh(): Result<Unit> = Result.success(Unit)
        }

    @Test
    fun `invoke returns entity matching id`() = runTest {
        val useCase = ObserveEntityStateUseCase(fakeRepo(listOf(light, switch)))
        assertEquals(light, useCase("light.living_room").first())
    }

    @Test
    fun `invoke returns null for unknown id`() = runTest {
        val useCase = ObserveEntityStateUseCase(fakeRepo(listOf(light)))
        assertNull(useCase("sensor.unknown").first())
    }

    @Test
    fun `invoke returns null when repo empty`() = runTest {
        val useCase = ObserveEntityStateUseCase(fakeRepo())
        assertNull(useCase("light.x").first())
    }
}
