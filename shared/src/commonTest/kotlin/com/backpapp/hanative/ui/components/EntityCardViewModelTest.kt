package com.backpapp.hanative.ui.components

import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.repository.EntityRepository
import com.backpapp.hanative.domain.usecase.CallServiceUseCase
import com.backpapp.hanative.domain.usecase.ObserveEntityStateUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EntityCardViewModelTest {

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

    private val ts = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private class FakeRepo(
        private val source: MutableStateFlow<HaEntity?>,
        var callResult: Result<Unit> = Result.success(Unit),
    ) : EntityRepository {
        override val entities: StateFlow<List<HaEntity>> = MutableStateFlow(emptyList())
        var lastDomain: String? = null
        var lastService: String? = null
        var lastEntityId: String? = null
        var lastServiceData: Map<String, Any?> = emptyMap()
        var callCount = 0

        override fun observeEntity(entityId: String): Flow<HaEntity?> = source
        override suspend fun callService(
            domain: String,
            service: String,
            entityId: String?,
            serviceData: Map<String, Any?>,
        ): Result<Unit> {
            callCount++
            lastDomain = domain
            lastService = service
            lastEntityId = entityId
            lastServiceData = serviceData
            return callResult
        }
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)
    }

    private fun vm(repo: FakeRepo, entityId: String): EntityCardViewModel =
        EntityCardViewModel(
            entityId = entityId,
            observe = ObserveEntityStateUseCase(repo),
            call = CallServiceUseCase(repo),
        )

    @Test
    fun nullEntityYieldsReadOnlyUnknown() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(null))
        val v = vm(repo, "light.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        val state = assertIs<EntityCardUiState.ReadOnly>(v.state.value)
        assertEquals("Unknown", state.label)
    }

    @Test
    fun lightMapsToToggleStateWithIsOn() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Light("light.living_room", "on", mapOf("friendly_name" to "Living Room"), ts, ts)
        ))
        val v = vm(repo, "light.living_room")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        val state = assertIs<EntityCardUiState.Toggle>(v.state.value)
        assertTrue(state.isOn)
        assertEquals("Living Room", state.title)
        assertTrue(state.isInteractable)
    }

    @Test
    fun unavailableEntityIsNotInteractable() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Light("light.x", "unavailable", emptyMap(), ts, ts)
        ))
        val v = vm(repo, "light.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        val state = assertIs<EntityCardUiState.Toggle>(v.state.value)
        assertFalse(state.isInteractable)
    }

    @Test
    fun onToggleSetsOptimisticAndCallsService() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Light("light.x", "off", emptyMap(), ts, ts)
        ))
        val v = vm(repo, "light.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onToggle()
        runCurrent()
        val state = assertIs<EntityCardUiState.Toggle>(v.state.value)
        assertTrue(state.isOn, "optimistic on after toggle")
        assertEquals("homeassistant", repo.lastDomain)
        assertEquals("toggle", repo.lastService)
        assertEquals("light.x", repo.lastEntityId)
    }

    @Test
    fun onToggleOnUnavailableIsNoop() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Light("light.x", "unavailable", emptyMap(), ts, ts)
        ))
        val v = vm(repo, "light.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onToggle()
        runCurrent()
        assertEquals(0, repo.callCount)
    }

    @Test
    fun onToggleFailureClearsOptimisticAndIncrementsRejection() = runTest(dispatcher) {
        val repo = FakeRepo(
            MutableStateFlow(HaEntity.Light("light.x", "off", emptyMap(), ts, ts)),
            callResult = Result.failure(RuntimeException("boom")),
        )
        val v = vm(repo, "light.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onToggle()
        runCurrent()
        val state = assertIs<EntityCardUiState.Toggle>(v.state.value)
        assertFalse(state.isOn, "optimistic cleared on failure")
        assertEquals(1L, state.rejectionCounter)
    }

    @Test
    fun climateMapsToStepperWithFormattedTemp() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Climate(
                entityId = "climate.lounge",
                state = "heat",
                attributes = mapOf(
                    "friendly_name" to "Lounge",
                    "temperature" to 21.0,
                    "current_temperature" to 20.5,
                ),
                lastChanged = ts,
                lastUpdated = ts,
            )
        ))
        val v = vm(repo, "climate.lounge")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        val state = assertIs<EntityCardUiState.Stepper>(v.state.value)
        assertEquals("21.0°", state.formattedTemp)
        assertTrue(state.hasTarget)
        assertTrue(state.currentLabel.startsWith("Current"))
    }

    @Test
    fun onStepTempUpAdvancesByHalfDegree() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Climate(
                entityId = "climate.x",
                state = "heat",
                attributes = mapOf("temperature" to 21.0),
                lastChanged = ts,
                lastUpdated = ts,
            )
        ))
        val v = vm(repo, "climate.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onStepTemp(1)
        runCurrent()
        val state = assertIs<EntityCardUiState.Stepper>(v.state.value)
        assertEquals("21.5°", state.formattedTemp)
        assertEquals("climate", repo.lastDomain)
        assertEquals("set_temperature", repo.lastService)
        assertEquals(21.5, repo.lastServiceData["temperature"])
    }

    @Test
    fun onStepTempClampsAtMaxAndIncrementsRejection() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Climate(
                entityId = "climate.x",
                state = "heat",
                attributes = mapOf(
                    "temperature" to 30.0,
                    "max_temp" to 30.0,
                    "min_temp" to 10.0,
                ),
                lastChanged = ts,
                lastUpdated = ts,
            )
        ))
        val v = vm(repo, "climate.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onStepTemp(1)
        runCurrent()
        assertEquals(0, repo.callCount, "no service call when clamped to no-op")
        val state = assertIs<EntityCardUiState.Stepper>(v.state.value)
        assertEquals(1L, state.rejectionCounter)
    }

    @Test
    fun scriptMapsToTrigger() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Script("script.goodnight", "off", mapOf("friendly_name" to "Goodnight"), ts, ts)
        ))
        val v = vm(repo, "script.goodnight")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        val state = assertIs<EntityCardUiState.Trigger>(v.state.value)
        assertEquals("Goodnight", state.title)
    }

    @Test
    fun onTriggerCallsScriptTurnOnAndIncrementsTriggerCounter() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Script("script.goodnight", "off", emptyMap(), ts, ts)
        ))
        val v = vm(repo, "script.goodnight")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onTrigger()
        runCurrent()
        assertEquals("script", repo.lastDomain)
        assertEquals("turn_on", repo.lastService)
        val state = assertIs<EntityCardUiState.Trigger>(v.state.value)
        assertEquals(1L, state.triggerCounter)
    }

    @Test
    fun sceneTriggerUsesSceneDomain() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Scene("scene.evening", "scening", emptyMap(), ts, ts)
        ))
        val v = vm(repo, "scene.evening")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onTrigger()
        runCurrent()
        assertEquals("scene", repo.lastDomain)
        assertEquals("turn_on", repo.lastService)
    }

    @Test
    fun mediaMapsToMediaState() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.MediaPlayer(
                entityId = "media_player.x",
                state = "playing",
                attributes = mapOf("media_title" to "Beethoven"),
                lastChanged = ts,
                lastUpdated = ts,
            )
        ))
        val v = vm(repo, "media_player.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        val state = assertIs<EntityCardUiState.Media>(v.state.value)
        assertEquals("Beethoven", state.title)
        assertTrue(state.isPlaying)
        assertEquals("Playing", state.subtitle)
    }

    @Test
    fun onPlayPauseCallsMediaPlayer() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.MediaPlayer("media_player.x", "playing", emptyMap(), ts, ts)
        ))
        val v = vm(repo, "media_player.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onPlayPause()
        runCurrent()
        assertEquals("media_player", repo.lastDomain)
        assertEquals("media_play_pause", repo.lastService)
    }

    @Test
    fun sensorMapsToReadOnlyWithUnit() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Sensor(
                entityId = "sensor.temp",
                state = "21.4",
                attributes = mapOf("friendly_name" to "Temp", "unit_of_measurement" to "°C"),
                lastChanged = ts,
                lastUpdated = ts,
            )
        ))
        val v = vm(repo, "sensor.temp")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        val state = assertIs<EntityCardUiState.ReadOnly>(v.state.value)
        assertEquals("21.4 °C", state.label)
    }

    @Test
    fun unknownDomainMapsToUnknownState() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Unknown(
                entityId = "vacuum.upstairs",
                state = "active",
                attributes = emptyMap(),
                lastChanged = ts,
                lastUpdated = ts,
                domain = "vacuum",
            )
        ))
        val v = vm(repo, "vacuum.upstairs")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        val state = assertIs<EntityCardUiState.Unknown>(v.state.value)
        assertEquals("vacuum.upstairs", state.title)
    }

    @Test
    fun setStaleFlowsThroughToState() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Light("light.x", "on", emptyMap(), ts, ts)
        ))
        val v = vm(repo, "light.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.setStale(true)
        runCurrent()
        val state = assertIs<EntityCardUiState.Toggle>(v.state.value)
        assertTrue(state.isStale)
    }

    @Test
    fun rapidDoubleToggleIsDebouncedWhileOptimisticInFlight() = runTest(dispatcher) {
        val repo = FakeRepo(MutableStateFlow(
            HaEntity.Light("light.x", "off", emptyMap(), ts, ts)
        ))
        val v = vm(repo, "light.x")
        backgroundScope.launch { v.state.collect {} }
        runCurrent()
        v.onToggle()
        v.onToggle()
        runCurrent()
        assertEquals(1, repo.callCount, "second press while optimistic in flight is debounced")
    }
}
