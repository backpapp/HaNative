package com.backpapp.hanative.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.data.remote.ServerManager
import com.backpapp.hanative.domain.model.Dashboard
import com.backpapp.hanative.domain.model.DashboardCard
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
import com.backpapp.hanative.platform.HapticPattern
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(FlowPreview::class, ExperimentalTime::class)
class DashboardViewModel(
    private val getDashboards: GetDashboardsUseCase,
    private val saveDashboard: SaveDashboardUseCase,
    private val addCard: AddCardUseCase,
    private val removeCard: RemoveCardUseCase,
    private val reorderCards: ReorderCardsUseCase,
    private val renameDashboard: RenameDashboardUseCase,
    private val deleteDashboard: DeleteDashboardUseCase,
    private val getActiveDashboardId: GetActiveDashboardIdUseCase,
    private val setActiveDashboardId: SetActiveDashboardIdUseCase,
    private val idGenerator: IdGenerator,
    private val connectionState: StateFlow<ServerManager.ConnectionState>,
    private val dashboardChrome: DashboardChrome,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private data class PendingDashboard(val id: String, val name: String, val createdAt: Long)

    private data class SwitcherChromeState(
        val visible: Boolean = false,
        val creating: Boolean = false,
        val pendingNewName: String = "",
        val renamingId: String? = null,
        val pendingRenameText: String = "",
        val pendingDeleteId: String? = null,
    )

    private sealed interface ActiveView {
        val id: String
        val name: String
        val cards: List<DashboardCard>
        data class Persisted(val d: Dashboard) : ActiveView {
            override val id: String get() = d.id
            override val name: String get() = d.name
            override val cards: List<DashboardCard> get() = d.cards
        }
        data class Pending(val p: PendingDashboard) : ActiveView {
            override val id: String get() = p.id
            override val name: String get() = p.name
            override val cards: List<DashboardCard> get() = emptyList()
        }
    }

    private val _pickerVisible = MutableStateFlow(false)
    private val _optimisticOrder = MutableStateFlow<List<String>?>(null)
    private val _pendingNewDashboard = MutableStateFlow<PendingDashboard?>(null)
    private val _switcherChrome = MutableStateFlow(SwitcherChromeState())

    private val _haptics = MutableSharedFlow<HapticPattern>(extraBufferCapacity = 4)
    val haptics: SharedFlow<HapticPattern> = _haptics.asSharedFlow()

    private val _reorderRequests = MutableSharedFlow<List<String>>(extraBufferCapacity = 16)
    private val addCardMutex = Mutex()

    private data class Sources(
        val dashboards: List<Dashboard>,
        val persistedActiveId: String?,
        val pending: PendingDashboard?,
        val switcherChrome: SwitcherChromeState,
    )

    val state: StateFlow<DashboardUiState> = combine(
        combine(
            getDashboards(),
            getActiveDashboardId(),
            _pendingNewDashboard,
            _switcherChrome,
        ) { d, aid, pn, sc -> Sources(d, aid, pn, sc) },
        connectionState,
        _pickerVisible,
        _optimisticOrder,
    ) { sources, connection, pickerVisible, optimisticOrder ->
        deriveState(sources, connection, pickerVisible, optimisticOrder)
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
            initialValue = DashboardUiState.Loading,
        )

    init {
        _reorderRequests
            .debounce(REORDER_DEBOUNCE_MS.milliseconds)
            .onEach { ids -> dispatchReorder(ids) }
            .launchIn(viewModelScope)

        // Outbound: push active dashboard name to chrome for nav bar label.
        state
            .map { uiStateName(it) }
            .distinctUntilChanged()
            .onEach { dashboardChrome.setActiveDashboardName(it) }
            .launchIn(viewModelScope)

        // Inbound: open switcher when nav-bar tap-while-selected fires.
        dashboardChrome.openSwitcherSignals
            .onEach { onIntent(DashboardIntent.OpenSwitcher) }
            .launchIn(viewModelScope)

        // D6: clear transient switcher chrome whenever derived state goes Loading,
        // so a latent visible=true doesn't re-pop the sheet on next Empty/Success emission.
        state
            .map { it is DashboardUiState.Loading }
            .distinctUntilChanged()
            .onEach { isLoading ->
                if (isLoading) {
                    _switcherChrome.update {
                        it.copy(
                            visible = false,
                            creating = false,
                            pendingNewName = "",
                            renamingId = null,
                            pendingRenameText = "",
                            pendingDeleteId = null,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        // Chrome name retained across VM teardown — survives Dashboard→Settings→back without label flicker.
        // Clear only on logout (separate hook, not wired in V1).
        super.onCleared()
    }

    fun onIntent(intent: DashboardIntent) {
        val loading = state.value is DashboardUiState.Loading
        when (intent) {
            // Switcher / CRUD intents gated on Loading per spec; dismiss/cancel always allowed.
            is DashboardIntent.AddCard -> if (!loading) handleAddCard(intent.entityId)
            is DashboardIntent.RemoveCard -> if (!loading) handleRemoveCard(intent.cardId)
            is DashboardIntent.Reorder -> if (!loading) handleReorder(intent.orderedCardIds)
            DashboardIntent.OpenPicker -> if (!loading) _pickerVisible.value = true
            DashboardIntent.DismissPicker -> _pickerVisible.value = false
            DashboardIntent.OpenSwitcher -> if (!loading) _switcherChrome.update { it.copy(visible = true) }
            DashboardIntent.DismissSwitcher -> _switcherChrome.update {
                it.copy(
                    visible = false,
                    creating = false,
                    pendingNewName = "",
                    renamingId = null,
                    pendingRenameText = "",
                    pendingDeleteId = null,
                )
            }
            is DashboardIntent.SelectDashboard -> if (!loading) handleSelectDashboard(intent.dashboardId)
            DashboardIntent.BeginCreateDashboard -> if (!loading) _switcherChrome.update {
                it.copy(creating = true, pendingNewName = "")
            }
            DashboardIntent.CancelCreateDashboard -> _switcherChrome.update {
                it.copy(creating = false, pendingNewName = "")
            }
            is DashboardIntent.UpdateNewDashboardName -> if (!loading) _switcherChrome.update {
                it.copy(pendingNewName = intent.text.take(NAME_MAX_LEN))
            }
            is DashboardIntent.ConfirmCreateDashboard -> if (!loading) handleConfirmCreate(intent.name)
            is DashboardIntent.BeginRenameDashboard -> if (!loading) handleBeginRename(intent.dashboardId)
            DashboardIntent.CancelRenameDashboard -> _switcherChrome.update {
                it.copy(renamingId = null, pendingRenameText = "")
            }
            is DashboardIntent.UpdateRenameText -> if (!loading) _switcherChrome.update {
                it.copy(pendingRenameText = intent.text.take(NAME_MAX_LEN))
            }
            is DashboardIntent.ConfirmRename -> if (!loading) handleConfirmRename(intent.dashboardId, intent.name)
            is DashboardIntent.RequestDeleteDashboard -> if (!loading) handleRequestDelete(intent.dashboardId)
            DashboardIntent.CancelDeleteDashboard -> _switcherChrome.update {
                it.copy(pendingDeleteId = null)
            }
            is DashboardIntent.ConfirmDeleteDashboard -> if (!loading) handleConfirmDelete(intent.dashboardId)
        }
    }

    private fun deriveState(
        sources: Sources,
        connection: ServerManager.ConnectionState,
        pickerVisible: Boolean,
        optimisticOrder: List<String>?,
    ): DashboardUiState {
        val (dashboards, persistedActiveId, pending, switcherChrome) = sources
        val active = activeView(dashboards, persistedActiveId, pending)
        val switcher = buildSwitcherUi(dashboards, pending, active?.id, switcherChrome)

        if (active == null || active.cards.isEmpty()) {
            return DashboardUiState.Empty(pickerVisible = pickerVisible, switcher = switcher)
        }
        val isStale = connection != ServerManager.ConnectionState.Connected
        val sorted = active.cards.sortedBy { it.position }
        val ordered = if (optimisticOrder != null) {
            val byId = sorted.associateBy { it.id }
            optimisticOrder.mapNotNull { byId[it] }
        } else {
            sorted
        }
        return DashboardUiState.Success(
            dashboardName = active.name,
            activeDashboardId = active.id,
            cards = ordered.map { DashboardCardUi(cardId = it.id, entityId = it.entityId) },
            isStale = isStale,
            pickerVisible = pickerVisible,
            switcher = switcher,
        )
    }

    private fun activeView(
        dashboards: List<Dashboard>,
        persistedActiveId: String?,
        pending: PendingDashboard?,
    ): ActiveView? {
        // Pending wins only when its id is not the same as the persisted active id.
        // If they match (e.g. race after pending flush wrote the id), defer to Persisted lookup
        // so SelectDashboard on the pending row doesn't route through "switching away".
        if (pending != null && pending.id != persistedActiveId) {
            return ActiveView.Pending(pending)
        }
        if (persistedActiveId != null) {
            dashboards.firstOrNull { it.id == persistedActiveId }
                ?.let { return ActiveView.Persisted(it) }
        }
        // Persisted id absent or stale (no matching row) — fall back to pending-if-any then first.
        if (pending != null) return ActiveView.Pending(pending)
        return dashboards.firstOrNull()?.let { ActiveView.Persisted(it) }
    }

    private fun buildSwitcherUi(
        dashboards: List<Dashboard>,
        pending: PendingDashboard?,
        activeId: String?,
        chrome: SwitcherChromeState,
    ): DashboardSwitcherUi {
        val summaries = dashboards.map { d ->
            DashboardSummaryUi(id = d.id, name = d.name, cardCount = d.cards.size)
        }
        val withPending = if (pending != null) {
            summaries + DashboardSummaryUi(
                id = pending.id,
                name = pending.name,
                cardCount = 0,
                isPending = true,
            )
        } else summaries
        return DashboardSwitcherUi(
            visible = chrome.visible,
            dashboards = withPending,
            activeDashboardId = activeId,
            creating = chrome.creating,
            pendingNewName = chrome.pendingNewName,
            renamingId = chrome.renamingId,
            pendingRenameText = chrome.pendingRenameText,
            pendingDeleteId = chrome.pendingDeleteId,
            // canDelete reflects persisted-only count — pending doesn't count as "another dashboard
            // you can fall back to". Pending row gets unconditional Delete via isPending in the sheet.
            canDelete = summaries.size > 1,
        )
    }

    private fun uiStateName(state: DashboardUiState): String? = when (state) {
        DashboardUiState.Loading -> null
        is DashboardUiState.Success -> state.dashboardName
        is DashboardUiState.Empty -> {
            val sw = state.switcher
            sw.dashboards.firstOrNull { it.id == sw.activeDashboardId }?.name
        }
    }

    private fun handleAddCard(entityId: String) {
        viewModelScope.launch {
            addCardMutex.withLock {
                val current = currentDashboards()
                if (current == null) {
                    _haptics.tryEmit(HapticPattern.ActionRejected)
                    return@withLock
                }

                val pending = _pendingNewDashboard.value
                val (activeId, isFirstCardOnPending) = if (pending != null) {
                    // Persist pending dashboard before card insert.
                    val nextPosition = current.size
                    val saveResult = saveDashboard(
                        Dashboard(
                            id = pending.id,
                            name = pending.name,
                            position = nextPosition,
                            createdAt = pending.createdAt,
                            cards = emptyList(),
                        ),
                    )
                    if (saveResult.isFailure) {
                        _haptics.tryEmit(HapticPattern.ActionRejected)
                        return@withLock
                    }
                    setActiveDashboardId(pending.id)
                    pending.id to true
                } else {
                    val ensured = ensureActiveDashboard(current) ?: run {
                        _haptics.tryEmit(HapticPattern.ActionRejected)
                        return@withLock
                    }
                    ensured to false
                }

                val nextCardPos = if (isFirstCardOnPending) {
                    0
                } else {
                    current.firstOrNull { it.id == activeId }?.cards?.size ?: 0
                }

                val card = DashboardCard(
                    id = idGenerator.generate(),
                    dashboardId = activeId,
                    entityId = entityId,
                    position = nextCardPos,
                    config = "",
                )
                val result = addCard(card)
                if (result.isSuccess) {
                    if (isFirstCardOnPending) {
                        _pendingNewDashboard.value = null
                    }
                    _pickerVisible.value = false
                } else {
                    _haptics.tryEmit(HapticPattern.ActionRejected)
                }
            }
        }
    }

    private suspend fun ensureActiveDashboard(currentDashboards: List<Dashboard>): String? {
        // P7: bounded — `first()` on an empty cold DataStore can suspend indefinitely
        // and would freeze every subsequent AddCard via the held mutex.
        val persistedId = currentPersistedActiveId()
        val matched = persistedId?.let { id -> currentDashboards.firstOrNull { it.id == id } }
        if (matched != null) return matched.id
        val fallback = currentDashboards.firstOrNull()
        if (fallback != null) return fallback.id
        // Bootstrap "Home" — Story 4.6 first-card path retained.
        val id = idGenerator.generate()
        val saveResult = saveDashboard(
            Dashboard(
                id = id,
                name = "Home",
                position = 0,
                createdAt = clock.now().toEpochMilliseconds(),
                cards = emptyList(),
            ),
        )
        if (saveResult.isFailure) return null
        setActiveDashboardId(id)
        return id
    }

    private fun handleSelectDashboard(dashboardId: String) {
        val active = currentActiveId()
        if (active == dashboardId) {
            // Same id — close sheet, no haptic, no persist.
            closeSwitcher()
            return
        }
        // Switching away from a pending dashboard discards it (P3: clear stale rename target too).
        if (_pendingNewDashboard.value != null && _pendingNewDashboard.value?.id != dashboardId) {
            _pendingNewDashboard.value = null
        }
        // P8: optimistic reorder belongs to the outgoing dashboard's cards — drop on switch.
        _optimisticOrder.value = null
        viewModelScope.launch {
            setActiveDashboardId(dashboardId)
        }
        _haptics.tryEmit(HapticPattern.DashboardSwitch)
        closeSwitcher()
    }

    // P1 + P2: closing the sheet for any reason flushes all transient sheet state
    // (creating, renaming, pending delete) so reopening shows a clean sheet.
    private fun closeSwitcher() {
        _switcherChrome.update {
            it.copy(
                visible = false,
                creating = false,
                pendingNewName = "",
                renamingId = null,
                pendingRenameText = "",
                pendingDeleteId = null,
            )
        }
    }

    private fun handleConfirmCreate(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _haptics.tryEmit(HapticPattern.ActionRejected)
            return
        }
        val id = idGenerator.generate()
        val createdAt = clock.now().toEpochMilliseconds()
        // D2: pending NOT persisted to DataStore. Active id flushes only on first AddCard.
        // activeView() prefers pending over persisted when ids differ, so the UI shows
        // the pending name immediately even though DataStore is unchanged.
        _pendingNewDashboard.value = PendingDashboard(id = id, name = trimmed, createdAt = createdAt)
        closeSwitcher()
    }

    private fun handleBeginRename(dashboardId: String) {
        if (state.value is DashboardUiState.Loading) return
        val seed = _pendingNewDashboard.value?.takeIf { it.id == dashboardId }?.name
            ?: state.value.let { s ->
                val list = when (s) {
                    is DashboardUiState.Success -> s.switcher.dashboards
                    is DashboardUiState.Empty -> s.switcher.dashboards
                    DashboardUiState.Loading -> emptyList()
                }
                list.firstOrNull { it.id == dashboardId }?.name.orEmpty()
            }
        _switcherChrome.update { it.copy(renamingId = dashboardId, pendingRenameText = seed) }
    }

    private fun handleConfirmRename(dashboardId: String, name: String) {
        if (state.value is DashboardUiState.Loading) return
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _haptics.tryEmit(HapticPattern.ActionRejected)
            return
        }
        val pending = _pendingNewDashboard.value
        if (pending != null && pending.id == dashboardId) {
            _pendingNewDashboard.value = pending.copy(name = trimmed)
            _switcherChrome.update { it.copy(renamingId = null, pendingRenameText = "") }
            return
        }
        viewModelScope.launch {
            val result = renameDashboard(dashboardId, trimmed)
            if (result.isSuccess) {
                // P15: only clear the renaming overlay if the user hasn't started editing
                // a different row OR typed past the value we just confirmed. Preserves
                // in-flight edits typed during the DB suspend.
                _switcherChrome.update {
                    if (it.renamingId == dashboardId && it.pendingRenameText.trim() == trimmed) {
                        it.copy(renamingId = null, pendingRenameText = "")
                    } else it
                }
            } else {
                _haptics.tryEmit(HapticPattern.ActionRejected)
            }
        }
    }

    private fun handleRequestDelete(dashboardId: String) {
        val pending = _pendingNewDashboard.value
        if (pending != null && pending.id == dashboardId) {
            // D3=C: pending dashboard delete bypasses canDelete + dialog — discard immediately.
            // Clear any rename targeting the pending row so UI doesn't render stale text field.
            _pendingNewDashboard.value = null
            _switcherChrome.update {
                if (it.renamingId == dashboardId) it.copy(renamingId = null, pendingRenameText = "") else it
            }
            return
        }
        // Persisted-only count for canDelete: pending doesn't count toward "you have other dashboards".
        val persistedCount = when (val s = state.value) {
            is DashboardUiState.Success -> s.switcher.dashboards.count { it.id != pending?.id }
            is DashboardUiState.Empty -> s.switcher.dashboards.count { it.id != pending?.id }
            DashboardUiState.Loading -> 0
        }
        if (persistedCount <= 1) {
            _haptics.tryEmit(HapticPattern.ActionRejected)
            return
        }
        _switcherChrome.update { it.copy(pendingDeleteId = dashboardId) }
    }

    private fun handleConfirmDelete(dashboardId: String) {
        // P12: snapshot pre-delete persisted active id so a concurrent DataStore write
        // can't make us miss the auto-advance.
        viewModelScope.launch {
            val preDeleteActiveId = currentPersistedActiveId()
            val result = deleteDashboard(dashboardId)
            if (result.isFailure) {
                _haptics.tryEmit(HapticPattern.ActionRejected)
                return@launch
            }
            if (preDeleteActiveId == dashboardId) {
                val remaining = currentDashboards()
                    ?.firstOrNull { it.id != dashboardId }
                    ?.id
                // P12: only write when we have a real id; null write deletes the key,
                // leaving us in fallback-to-firstOrNull territory which is the same effect.
                if (remaining != null) {
                    setActiveDashboardId(remaining)
                } else {
                    setActiveDashboardId(null)
                }
            }
            _switcherChrome.update { it.copy(pendingDeleteId = null) }
        }
    }

    // P6: bounded read of persisted active id; matches currentDashboards() pattern.
    private suspend fun currentPersistedActiveId(): String? =
        withTimeoutOrNull(CURRENT_DASHBOARDS_TIMEOUT) {
            runCatchingNull { getActiveDashboardId().first() }
        }

    private fun currentActiveId(): String? = when (val s = state.value) {
        is DashboardUiState.Success -> s.activeDashboardId
        is DashboardUiState.Empty -> s.switcher.activeDashboardId
        DashboardUiState.Loading -> null
    }

    private fun handleRemoveCard(cardId: String) {
        _optimisticOrder.update { it?.minus(cardId) }
        viewModelScope.launch {
            val result = removeCard(cardId)
            if (result.isFailure) {
                _haptics.tryEmit(HapticPattern.ActionRejected)
            }
        }
    }

    private fun handleReorder(orderedCardIds: List<String>) {
        _optimisticOrder.value = orderedCardIds
        _reorderRequests.tryEmit(orderedCardIds)
    }

    private suspend fun dispatchReorder(orderedCardIds: List<String>) {
        val current = currentDashboards()
        val activeId = currentActiveId()
        val active = current?.firstOrNull { it.id == activeId }
        if (active == null) {
            _optimisticOrder.value = null
            return
        }
        val priorOrder = active.cards.sortedBy { it.position }.map { it.id }
        val result = reorderCards(active.id, orderedCardIds)
        if (result.isFailure) {
            _optimisticOrder.value = priorOrder
            _haptics.tryEmit(HapticPattern.ActionRejected)
        } else {
            _optimisticOrder.value = null
        }
    }

    private suspend fun currentDashboards(): List<Dashboard>? =
        withTimeoutOrNull(CURRENT_DASHBOARDS_TIMEOUT) { getDashboards().first() }

    private inline fun <T> runCatchingNull(block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        if (t is kotlinx.coroutines.CancellationException) throw t
        null
    }

    companion object {
        private const val REORDER_DEBOUNCE_MS = 250L
        private const val NAME_MAX_LEN = 40
        private val CURRENT_DASHBOARDS_TIMEOUT = 2.seconds
    }
}
