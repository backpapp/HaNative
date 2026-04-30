package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.repository.EntityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CallServiceUseCaseTest {

    private fun fakeRepo(succeeds: Boolean): EntityRepository =
        object : EntityRepository {
            override val entities: StateFlow<List<HaEntity>> = MutableStateFlow(emptyList())
            override fun observeEntity(entityId: String): Flow<HaEntity?> = flowOf(null)
            override suspend fun callService(
                domain: String, service: String, entityId: String?, serviceData: Map<String, Any?>
            ): Result<Unit> = if (succeeds) Result.success(Unit) else Result.failure(RuntimeException("err"))
        }

    @Test
    fun `invoke returns success when repository succeeds`() = runTest {
        val useCase = CallServiceUseCase(fakeRepo(true))
        assertTrue(useCase("light", "turn_on", "light.x").isSuccess)
    }

    @Test
    fun `invoke returns failure when repository fails`() = runTest {
        val useCase = CallServiceUseCase(fakeRepo(false))
        assertFalse(useCase("light", "turn_on", "light.x").isSuccess)
    }

    @Test
    fun `invoke passes all parameters to repository`() = runTest {
        var capturedDomain = ""
        var capturedService = ""
        var capturedEntityId: String? = null
        var capturedData: Map<String, Any?> = emptyMap()

        val repo = object : EntityRepository {
            override val entities: StateFlow<List<HaEntity>> = MutableStateFlow(emptyList())
            override fun observeEntity(entityId: String): Flow<HaEntity?> = flowOf(null)
            override suspend fun callService(
                domain: String, service: String, entityId: String?, serviceData: Map<String, Any?>
            ): Result<Unit> {
                capturedDomain = domain
                capturedService = service
                capturedEntityId = entityId
                capturedData = serviceData
                return Result.success(Unit)
            }
        }

        val useCase = CallServiceUseCase(repo)
        val data = mapOf("brightness" to 255)
        useCase("light", "turn_on", "light.living_room", data)

        assert(capturedDomain == "light")
        assert(capturedService == "turn_on")
        assert(capturedEntityId == "light.living_room")
        assert(capturedData == data)
    }
}
