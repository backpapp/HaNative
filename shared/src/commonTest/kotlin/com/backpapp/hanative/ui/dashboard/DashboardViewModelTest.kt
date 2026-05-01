package com.backpapp.hanative.ui.dashboard

import com.backpapp.hanative.data.remote.ServerManager.ConnectionState
import com.backpapp.hanative.domain.model.Dashboard
import com.backpapp.hanative.domain.model.DashboardCard
import com.backpapp.hanative.domain.repository.ActiveDashboardRepository
import com.backpapp.hanative.domain.repository.DashboardRepository
import com.backpapp.hanative.domain.usecase.AddCardUseCase
import com.backpapp.hanative.domain.usecase.DeleteDashboardUseCase
import com.backpapp.hanative.domain.usecase.GetActiveDashboardIdUseCase
import com.backpapp.hanative.domain.usecase.GetDashboardsUseCase
import com.backpapp.hanative.domain.usecase.RemoveCardUseCase
import com.backpapp.hanative.domain.usecase.RenameDashboardUseCase
import com.backpapp.hanative.domain.usecase.ReorderCardsUseCase
import com.backpapp.hanative.domain.usecase.SaveDashboardUseCase
import com.backpapp.hanative.domain.usecase.SetActiveDashboardIdUseCase
import com.backpapp.hanative.domain.util.IdGenerator
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun emitsEmptyForEmptyRepo() = runTest {
        val repo = FakeDashboardRepository()
        val vm = buildVm(repo)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        assertIs<DashboardUiState.Empty>(vm.state.value)
        assertEquals(false, (vm.state.value as DashboardUiState.Empty).pickerVisible)
        sub.cancel()
    }

    @Test
    fun singleDashboardWithEmptyCardsRendersEmptyNotSuccess() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = emptyList())))
        }
        val vm = buildVm(repo)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        assertIs<DashboardUiState.Empty>(vm.state.value)
        sub.cancel()
    }

    @Test
    fun emitsSuccessWithMappedCardsWhenPopulated() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(
                listOf(
                    dashboard(
                        "d1",
                        name = "Home",
                        cards = listOf(
                            card("c1", "d1", "light.living_room", position = 1),
                            card("c2", "d1", "switch.kitchen", position = 0),
                        ),
                    ),
                ),
            )
        }
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val vm = buildVm(repo, connection = connection)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        val s = vm.state.value
        assertIs<DashboardUiState.Success>(s)
        assertEquals("Home", s.dashboardName)
        assertEquals(listOf("c2", "c1"), s.cards.map { it.cardId })
        assertEquals(listOf("switch.kitchen", "light.living_room"), s.cards.map { it.entityId })
        assertEquals(false, s.isStale)
        sub.cancel()
    }

    @Test
    fun isStaleTogglesOnConnectionChange() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.x")))))
        }
        val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val vm = buildVm(repo, connection = connection)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(false, (vm.state.value as DashboardUiState.Success).isStale)

        connection.value = ConnectionState.Reconnecting
        advanceUntilIdle()
        assertEquals(true, (vm.state.value as DashboardUiState.Success).isStale)

        connection.value = ConnectionState.Failed
        advanceUntilIdle()
        assertEquals(true, (vm.state.value as DashboardUiState.Success).isStale)
        sub.cancel()
    }

    @Test
    fun addCardOnEmptyRepoCreatesDashboardThenCard() = runTest {
        val repo = FakeDashboardRepository()
        val ids = SequentialIdGenerator(listOf("dash-1", "card-1"))
        val vm = buildVm(repo, idGenerator = ids)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.AddCard("light.living_room"))
        advanceUntilIdle()

        val saved = repo.dashboardsSaved
        assertEquals(1, saved.size)
        assertEquals("dash-1", saved.first().id)
        assertEquals("Home", saved.first().name)

        val added = repo.cardsAdded
        assertEquals(1, added.size)
        assertEquals("card-1", added.first().id)
        assertEquals("dash-1", added.first().dashboardId)
        assertEquals("light.living_room", added.first().entityId)
        assertEquals(0, added.first().position)
        sub.cancel()
    }

    @Test
    fun addCardWhenDashboardExistsAppendsAtEnd() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(
                listOf(
                    dashboard(
                        "d1",
                        cards = listOf(card("c1", "d1", "switch.x", position = 0)),
                    ),
                ),
            )
        }
        val ids = SequentialIdGenerator(listOf("card-2"))
        val vm = buildVm(repo, idGenerator = ids)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.AddCard("light.y"))
        advanceUntilIdle()

        assertEquals(0, repo.dashboardsSaved.size, "should not bootstrap when dashboard exists")
        val added = repo.cardsAdded.single()
        assertEquals("card-2", added.id)
        assertEquals("d1", added.dashboardId)
        assertEquals("light.y", added.entityId)
        assertEquals(1, added.position)
        sub.cancel()
    }

    @Test
    fun removeCardInvokesUseCase() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.x")))))
        }
        val vm = buildVm(repo)
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.RemoveCard("c1"))
        advanceUntilIdle()

        assertEquals(listOf("c1"), repo.cardsRemoved)
    }

    @Test
    fun reorderDebouncesAndDispatchesOnce() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(
                listOf(
                    dashboard(
                        "d1",
                        cards = listOf(
                            card("a", "d1", "light.a", position = 0),
                            card("b", "d1", "light.b", position = 1),
                            card("c", "d1", "light.c", position = 2),
                        ),
                    ),
                ),
            )
        }
        val vm = buildVm(repo)
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.Reorder(listOf("c", "a", "b")))
        advanceTimeBy(100)
        vm.onIntent(DashboardIntent.Reorder(listOf("b", "a", "c")))
        advanceTimeBy(100)
        vm.onIntent(DashboardIntent.Reorder(listOf("a", "b", "c")))

        // Before debounce window elapses, no dispatch has happened.
        assertEquals(0, repo.reorderCalls.size)

        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(1, repo.reorderCalls.size, "debounce coalesces to single dispatch")
        assertEquals("d1" to listOf("a", "b", "c"), repo.reorderCalls.first())
    }

    @Test
    fun openAndDismissPickerTogglesState() = runTest {
        val repo = FakeDashboardRepository()
        val vm = buildVm(repo)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.OpenPicker)
        advanceUntilIdle()
        assertEquals(true, (vm.state.value as DashboardUiState.Empty).pickerVisible)

        vm.onIntent(DashboardIntent.DismissPicker)
        advanceUntilIdle()
        assertEquals(false, (vm.state.value as DashboardUiState.Empty).pickerVisible)
        sub.cancel()
    }

    @Test
    fun addCardFailureEmitsRejectionHapticWithoutCrash() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
            addCardResultFactory = { Result.failure(IllegalStateException("boom")) }
        }
        val vm = buildVm(repo)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        val collected = mutableListOf<Any>()
        val collectorJob = launch { vm.haptics.collect { collected += it } }

        vm.onIntent(DashboardIntent.AddCard("light.x"))
        advanceUntilIdle()

        assertTrue(collected.isNotEmpty(), "expected ActionRejected haptic")
        collectorJob.cancel()
        sub.cancel()
    }

    // ── Story 4.7: switcher + multi-dashboard ─────────────────────────────────

    @Test
    fun staleActiveIdFallsBackToFirstDashboard() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", name = "First", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("does-not-exist")
        val vm = buildVm(repo, activeRepo = active)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        val s = vm.state.value
        assertIs<DashboardUiState.Success>(s)
        assertEquals("First", s.dashboardName)
        assertEquals("d1", s.activeDashboardId)
        sub.cancel()
    }

    @Test
    fun confirmCreateDashboardSetsPendingAndEmptyState() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", name = "Home", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(repo, activeRepo = active, idGenerator = SequentialIdGenerator(listOf("pending-1")))
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.ConfirmCreateDashboard("Office"))
        advanceUntilIdle()

        // No DashboardRepository.saveDashboard yet — pending only.
        assertEquals(0, repo.dashboardsSaved.size)
        // D2: pending NOT persisted to DataStore; active id stays at the prior persisted value
        // until first card flush. Pending dashboard wins via activeView only because its id
        // differs from the persisted id.
        assertEquals("d1", active.observeActiveDashboardId().first())
        val s = vm.state.value
        assertIs<DashboardUiState.Empty>(s)
        assertEquals("pending-1", s.switcher.activeDashboardId)
        sub.cancel()
    }

    @Test
    fun addCardOnPendingPersistsDashboardThenCard() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", name = "Home", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(
            repo,
            activeRepo = active,
            idGenerator = SequentialIdGenerator(listOf("pending-1", "card-99")),
        )
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.ConfirmCreateDashboard("Office"))
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.AddCard("light.office"))
        advanceUntilIdle()

        assertEquals(1, repo.dashboardsSaved.size, "pending dashboard persisted on first card")
        assertEquals("pending-1", repo.dashboardsSaved.first().id)
        assertEquals("Office", repo.dashboardsSaved.first().name)
        val added = repo.cardsAdded.single()
        assertEquals("card-99", added.id)
        assertEquals("pending-1", added.dashboardId)
        assertEquals(0, added.position)
        sub.cancel()
    }

    @Test
    fun selectDashboardWritesActiveIdAndEmitsSwitchHaptic() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(
                listOf(
                    dashboard("d1", name = "Home", cards = listOf(card("c1", "d1", "light.a"))),
                    dashboard("d2", name = "Office", cards = listOf(card("c2", "d2", "light.b"))),
                ),
            )
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(repo, activeRepo = active)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        val haptics = mutableListOf<Any>()
        val hapticJob = launch { vm.haptics.collect { haptics += it } }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.SelectDashboard("d2"))
        advanceUntilIdle()

        assertEquals("d2", active.lastSet)
        assertTrue(haptics.isNotEmpty(), "expected DashboardSwitch haptic")
        hapticJob.cancel()
        sub.cancel()
    }

    @Test
    fun selectingActiveDashboardIsNoOp() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(repo, activeRepo = active)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        val haptics = mutableListOf<Any>()
        val hapticJob = launch { vm.haptics.collect { haptics += it } }
        active.lastSet = null  // reset baseline

        vm.onIntent(DashboardIntent.SelectDashboard("d1"))
        advanceUntilIdle()

        assertNull(active.lastSet, "no DataStore write for same-id selection")
        assertEquals(0, haptics.size, "no haptic for same-id selection")
        hapticJob.cancel()
        sub.cancel()
    }

    @Test
    fun deleteActiveDashboardAdvancesToNextRemaining() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(
                listOf(
                    dashboard("d1", name = "Home", cards = listOf(card("c1", "d1", "light.a"))),
                    dashboard("d2", name = "Office", cards = listOf(card("c2", "d2", "light.b"))),
                ),
            )
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(repo, activeRepo = active)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.RequestDeleteDashboard("d1"))
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.ConfirmDeleteDashboard("d1"))
        advanceUntilIdle()

        assertEquals(listOf("d1"), repo.dashboardsDeleted)
        assertEquals("d2", active.lastSet, "active id auto-advanced to remaining first")
        sub.cancel()
    }

    @Test
    fun requestDeleteOnPendingDiscardsWithoutUseCase() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", name = "Home", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(repo, activeRepo = active, idGenerator = SequentialIdGenerator(listOf("pending-1")))
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.ConfirmCreateDashboard("Tmp"))
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.RequestDeleteDashboard("pending-1"))
        advanceUntilIdle()

        assertEquals(0, repo.dashboardsDeleted.size, "DeleteDashboardUseCase not invoked for pending")
        // Active id may be stale ("pending-1") in DataStore; resolved in UI via fallback.
        sub.cancel()
    }

    @Test
    fun requestDeleteOnSoleDashboardEmitsRejection() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(repo, activeRepo = active)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        val haptics = mutableListOf<Any>()
        val hapticJob = launch { vm.haptics.collect { haptics += it } }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.RequestDeleteDashboard("d1"))
        advanceUntilIdle()

        assertTrue(haptics.isNotEmpty(), "expected ActionRejected haptic")
        assertEquals(0, repo.dashboardsDeleted.size)
        hapticJob.cancel()
        sub.cancel()
    }

    @Test
    fun renameDashboardInvokesUseCase() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", name = "Home", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(repo, activeRepo = active)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.ConfirmRename("d1", "Living"))
        advanceUntilIdle()

        assertEquals(listOf("d1" to "Living"), repo.renames)
        sub.cancel()
    }

    @Test
    fun chromeReceivesActiveNameOnSwitch() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(
                listOf(
                    dashboard("d1", name = "Home", cards = listOf(card("c1", "d1", "light.a"))),
                    dashboard("d2", name = "Office", cards = listOf(card("c2", "d2", "light.b"))),
                ),
            )
        }
        val active = FakeActiveDashboardRepository("d1")
        val chrome = DashboardChrome()
        val vm = buildVm(repo, activeRepo = active, chrome = chrome)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals("Home", chrome.activeDashboardName.value)

        vm.onIntent(DashboardIntent.SelectDashboard("d2"))
        advanceUntilIdle()
        assertEquals("Office", chrome.activeDashboardName.value)
        sub.cancel()
    }

    @Test
    fun chromeOpenSwitcherSignalOpensSheet() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val chrome = DashboardChrome()
        val vm = buildVm(repo, activeRepo = active, chrome = chrome)
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        chrome.requestOpenSwitcher()
        advanceUntilIdle()

        val s = vm.state.value
        assertIs<DashboardUiState.Success>(s)
        assertEquals(true, s.switcher.visible)
        sub.cancel()
    }

    // ── Story 4.7 — additional VM coverage (P14) ──────────────────────────────

    @Test
    fun switcherOpensAndClosesViaIntents() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val vm = buildVm(repo, activeRepo = FakeActiveDashboardRepository("d1"))
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.OpenSwitcher)
        advanceUntilIdle()
        assertEquals(true, (vm.state.value as DashboardUiState.Success).switcher.visible)

        vm.onIntent(DashboardIntent.DismissSwitcher)
        advanceUntilIdle()
        assertEquals(false, (vm.state.value as DashboardUiState.Success).switcher.visible)
        sub.cancel()
    }

    @Test
    fun beginCreateDashboardOpensInlineField() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val vm = buildVm(repo, activeRepo = FakeActiveDashboardRepository("d1"))
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.OpenSwitcher)
        vm.onIntent(DashboardIntent.BeginCreateDashboard)
        vm.onIntent(DashboardIntent.UpdateNewDashboardName("Office"))
        advanceUntilIdle()

        val sw = (vm.state.value as DashboardUiState.Success).switcher
        assertEquals(true, sw.creating)
        assertEquals("Office", sw.pendingNewName)
        sub.cancel()
    }

    @Test
    fun confirmCreateDashboardOnEmptyNameIsRejected() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(repo, activeRepo = active)
        val collected = mutableListOf<com.backpapp.hanative.platform.HapticPattern>()
        val collectorJob = launch { vm.haptics.collect { collected += it } }
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.OpenSwitcher)
        vm.onIntent(DashboardIntent.BeginCreateDashboard)
        vm.onIntent(DashboardIntent.ConfirmCreateDashboard("   "))
        advanceUntilIdle()

        // No pending dashboard; no DataStore write; haptic emitted.
        assertEquals(0, repo.dashboardsSaved.size)
        assertEquals("d1", active.observeActiveDashboardId().first())
        assertTrue(collected.contains(com.backpapp.hanative.platform.HapticPattern.ActionRejected))
        // creating state retained so user can retry without re-tapping the footer.
        assertEquals(true, (vm.state.value as DashboardUiState.Success).switcher.creating)
        collectorJob.cancel()
        sub.cancel()
    }

    @Test
    fun pendingDashboardDiscardedOnSwitch() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val active = FakeActiveDashboardRepository("d1")
        val vm = buildVm(
            repo,
            activeRepo = active,
            idGenerator = SequentialIdGenerator(listOf("pending-1")),
        )
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.ConfirmCreateDashboard("Office"))
        advanceUntilIdle()
        // Pending wins via activeView (different id).
        assertEquals("pending-1", (vm.state.value as DashboardUiState.Empty).switcher.activeDashboardId)

        vm.onIntent(DashboardIntent.SelectDashboard("d1"))
        advanceUntilIdle()
        // Pending discarded; no SaveDashboardUseCase invocation; active id flips to d1.
        assertEquals(0, repo.dashboardsSaved.size)
        assertEquals("d1", active.lastSet)
        val s = vm.state.value
        assertIs<DashboardUiState.Success>(s)
        assertEquals("d1", s.activeDashboardId)
        sub.cancel()
    }

    @Test
    fun renameDashboardRejectedOnBlank() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", name = "Home", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val vm = buildVm(repo, activeRepo = FakeActiveDashboardRepository("d1"))
        val collected = mutableListOf<com.backpapp.hanative.platform.HapticPattern>()
        val collectorJob = launch { vm.haptics.collect { collected += it } }
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.OpenSwitcher)
        vm.onIntent(DashboardIntent.BeginRenameDashboard("d1"))
        vm.onIntent(DashboardIntent.ConfirmRename("d1", "   "))
        advanceUntilIdle()

        assertEquals(0, repo.renames.size)
        assertTrue(collected.contains(com.backpapp.hanative.platform.HapticPattern.ActionRejected))
        // renamingId retained so user can retry.
        assertEquals("d1", (vm.state.value as DashboardUiState.Success).switcher.renamingId)
        collectorJob.cancel()
        sub.cancel()
    }

    @Test
    fun renamePendingDashboardMutatesPendingNotRepo() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(listOf(dashboard("d1", cards = listOf(card("c1", "d1", "light.a")))))
        }
        val vm = buildVm(
            repo,
            activeRepo = FakeActiveDashboardRepository("d1"),
            idGenerator = SequentialIdGenerator(listOf("pending-1")),
        )
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.ConfirmCreateDashboard("Office"))
        advanceUntilIdle()
        vm.onIntent(DashboardIntent.OpenSwitcher)
        vm.onIntent(DashboardIntent.BeginRenameDashboard("pending-1"))
        vm.onIntent(DashboardIntent.ConfirmRename("pending-1", "Studio"))
        advanceUntilIdle()

        // RenameDashboardUseCase NOT called for pending; pending name updated in place.
        assertEquals(0, repo.renames.size)
        val sw = (vm.state.value as DashboardUiState.Empty).switcher
        val pendingRow = sw.dashboards.firstOrNull { it.id == "pending-1" }
        assertEquals("Studio", pendingRow?.name)
        assertNull(sw.renamingId)
        sub.cancel()
    }

    @Test
    fun requestDeleteOpensDialog() = runTest {
        val repo = FakeDashboardRepository().apply {
            update(
                listOf(
                    dashboard("d1", cards = listOf(card("c1", "d1", "light.a"))),
                    dashboard("d2", cards = listOf(card("c2", "d2", "light.b"))),
                ),
            )
        }
        val vm = buildVm(repo, activeRepo = FakeActiveDashboardRepository("d1"))
        val sub = launch { vm.state.collect {} }
        advanceUntilIdle()

        vm.onIntent(DashboardIntent.OpenSwitcher)
        vm.onIntent(DashboardIntent.RequestDeleteDashboard("d2"))
        advanceUntilIdle()

        val sw = (vm.state.value as DashboardUiState.Success).switcher
        assertEquals("d2", sw.pendingDeleteId)
        assertEquals(0, repo.dashboardsDeleted.size)
        sub.cancel()
    }

    @Test
    fun intentsIgnoredWhenLoading() = runTest {
        // Do not seed the repo flow; state remains Loading until first emission.
        val repo = FakeDashboardRepository()
        val vm = buildVm(repo, activeRepo = FakeActiveDashboardRepository(null))
        // Don't subscribe — keeps state derivation cold so VM stays at Loading initial value.
        assertIs<DashboardUiState.Loading>(vm.state.value)

        // Switcher / CRUD intents must be ignored while Loading.
        vm.onIntent(DashboardIntent.OpenSwitcher)
        vm.onIntent(DashboardIntent.BeginCreateDashboard)
        vm.onIntent(DashboardIntent.ConfirmCreateDashboard("X"))
        vm.onIntent(DashboardIntent.SelectDashboard("d1"))
        vm.onIntent(DashboardIntent.RequestDeleteDashboard("d1"))
        vm.onIntent(DashboardIntent.ConfirmDeleteDashboard("d1"))
        vm.onIntent(DashboardIntent.BeginRenameDashboard("d1"))
        vm.onIntent(DashboardIntent.ConfirmRename("d1", "Renamed"))
        advanceUntilIdle()

        assertEquals(0, repo.dashboardsSaved.size)
        assertEquals(0, repo.dashboardsDeleted.size)
        assertEquals(0, repo.renames.size)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildVm(
        repo: FakeDashboardRepository,
        activeRepo: FakeActiveDashboardRepository = FakeActiveDashboardRepository(null),
        chrome: DashboardChrome = DashboardChrome(),
        connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected),
        idGenerator: IdGenerator = SequentialIdGenerator(generateSequence(0L) { it + 1 }.map { "id-$it" }.take(50).toList()),
    ): DashboardViewModel = DashboardViewModel(
        getDashboards = GetDashboardsUseCase(repo),
        saveDashboard = SaveDashboardUseCase(repo),
        addCard = AddCardUseCase(repo),
        removeCard = RemoveCardUseCase(repo),
        reorderCards = ReorderCardsUseCase(repo),
        renameDashboard = RenameDashboardUseCase(repo),
        deleteDashboard = DeleteDashboardUseCase(repo),
        getActiveDashboardId = GetActiveDashboardIdUseCase(activeRepo),
        setActiveDashboardId = SetActiveDashboardIdUseCase(activeRepo),
        idGenerator = idGenerator,
        connectionState = connection,
        dashboardChrome = chrome,
        clock = FixedClock(Instant.fromEpochMilliseconds(1_700_000_000_000L)),
    )

    private fun dashboard(id: String, name: String = "Home", cards: List<DashboardCard>) = Dashboard(
        id = id,
        name = name,
        position = 0,
        createdAt = 0L,
        cards = cards,
    )

    private fun card(id: String, dashboardId: String, entityId: String, position: Int = 0) = DashboardCard(
        id = id,
        dashboardId = dashboardId,
        entityId = entityId,
        position = position,
        config = "",
    )
}

private class FakeDashboardRepository : DashboardRepository {
    private val flow = MutableStateFlow<List<Dashboard>>(emptyList())
    val dashboardsSaved = mutableListOf<Dashboard>()
    val dashboardsDeleted = mutableListOf<String>()
    val cardsAdded = mutableListOf<DashboardCard>()
    val cardsRemoved = mutableListOf<String>()
    val reorderCalls = mutableListOf<Pair<String, List<String>>>()
    val renames = mutableListOf<Pair<String, String>>()
    var addCardResultFactory: () -> Result<Unit> = { Result.success(Unit) }

    fun update(list: List<Dashboard>) { flow.value = list }

    override fun getDashboards(): Flow<List<Dashboard>> = flow

    override suspend fun saveDashboard(dashboard: Dashboard): Result<Unit> {
        dashboardsSaved += dashboard
        flow.update { current -> if (current.any { it.id == dashboard.id }) current else current + dashboard }
        return Result.success(Unit)
    }

    override suspend fun renameDashboard(dashboardId: String, name: String): Result<Unit> {
        renames += dashboardId to name
        flow.update { current ->
            current.map { d -> if (d.id == dashboardId) d.copy(name = name) else d }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteDashboard(dashboardId: String): Result<Unit> {
        dashboardsDeleted += dashboardId
        flow.update { current -> current.filterNot { it.id == dashboardId } }
        return Result.success(Unit)
    }

    override suspend fun addCard(card: DashboardCard): Result<Unit> {
        cardsAdded += card
        val result = addCardResultFactory()
        if (result.isSuccess) {
            flow.update { current ->
                current.map { d ->
                    if (d.id == card.dashboardId) d.copy(cards = d.cards + card) else d
                }
            }
        }
        return result
    }

    override suspend fun removeCard(cardId: String): Result<Unit> {
        cardsRemoved += cardId
        flow.update { current ->
            current.map { d -> d.copy(cards = d.cards.filterNot { it.id == cardId }) }
        }
        return Result.success(Unit)
    }

    override suspend fun reorderCards(dashboardId: String, cardIds: List<String>): Result<Unit> {
        reorderCalls += dashboardId to cardIds
        return Result.success(Unit)
    }
}

private class FakeActiveDashboardRepository(initial: String?) : ActiveDashboardRepository {
    private val flow = MutableStateFlow(initial)
    var lastSet: String? = null

    override fun observeActiveDashboardId(): Flow<String?> = flow

    override suspend fun setActiveDashboardId(dashboardId: String?): Result<Unit> {
        lastSet = dashboardId
        flow.value = dashboardId
        return Result.success(Unit)
    }
}

private class SequentialIdGenerator(private val ids: List<String>) : IdGenerator {
    private var index = 0
    override fun generate(): String = ids.getOrElse(index++) { "id-${index - 1}" }
}

@OptIn(ExperimentalTime::class)
private class FixedClock(private val now: Instant) : Clock {
    override fun now(): Instant = now
}
