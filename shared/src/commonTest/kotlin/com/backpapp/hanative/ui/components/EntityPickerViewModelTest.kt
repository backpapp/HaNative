package com.backpapp.hanative.ui.components

import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.repository.EntityRepository
import com.backpapp.hanative.domain.usecase.GetSortedEntitiesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EntityPickerViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ts(ms: Long): Instant = Instant.fromEpochMilliseconds(ms)

    private fun light(id: String, name: String? = null, lastUpdated: Long = 1_000L): HaEntity =
        HaEntity.Light(
            entityId = id,
            state = "on",
            attributes = if (name != null) mapOf("friendly_name" to name) else emptyMap(),
            lastChanged = ts(lastUpdated),
            lastUpdated = ts(lastUpdated),
        )

    private fun fakeRepo(source: MutableStateFlow<List<HaEntity>>): EntityRepository =
        object : EntityRepository {
            override val entities: StateFlow<List<HaEntity>> = source
            override fun observeEntity(entityId: String): Flow<HaEntity?> = flowOf(null)
            override suspend fun callService(
                domain: String, service: String, entityId: String?, serviceData: Map<String, Any?>,
            ): Result<Unit> = Result.success(Unit)
            override suspend fun refresh(): Result<Unit> = Result.success(Unit)
        }

    private fun makeVm(source: MutableStateFlow<List<HaEntity>>): EntityPickerViewModel =
        EntityPickerViewModel(GetSortedEntitiesUseCase(fakeRepo(source)))

    @Test
    fun initialStateIsLoading() = runTest(dispatcher) {
        val vm = makeVm(MutableStateFlow(emptyList()))
        assertEquals(EntityPickerUiState.Loading, vm.state.value)
    }

    @Test
    fun emptyEntitiesAfterDeadlineYieldsEmptyAll() = runTest(dispatcher) {
        val source = MutableStateFlow<List<HaEntity>>(emptyList())
        val vm = makeVm(source)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        advanceTimeBy(900L)
        runCurrent()
        assertEquals(EntityPickerUiState.EmptyAll, vm.state.value)
    }

    @Test
    fun loadedEntitiesMapToRowUi() = runTest(dispatcher) {
        val source = MutableStateFlow(
            listOf(
                light("light.living_room", "Living Room", lastUpdated = 2_000L),
                light("light.bedroom", "Bedroom", lastUpdated = 1_000L),
            )
        )
        val vm = makeVm(source)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        val state = assertIs<EntityPickerUiState.Loaded>(vm.state.value)
        assertEquals(2, state.rows.size)
        assertEquals("light.living_room", state.rows[0].entityId)
        assertEquals("light", state.rows[0].domain)
        assertEquals("Living Room", state.rows[0].name)
        assertEquals("On", state.rows[0].stateLabel)
    }

    @Test
    fun selectingDomainFiltersRows() = runTest(dispatcher) {
        val source = MutableStateFlow(
            listOf(
                light("light.a", "A", lastUpdated = 2_000L),
                HaEntity.Switch("switch.b", "off", emptyMap(), ts(1_000L), ts(1_000L)),
            )
        )
        val vm = makeVm(source)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        vm.onDomainSelected("switch")
        runCurrent()
        val state = assertIs<EntityPickerUiState.Loaded>(vm.state.value)
        assertEquals(1, state.rows.size)
        assertEquals("switch.b", state.rows[0].entityId)
    }

    @Test
    fun selectingDomainWithNoMatchesYieldsEmptyDomain() = runTest(dispatcher) {
        val source = MutableStateFlow(listOf(light("light.a", "A", lastUpdated = 2_000L)))
        val vm = makeVm(source)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        vm.onDomainSelected("climate")
        runCurrent()
        val state = assertIs<EntityPickerUiState.EmptyDomain>(vm.state.value)
        assertEquals("climate", state.domain)
    }

    @Test
    fun clearingDomainReturnsAllRows() = runTest(dispatcher) {
        val source = MutableStateFlow(
            listOf(
                light("light.a", "A", lastUpdated = 2_000L),
                HaEntity.Switch("switch.b", "off", emptyMap(), ts(1_000L), ts(1_000L)),
            )
        )
        val vm = makeVm(source)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        vm.onDomainSelected("light")
        runCurrent()
        assertIs<EntityPickerUiState.Loaded>(vm.state.value).also {
            assertEquals(1, it.rows.size)
        }
        vm.onDomainSelected(null)
        runCurrent()
        val cleared = assertIs<EntityPickerUiState.Loaded>(vm.state.value)
        assertEquals(2, cleared.rows.size)
    }

    @Test
    fun rowMappingExposesNoDomainTypes() = runTest(dispatcher) {
        val source = MutableStateFlow(listOf(light("light.a", "A", lastUpdated = 2_000L)))
        val vm = makeVm(source)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        val state = assertIs<EntityPickerUiState.Loaded>(vm.state.value)
        assertTrue(state.rows.all { it is EntityRowUi })
    }
}
