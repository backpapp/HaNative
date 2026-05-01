# Story 4.7: Dashboard Management — CRUD & Switcher

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a power user,
I want to create multiple named dashboards and switch between them in one tap,
so that "Morning", "Living Room", and "Kitchen Wall" each have exactly the cards I need, accessible instantly.

## Acceptance Criteria

1. **Dashboard tab label is dynamic.** The compact `NavigationBarItem` for the Dashboard tab (`HaNativeNavHost.CompactLayout` lines ~163–169) and the equivalent rail label (`ExpandedLayout` lines ~190–199) MUST render the active dashboard name (`Dashboard.name`), not the fixed `"Dashboard"` literal currently in `navItems`. When the user has no dashboards yet (cold first launch), label falls back to `"Dashboard"`. (UX-DR6)

2. **Tap-active-tab opens `DashboardSwitcherSheet`.** When the Dashboard tab is already `selected == true` AND the user taps it, the navigation bar dispatches an "open switcher" signal. Tapping the tab from any other tab simply selects Dashboard (no sheet, existing behavior). The signal travels through a singleton `DashboardChrome` coordinator (see AC10) — the nav host MUST NOT hold a direct ViewModel reference. (UX-DR6, AC from epic)

3. **`DashboardSwitcherSheet` composable** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardSwitcherSheet.kt`. Public signature:
   ```kotlin
   @Composable
   internal fun DashboardSwitcherSheet(
       state: DashboardSwitcherUi,
       onIntent: (DashboardIntent) -> Unit,
       modifier: Modifier = Modifier,
   )
   ```
   Renders an M3 `ModalBottomSheet` (Material3 `androidx.compose.material3.ModalBottomSheet`) when `state.visible`. Sheet anatomy:
   - Title `"Dashboards"` (`titleMedium`, 16dp horizontal padding, 12dp top, 4dp bottom).
   - `LazyColumn` of dashboard rows — one row per `DashboardSummaryUi` in `state.dashboards`.
   - Row anatomy: leading 24dp dashboard icon (`Icons.Outlined.GridView`), `Text(name, bodyLarge)`, trailing M3 `IconButton` with `Icons.Outlined.MoreVert` opening per-row `DropdownMenu` (rename / delete entries). Row `clickable` selects the dashboard.
   - Active dashboard row shows trailing `Icons.Outlined.Check` (`primary` tint) **before** the overflow icon — visual selection cue.
   - `Divider()` between rows + bottom of list.
   - Footer: "New Dashboard" row — leading `Icons.Outlined.Add`, label `"New Dashboard"` — clickable. When `state.creating == true`, footer instead becomes inline `TextField` with placeholder `"Name your dashboard"`, autofocus, IME action `Done`, and `Icons.Outlined.Check` confirm + `Icons.Outlined.Close` cancel trailing icons.
   - Sheet animates open via `Motion.bottomSheetOpen` and dismisses via `Motion.bottomSheetDismiss` — these specs exist at `Motion.kt:14,17` already; default `ModalBottomSheet` animation is M3 default and is acceptable since the spec values match (no `animationSpec` override needed unless future polish requires).

4. **Switcher state model** — extend `DashboardUiModels.kt`:
   ```kotlin
   data class DashboardSwitcherUi(
       val visible: Boolean = false,
       val dashboards: List<DashboardSummaryUi> = emptyList(),
       val activeDashboardId: String? = null,
       val creating: Boolean = false,
       val pendingNewName: String = "",
       val renamingId: String? = null,
       val pendingRenameText: String = "",
       val pendingDeleteId: String? = null,
       val canDelete: Boolean = false,           // dashboards.size > 1
   )

   data class DashboardSummaryUi(
       val id: String,
       val name: String,
       val cardCount: Int,
   )
   ```
   `DashboardSummaryUi` carries no domain types — it is a UI-layer projection produced by `DashboardViewModel.deriveSwitcher()`. **No `Dashboard` import in any composable file under `ui/dashboard/`** beyond `DashboardViewModel.kt`. (Memorialized in `feedback_compose_architecture.md`.)

5. **`DashboardUiState` carries the switcher slice.** Both `Empty` and `Success` gain a `switcher: DashboardSwitcherUi` field (default `DashboardSwitcherUi()`). `Loading` does not need one — VM ignores switcher intents while `state.value is Loading` (mirror of Story 4.6 D2 gating). Updated `DashboardUiModels.kt`:
   ```kotlin
   sealed class DashboardUiState {
       data object Loading : DashboardUiState()
       data class Empty(
           val pickerVisible: Boolean = false,
           val switcher: DashboardSwitcherUi = DashboardSwitcherUi(),
       ) : DashboardUiState()
       data class Success(
           val dashboardName: String,
           val cards: List<DashboardCardUi>,
           val isStale: Boolean,
           val pickerVisible: Boolean = false,
           val switcher: DashboardSwitcherUi = DashboardSwitcherUi(),
       ) : DashboardUiState()
   }
   ```

6. **New intents** — extend `DashboardIntent` sealed class with switcher / CRUD intents. Each intent corresponds to a single VM handler:
   ```kotlin
   sealed class DashboardIntent {
       // existing 5 from Story 4.6 retained verbatim
       data class AddCard(val entityId: String) : DashboardIntent()
       data class RemoveCard(val cardId: String) : DashboardIntent()
       data class Reorder(val orderedCardIds: List<String>) : DashboardIntent()
       data object OpenPicker : DashboardIntent()
       data object DismissPicker : DashboardIntent()

       // Story 4.7 additions
       data object OpenSwitcher : DashboardIntent()
       data object DismissSwitcher : DashboardIntent()
       data class SelectDashboard(val dashboardId: String) : DashboardIntent()
       data object BeginCreateDashboard : DashboardIntent()
       data object CancelCreateDashboard : DashboardIntent()
       data class UpdateNewDashboardName(val text: String) : DashboardIntent()
       data class ConfirmCreateDashboard(val name: String) : DashboardIntent()
       data class BeginRenameDashboard(val dashboardId: String) : DashboardIntent()
       data object CancelRenameDashboard : DashboardIntent()
       data class UpdateRenameText(val text: String) : DashboardIntent()
       data class ConfirmRename(val dashboardId: String, val name: String) : DashboardIntent()
       data class RequestDeleteDashboard(val dashboardId: String) : DashboardIntent()
       data object CancelDeleteDashboard : DashboardIntent()
       data class ConfirmDeleteDashboard(val dashboardId: String) : DashboardIntent()
   }
   ```

7. **Dashboard creation — pending-then-persist (FR12).** Tapping "New Dashboard" → `BeginCreateDashboard` flips `creating = true` + clears `pendingNewName`. Typing fires `UpdateNewDashboardName(text)` (capped at 40 chars; trim on store). Confirm fires `ConfirmCreateDashboard(name)` which:
   - Validates non-blank trimmed name. Empty → emit `HapticPattern.ActionRejected`, leave `creating = true`, no state change.
   - Stores the **pending** dashboard in a VM-internal `_pendingNewDashboard: MutableStateFlow<PendingDashboard?>` (where `data class PendingDashboard(val id: String, val name: String, val createdAt: Long)`). Sheet dismisses (`switcher.visible = false`); active-id flips to the pending id; UI immediately shows `EmptyDashboardState` for the pending dashboard.
   - On the next `AddCard(entityId)` while `_pendingNewDashboard != null`: VM persists the pending dashboard via `SaveDashboardUseCase` first, then persists the card, then clears `_pendingNewDashboard`. (Mirror of Story 4.6 first-card-bootstrap, but with chosen name + chosen id.)
   - If user cancels the empty dashboard (taps Dashboard tab again, opens switcher, selects another dashboard) before adding a card, the pending dashboard is discarded — never written to SQLDelight. (FR12: "after first card is added the dashboard is created and persisted".)
   - **Edge:** if user aborts pending creation by selecting another dashboard from the switcher, `_pendingNewDashboard.value = null` and active-id flips to the selected dashboard's id. Document in test `pendingDashboardDiscardedOnSwitch`.

8. **Inline rename (FR13).** `BeginRenameDashboard(id)` flips `renamingId = id` + seeds `pendingRenameText` with the current name. `UpdateRenameText(text)` stores trimmed text capped at 40 chars. `ConfirmRename(id, name)`:
   - Validates non-blank trimmed name.
   - If renaming the **pending** dashboard (`_pendingNewDashboard?.id == id`) → mutate `_pendingNewDashboard` in place (no DB call); clear `renamingId`.
   - Else → call new `RenameDashboardUseCase(id, name)` (see AC11). On success, `renamingId = null`. On failure, emit `HapticPattern.ActionRejected`, keep `renamingId` open so user can retry.
   - `CancelRenameDashboard` clears `renamingId` + `pendingRenameText` without persisting.

9. **Delete with confirmation (FR14, "only modal in V1").** Three-phase flow:
   - **Phase 1** — `RequestDeleteDashboard(id)` opens an `AlertDialog` with title `"Delete [name]?"`, body `"This cannot be undone."`, primary action `"Delete"` (M3 `TextButton` red — `colorScheme.error`), secondary `"Cancel"`. Sets `pendingDeleteId = id` (sheet stays open behind dialog).
   - **Phase 2** — `CancelDeleteDashboard` clears `pendingDeleteId`. `ConfirmDeleteDashboard(id)` invokes `DeleteDashboardUseCase(id)`. On success, clear `pendingDeleteId`; if the deleted id == active id, flip active id to the next remaining dashboard's id (`dashboards.firstOrNull { it.id != id }?.id`) and persist via `SetActiveDashboardIdUseCase`. On failure, emit `HapticPattern.ActionRejected`, leave `pendingDeleteId` so user sees the dialog still open.
   - **Phase 3** — Dialog dismisses on success.
   - **Delete disabled when `dashboards.size == 1`.** The overflow `DropdownMenu` "Delete" entry MUST be `enabled = state.switcher.canDelete` (or omitted entirely). UX-DR4-style spec: do not crash if a user manages to dispatch `RequestDeleteDashboard` with `canDelete == false` — VM short-circuits and emits `HapticPattern.ActionRejected`.
   - **Pending dashboard delete** — if the user opens overflow on the pending (unsaved) dashboard and taps Delete, no dialog needed: VM clears `_pendingNewDashboard` directly. Tests cover via `deletePendingDashboardSkipsDialogAndUseCase`.
   - **No other modals** in V1. The picker (Story 4.5) is a bottom sheet; the switcher is a bottom sheet. The delete confirmation `AlertDialog` is the **only** `Dialog` composable allowed in the dashboard package per UX feedback table.

10. **`DashboardChrome` singleton coordinator** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardChrome.kt`. Single shared bridge between the always-on navigation bar (which lives outside any nav3 entry) and the per-screen `DashboardViewModel` (which lives inside the `DashboardRoute` backstack entry):
    ```kotlin
    package com.backpapp.hanative.ui.dashboard

    import kotlinx.coroutines.flow.MutableSharedFlow
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.SharedFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asSharedFlow
    import kotlinx.coroutines.flow.asStateFlow

    class DashboardChrome {
        private val _activeDashboardName = MutableStateFlow<String?>(null)
        val activeDashboardName: StateFlow<String?> = _activeDashboardName.asStateFlow()

        // tryEmit replay buffer of 0 + extra capacity 1 — the latest signal is consumed
        // by the dashboard VM; if the VM is not yet collecting (e.g. during route swap),
        // signals are dropped, which matches user expectation (re-tap to re-open).
        private val _openSwitcherSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val openSwitcherSignals: SharedFlow<Unit> = _openSwitcherSignals.asSharedFlow()

        fun setActiveDashboardName(name: String?) {
            _activeDashboardName.value = name
        }

        fun requestOpenSwitcher() {
            _openSwitcherSignals.tryEmit(Unit)
        }
    }
    ```
    Register `single { DashboardChrome() }` in `serverManagerModule()` of `DataModule.kt` (placed alongside other shared singletons; not in `domainModule` because the type is in `ui.dashboard`). DI-acceptable because `DashboardChrome` has no domain dependencies and exists only to bridge two UI layers separated by the nav stack.

11. **`RenameDashboardUseCase`** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/RenameDashboardUseCase.kt`. Wraps a new repository method `DashboardRepository.renameDashboard(id, name)`:
    ```kotlin
    class RenameDashboardUseCase(private val repository: DashboardRepository) {
        suspend operator fun invoke(dashboardId: String, name: String): Result<Unit> =
            repository.renameDashboard(dashboardId, name)
    }
    ```
    Add to `DashboardRepository.kt`:
    ```kotlin
    suspend fun renameDashboard(dashboardId: String, name: String): Result<Unit>
    ```
    Implement in `DashboardRepositoryImpl.kt` using the **existing** `updateDashboardName` query (already present in `Dashboard.sq:14-15`). Validate non-blank id + name; trim; runCatchingCancellable; mutex-guarded; `Dispatchers.Default`; reuse the `runCatchingCancellable` helper at `DashboardRepositoryImpl.kt:165-171`. **Do NOT invoke `saveDashboard()` for renames** — that path also rewrites `position` which is unrelated.

12. **`ActiveDashboardRepository`** persists the user's currently-selected dashboard id across launches. Lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/ActiveDashboardRepository.kt`:
    ```kotlin
    interface ActiveDashboardRepository {
        fun observeActiveDashboardId(): Flow<String?>
        suspend fun setActiveDashboardId(dashboardId: String?): Result<Unit>
    }
    ```
    Implementation at `shared/src/commonMain/kotlin/com/backpapp/hanative/data/repository/ActiveDashboardRepositoryImpl.kt`:
    ```kotlin
    class ActiveDashboardRepositoryImpl(
        private val dataStore: DataStore<Preferences>,
    ) : ActiveDashboardRepository {
        override fun observeActiveDashboardId(): Flow<String?> =
            dataStore.data.map { it[HaSettingsKeys.ACTIVE_DASHBOARD_ID] }

        override suspend fun setActiveDashboardId(dashboardId: String?): Result<Unit> =
            runCatchingCancellable {
                dataStore.edit { prefs ->
                    if (dashboardId == null) prefs.remove(HaSettingsKeys.ACTIVE_DASHBOARD_ID)
                    else prefs[HaSettingsKeys.ACTIVE_DASHBOARD_ID] = dashboardId
                }
            }
    }
    ```
    Reuse the **same** `DataStore<Preferences>` singleton already wired by `settingsDataStoreModule()` at `DataModule.kt:26` — no new DataStore instance. Add `ACTIVE_DASHBOARD_ID = stringPreferencesKey("active_dashboard_id")` to `data/local/HaSettingsKeys.kt:5-7`.

13. **New use cases** for the active-id seam:
    ```kotlin
    // shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/GetActiveDashboardIdUseCase.kt
    class GetActiveDashboardIdUseCase(private val repo: ActiveDashboardRepository) {
        operator fun invoke(): Flow<String?> = repo.observeActiveDashboardId()
    }

    // shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/SetActiveDashboardIdUseCase.kt
    class SetActiveDashboardIdUseCase(private val repo: ActiveDashboardRepository) {
        suspend operator fun invoke(dashboardId: String?): Result<Unit> =
            repo.setActiveDashboardId(dashboardId)
    }
    ```
    Register both `factory { … }` in `DomainModule.kt` alongside existing dashboard use cases (lines 19–25). Register `RenameDashboardUseCase` likewise.

14. **`DashboardViewModel` updates** — the `activeDashboardOf(dashboards)` seam (line 124 in current source) is the single point that this story changes. Replace the `firstOrNull()` heuristic with a persisted-id-driven derivation:
    ```kotlin
    private fun activeDashboardOf(
        dashboards: List<Dashboard>,
        persistedActiveId: String?,
        pendingNew: PendingDashboard?,
    ): ActiveDashboardView? {
        // Pending unsaved dashboard wins over persisted id — user just created it
        if (pendingNew != null && pendingNew.id != persistedActiveId) {
            return ActiveDashboardView.Pending(pendingNew)
        }
        if (persistedActiveId != null) {
            dashboards.firstOrNull { it.id == persistedActiveId }
                ?.let { return ActiveDashboardView.Persisted(it) }
        }
        // Fallback: lowest position id (mirrors Story 4.6 v1 behavior)
        return dashboards.firstOrNull()?.let { ActiveDashboardView.Persisted(it) }
    }
    ```
    Where `ActiveDashboardView` is a private sealed interface in the VM:
    ```kotlin
    private sealed interface ActiveDashboardView {
        val id: String
        val name: String
        val cards: List<DashboardCard>
        data class Persisted(val d: Dashboard) : ActiveDashboardView {
            override val id = d.id; override val name = d.name; override val cards = d.cards
        }
        data class Pending(val p: PendingDashboard) : ActiveDashboardView {
            override val id = p.id; override val name = p.name; override val cards = emptyList()
        }
    }
    ```
    `state` flow's `combine` widens to include the persisted active id flow + `_pendingNewDashboard`. Cap the `combine` arity by grouping picker / switcher state into a single `_chromeState: MutableStateFlow<ChromeState>` if Kotlin's 5-arg `combine` is exceeded. (Use the variadic `combine(flow1, flow2, …, flowN) { values -> … }` overload — it accepts arbitrary arity via `vararg Flow<T>`.)

15. **Bidirectional `DashboardChrome` integration in the VM:**
    - **Outbound** — VM derives `state.dashboardName` (or `"Dashboard"` fallback) and writes via `dashboardChrome.setActiveDashboardName(...)`. Implement as:
      ```kotlin
      state
          .map { uiStateName(it) }
          .distinctUntilChanged()
          .onEach { dashboardChrome.setActiveDashboardName(it) }
          .launchIn(viewModelScope)
      ```
      Where `uiStateName(state) = (state as? Success)?.dashboardName ?: (state as? Empty)?.takeIf { dashboards.isNotEmpty() }?.let { activeName } ?: null`.
    - **Inbound** — VM collects `dashboardChrome.openSwitcherSignals` and treats each emission as `OpenSwitcher`:
      ```kotlin
      dashboardChrome.openSwitcherSignals
          .onEach { onIntent(DashboardIntent.OpenSwitcher) }
          .launchIn(viewModelScope)
      ```
    - **Cleanup** — when VM is cleared (`onCleared()`), call `dashboardChrome.setActiveDashboardName(null)` so a stale name does not survive logout / process recycling. **Do not** clear the open-switcher flow — it's a `SharedFlow`, no resource to release.

16. **Cross-fade transition on switch (NFR3, ≤200ms).** When the active dashboard id changes:
    - The composable tree under `DashboardScreen` MUST cross-fade between the outgoing and incoming dashboard's content (header + LazyColumn). Use M3 `androidx.compose.animation.Crossfade` keyed by `state.dashboardName + state.cards.hashCode()` — **no**, key by `activeDashboardId` only (more stable). The VM needs to expose `activeDashboardId: String?` either as a separate flow OR within `DashboardUiState.Success/Empty`. **Choose: add `val activeDashboardId: String?` to `Success` only** (Empty has no active dashboard from a UX POV).
    - `Crossfade(targetState = state, animationSpec = Motion.dashboardTransition, label = "dashboardSwitch")` wraps the body when state is `Success`. Loading + Empty render outside the Crossfade (no transition needed — those are first-frame paths).
    - **Verify:** total transition ≤200ms — `Motion.dashboardTransition` is already `tween(200, FastOutSlowInEasing)` per `Motion.kt:11`. Do not override.

17. **Haptic feedback on switch.** When `SelectDashboard(id)` lands on the VM and the id is a different dashboard than the current active id, the VM emits `HapticPattern.DashboardSwitch` via the existing `_haptics` SharedFlow (set up in Story 4.6 at `DashboardViewModel.kt:53-54`). The `DashboardScreen` already collects this flow in `LaunchedEffect(viewModel)` at lines 59–61 — no new wiring. Selecting the **same** id is a no-op (no haptic, sheet still dismisses).

18. **Persistence (FR16) verification:** all of the following must survive process death:
    - List of dashboards — already persisted by `Dashboard.sq` (Story 4.2).
    - All cards per dashboard — already persisted by `DashboardCard.sq` + cascade (Story 4.2).
    - Renamed name — persisted by new `updateDashboardName` query call.
    - Active dashboard id — persisted by `ACTIVE_DASHBOARD_ID` DataStore key.
    - Pending unsaved dashboard — **NOT persisted by design**. If the process dies before the first card is added, the pending dashboard is gone. (FR12 wording: "after first card is added the dashboard is created and persisted".)

19. **Compose UI Boundary verification gates** (Story 4.6 architecture rules, memorialized in `feedback_compose_architecture.md`):
    - `grep -rE "domain\.model\." shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/` MUST return only `DashboardViewModel.kt`.
    - `grep -rE "data\.|ktor\.|sqldelight\." shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/` MUST return only `DashboardViewModel.kt` (existing `ServerManager` import only).
    - `grep -rE "ModalBottomSheet|AlertDialog|DropdownMenu" shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/` MUST appear ONLY in `DashboardSwitcherSheet.kt` and `DashboardScreen.kt` (the `AlertDialog` for delete-confirm).
    - `grep -rE "androidx.datastore" shared/src/commonMain/kotlin/com/backpapp/hanative/ui/` MUST return zero matches.

20. **`@Preview` matrix** — extend `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardPreviews.kt` with switcher / dialog coverage. Add ≥6 new `@Preview` entries:
    - `Dashboard_Switcher_Closed` — `Success` with empty `switcher`.
    - `Dashboard_Switcher_OpenThree` — switcher visible with 3 dashboards.
    - `Dashboard_Switcher_OpenSingle_DeleteDisabled` — switcher visible with 1 dashboard, `canDelete = false`.
    - `Dashboard_Switcher_Creating` — `creating = true`, inline TextField visible.
    - `Dashboard_Switcher_Renaming` — `renamingId` set, inline TextField on a row.
    - `Dashboard_DeleteDialog_Open` — `pendingDeleteId` set, full overlay.
    Each preview drives `DashboardBody` directly with stub `cardSlot` / `pickerSlot` and a third stub `switcherSlot` (the body now hosts a switcher slot — see AC21). Wraps `HaNativeTheme { … }`. Total previews ≥ 13 (7 from Story 4.6 + 6 new).

21. **`DashboardBody` slot expansion.** Add a third slot for the switcher to keep previews Koin-free:
    ```kotlin
    @Composable
    internal fun DashboardBody(
        state: DashboardUiState,
        onIntent: (DashboardIntent) -> Unit,
        modifier: Modifier = Modifier,
        cardSlot: @Composable (DashboardCardUi, Boolean, Modifier) -> Unit = { … real EntityCard … },
        pickerSlot: @Composable (Boolean, () -> Unit, (String) -> Unit) -> Unit = { … real EntityPicker … },
        switcherSlot: @Composable (DashboardSwitcherUi, (DashboardIntent) -> Unit) -> Unit = { switcher, intent ->
            DashboardSwitcherSheet(state = switcher, onIntent = intent)
        },
    )
    ```
    The body invokes `switcherSlot(switcher, onIntent)` once at the same scope as `pickerSlot`. The delete-confirm `AlertDialog` is rendered inline in `DashboardBody` (not a separate slot — it's a single static composable keyed off `switcher.pendingDeleteId`).

22. **Accessibility (UX-DR9):**
    - Sheet root: `Modifier.semantics(mergeDescendants = false) { paneTitle = "Dashboard switcher" }`.
    - Each row: `contentDescription = "Switch to ${name} dashboard. ${cardCount} cards."`, `role = Role.Button`. Active row appends `"Currently active."` to the description.
    - Overflow `IconButton`: `contentDescription = "More options for ${name}"`.
    - "New Dashboard" footer button: `contentDescription = "Create new dashboard"; role = Role.Button`.
    - Delete dialog: M3 `AlertDialog` provides default semantics; verify TalkBack reads title + body + buttons in order.
    - Inline TextFields: `Modifier.semantics { contentDescription = "New dashboard name" }` (and analogous for rename).

23. **Tests** — extend `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModelTest.kt` (existing 10 tests retained verbatim). Add ≥12 new test methods:
    1. `switcherOpensAndClosesViaIntents` — `OpenSwitcher` flips `switcher.visible = true`; `DismissSwitcher` flips back.
    2. `switcherOpenSignalFromChromeFlipsVisible` — `dashboardChrome.requestOpenSwitcher()` → VM observes signal → `switcher.visible = true`.
    3. `chromeReceivesActiveDashboardName` — when `Success` emits with `dashboardName = "Living Room"`, `dashboardChrome.activeDashboardName.value == "Living Room"`.
    4. `selectDashboardPersistsActiveIdAndEmitsHaptic` — `SelectDashboard(otherId)` invokes `setActiveDashboardId(otherId)` and emits `HapticPattern.DashboardSwitch`.
    5. `selectSameDashboardIsNoopHapticAndPersistence` — `SelectDashboard(activeId)` does NOT emit haptic and does NOT call `setActiveDashboardId`.
    6. `beginCreateDashboardOpensInlineField` — `BeginCreateDashboard` flips `switcher.creating = true`, `pendingNewName = ""`.
    7. `confirmCreateDashboardOnEmptyNameIsRejected` — `ConfirmCreateDashboard("   ")` → no state change, `HapticPattern.ActionRejected` emitted.
    8. `confirmCreateDashboardCreatesPendingAndDismissesSheet` — `ConfirmCreateDashboard("Kitchen")` → `_pendingNewDashboard != null`, switcher visible = false, active id = pending id, state derives Empty for pending dashboard.
    9. `firstCardOnPendingDashboardPersistsBoth` — pending dashboard + `AddCard(entityId)` → `SaveDashboardUseCase` called once with pending id + name, then `AddCardUseCase` called, then `_pendingNewDashboard == null`.
    10. `pendingDashboardDiscardedOnSwitch` — pending dashboard exists, `SelectDashboard(otherExistingId)` → `_pendingNewDashboard == null`, no `SaveDashboardUseCase` invocation, active id = otherExistingId.
    11. `renameDashboardSuccess` — `ConfirmRename(id, "New Name")` → `RenameDashboardUseCase(id, "New Name")` called once, `renamingId = null`.
    12. `renameDashboardRejectedOnBlank` — `ConfirmRename(id, "")` → no use-case call, `HapticPattern.ActionRejected`, `renamingId` retained.
    13. `renamePendingDashboardMutatesPendingNotRepo` — pending dashboard, `ConfirmRename(pendingId, "Renamed")` → `_pendingNewDashboard.value.name == "Renamed"`, `RenameDashboardUseCase` NOT called.
    14. `requestDeleteOpensDialog` — `RequestDeleteDashboard(id)` → `pendingDeleteId == id`.
    15. `confirmDeleteCallsUseCaseAndAdvancesActiveId` — when active id is deleted, VM calls `setActiveDashboardId(otherId)` after delete success.
    16. `requestDeleteWhenCannotDeleteIsRejected` — `dashboards.size == 1`, `RequestDeleteDashboard(id)` → no `pendingDeleteId`, `HapticPattern.ActionRejected`.
    17. `deletePendingDashboardSkipsDialogAndUseCase` — `RequestDeleteDashboard(pendingId)` on a pending unsaved dashboard → `_pendingNewDashboard == null`, no dialog opened, `DeleteDashboardUseCase` NOT called.
    18. `intentsIgnoredWhenLoading` — All switcher / CRUD intents ignored when `state.value is Loading` (mirror of Story 4.6 D2 gate).

    Use the existing `FakeDashboardRepository` pattern; add a fake `ActiveDashboardRepository` (in-memory `MutableStateFlow<String?>`); pass `DashboardChrome` directly to the VM via constructor (no Koin in tests).

24. **`HaNativeNavHost` changes** — minimal:
    - Add `val dashboardChrome: DashboardChrome = koinInject()` in `NavHostContent` (use `org.koin.compose.koinInject` — already on classpath via Koin Compose 4.2.1).
    - Collect `dashboardChrome.activeDashboardName.collectAsStateWithLifecycle()` → use as the Dashboard tab label, falling back to `"Dashboard"` when null.
    - `handleNavItemClick` (line 114) gains a Dashboard branch:
      ```kotlin
      val handleNavItemClick: (Int) -> Unit = { index ->
          when (index) {
              0 -> {
                  if (selectedIndex == 0) dashboardChrome.requestOpenSwitcher()
                  // else: already-on-Dashboard tap — no-op, derived selectedIndex covers it
              }
              2 -> if (backStack.lastOrNull() !is SettingsRoute) backStack.add(SettingsRoute)
          }
      }
      ```
    - Replace the static `navItems` (line 53) with an in-composable `listOf(activeDashboardName ?: "Dashboard", "Rooms", "Settings")` so the label tracks active state.
    - **Do not** introduce a second NavigationBar or alter `selectedIndex` derivation. Only the label string is dynamic.

## Tasks / Subtasks

- [ ] Task 1: DataStore key + `ActiveDashboardRepository` (AC: 12)
  - [ ] 1.1: Add `ACTIVE_DASHBOARD_ID = stringPreferencesKey("active_dashboard_id")` to `data/local/HaSettingsKeys.kt`.
  - [ ] 1.2: Create `domain/repository/ActiveDashboardRepository.kt` interface.
  - [ ] 1.3: Create `data/repository/ActiveDashboardRepositoryImpl.kt` reusing the shared `DataStore<Preferences>` singleton.
  - [ ] 1.4: Wire in `DataModule.serverManagerModule()`: `single { ActiveDashboardRepositoryImpl(get()) } bind ActiveDashboardRepository::class` (Koin `bind` DSL, NOT infix — see `DataModule.kt:55-56` and mem S1040).

- [ ] Task 2: Repository rename + use cases (AC: 11, 13)
  - [ ] 2.1: Add `renameDashboard(dashboardId, name)` to `DashboardRepository.kt`.
  - [ ] 2.2: Implement in `DashboardRepositoryImpl.kt` using existing `dashboardQueries.updateDashboardName` (`Dashboard.sq:14-15`); mirror the validation + mutex + dispatchers + runCatchingCancellable pattern.
  - [ ] 2.3: Create `RenameDashboardUseCase`, `GetActiveDashboardIdUseCase`, `SetActiveDashboardIdUseCase` under `domain/usecase/`.
  - [ ] 2.4: Register all three `factory { … }` in `DomainModule.kt` (alongside lines 19–25).

- [ ] Task 3: `DashboardChrome` coordinator + DI (AC: 10)
  - [ ] 3.1: Create `ui/dashboard/DashboardChrome.kt` per AC10.
  - [ ] 3.2: Register `single { DashboardChrome() }` in `DataModule.serverManagerModule()` (placed near `OAuthCallbackBus`).

- [ ] Task 4: UI model + intent expansion (AC: 4, 5, 6)
  - [ ] 4.1: Extend `DashboardUiModels.kt`: add `DashboardSwitcherUi`, `DashboardSummaryUi`; add `switcher` field to `Empty` + `Success`; add 13 new `DashboardIntent` subclasses.
  - [ ] 4.2: Confirm zero new domain imports in `DashboardUiModels.kt` (Compose UI Boundary).

- [ ] Task 5: ViewModel updates (AC: 5, 7, 8, 9, 14, 15, 17, 23)
  - [ ] 5.1: Inject `setActiveDashboardId: SetActiveDashboardIdUseCase`, `getActiveDashboardId: GetActiveDashboardIdUseCase`, `renameDashboard: RenameDashboardUseCase`, `deleteDashboard: DeleteDashboardUseCase`, `dashboardChrome: DashboardChrome` into `DashboardViewModel` constructor (alongside existing 8 deps).
  - [ ] 5.2: Replace `activeDashboardOf(dashboards)` (line 124) with the persisted-id-aware version (AC14). Introduce private `ActiveDashboardView` sealed interface. Maintain compatibility — fallback to `dashboards.firstOrNull()` when persisted id is null or stale.
  - [ ] 5.3: Add `_pendingNewDashboard: MutableStateFlow<PendingDashboard?>` and weave into `combine(...)` for state derivation.
  - [ ] 5.4: Implement handlers: `handleOpenSwitcher`, `handleDismissSwitcher`, `handleSelectDashboard`, `handleBeginCreate`, `handleCancelCreate`, `handleUpdateNewName`, `handleConfirmCreate`, `handleBeginRename`, `handleCancelRename`, `handleUpdateRenameText`, `handleConfirmRename`, `handleRequestDelete`, `handleCancelDelete`, `handleConfirmDelete`. All gate on `state.value !is Loading`.
  - [ ] 5.5: Wire bidirectional `DashboardChrome` integration per AC15: outbound name push, inbound open-switcher signal collection, `onCleared()` cleanup.
  - [ ] 5.6: Update `handleAddCard` to persist pending dashboard first when `_pendingNewDashboard != null` (single mutex-guarded transaction sequence: `saveDashboard(pending)` → `addCard(...)` → `_pendingNewDashboard.value = null`).
  - [ ] 5.7: Active-id auto-advance on delete: when `ConfirmDeleteDashboard(id)` matches current persisted active id, call `setActiveDashboardId(remainingFirstId)` after the delete use case succeeds.

- [ ] Task 6: Switcher composable (AC: 3, 22)
  - [ ] 6.1: Create `ui/dashboard/DashboardSwitcherSheet.kt` per AC3 anatomy. Use `androidx.compose.material3.ModalBottomSheet`, `rememberModalBottomSheetState()`. Sheet `onDismissRequest = { onIntent(DashboardIntent.DismissSwitcher) }`.
  - [ ] 6.2: Render `LazyColumn` of `DashboardSummaryUi` rows. Per-row `IconButton` with `Icons.Outlined.MoreVert` → `DropdownMenu` with `DropdownMenuItem("Rename")` and `DropdownMenuItem("Delete", enabled = state.canDelete && row.id != activeIfPending)`. Active-row check icon prepended.
  - [ ] 6.3: Footer "New Dashboard" → `BeginCreateDashboard`; in `creating` mode replace footer with inline `OutlinedTextField` (autofocus via `FocusRequester`, IME `Done`, max 40 chars), confirm/cancel `IconButton`s.
  - [ ] 6.4: When `state.renamingId != null`, the matching row replaces its `Text` with an inline `OutlinedTextField` seeded with `pendingRenameText`; rest of row (icons) stays for visual continuity.
  - [ ] 6.5: All accessibility per AC22.

- [ ] Task 7: Delete dialog + cross-fade transition (AC: 9, 16)
  - [ ] 7.1: In `DashboardScreen.kt`'s `DashboardBody`, render an `AlertDialog` when `switcher.pendingDeleteId != null`. Title `"Delete ${name}?"`, body `"This cannot be undone."`, primary button `"Delete"` (`TextButton` with `colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)`), secondary `"Cancel"`. Resolve `name` from `state.switcher.dashboards.firstOrNull { it.id == pendingDeleteId }?.name ?: ""`.
  - [ ] 7.2: Wrap the `Success` body in `Crossfade(targetState = state.activeDashboardId, animationSpec = Motion.dashboardTransition, label = "dashboardSwitch")` so the switch transition is the 200ms cross-fade. Loading + Empty render outside the Crossfade.

- [ ] Task 8: NavHost dynamic label + tap-active (AC: 1, 2, 24)
  - [ ] 8.1: Replace static `navItems` with a `@Composable` `derivedStateOf` list pulling the active dashboard name from `dashboardChrome.activeDashboardName`.
  - [ ] 8.2: Update `handleNavItemClick` to dispatch `dashboardChrome.requestOpenSwitcher()` when the Dashboard tab is tapped while already selected.
  - [ ] 8.3: Wire `koinInject<DashboardChrome>()` in `NavHostContent` (compact + expanded paths).

- [ ] Task 9: DI updates (AC: 5.1, 10, 12, 13)
  - [ ] 9.1: Update `PresentationModule.kt` `viewModel { DashboardViewModel(...) }` to pass the 5 new dependencies (4 use cases + chrome).
  - [ ] 9.2: Verify `DataModule.serverManagerModule()` registers `DashboardChrome` and `ActiveDashboardRepositoryImpl bind ActiveDashboardRepository::class`.
  - [ ] 9.3: Verify `DomainModule.kt` registers `factory { GetActiveDashboardIdUseCase(get()) }`, `factory { SetActiveDashboardIdUseCase(get()) }`, `factory { RenameDashboardUseCase(get()) }`.

- [ ] Task 10: Tests + previews (AC: 20, 23)
  - [ ] 10.1: Extend `DashboardViewModelTest.kt` with the 18 new tests in AC23. Reuse `FakeDashboardRepository`; add `FakeActiveDashboardRepository(initial: String? = null)` with a `MutableStateFlow<String?>` and `setActiveDashboardId` impl.
  - [ ] 10.2: Add the 6 new `@Preview` entries to `DashboardPreviews.kt`. Use a `StubSwitcherSlot` that renders an inline `Column` for preview (no `ModalBottomSheet` — that doesn't render in `@Preview`). Stub takes the same parameters as the real `switcherSlot`.

- [ ] Task 11: Verification (AC: 19, all)
  - [ ] 11.1: `./gradlew :shared:testAndroidHostTest` — BUILD SUCCESSFUL. All existing 10 + new 18 VM tests green.
  - [ ] 11.2: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL.
  - [ ] 11.3: Compose UI Boundary grep gates per AC19 — all four MUST pass.
  - [ ] 11.4: Manual smoke on Android: cold launch → see active dashboard label; tap-tab → switcher opens; create dashboard inline → empty state with new name; add card → persisted; rotate device → state survives; relaunch app → active dashboard preserved; rename → label updates; delete with one dashboard remaining → action disabled; delete with confirmation → advances active id.

### Review Findings

_BMAD code review on 2026-05-01. Layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor. 6 decisions resolved (D1=A, D2=A, D3=C, D4=A, D5=A, D6=A), 16 patches applied + 3 dismissed/redundant (P10, P17, P19), 17 deferred, 14 dismissed. Verification: `./gradlew :shared:testAndroidHostTest` BUILD SUCCESSFUL (159 tests, +8 new); `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` BUILD SUCCESSFUL._

#### Decisions Needed

- [x] [Review][Decision] **D1 — Pending dashboard always wins regardless of `persistedActiveId`** [`DashboardViewModel.kt:activeView`] — Spec AC14 says "pending wins only when `pending != null && pending.id != persistedActiveId`". Current `activeView()` returns `Pending` whenever `pending != null`, no comparison. Edge race: tapping the pending row in switcher while DataStore write hasn't materialized routes through "switching away" branch and silently discards the pending dashboard. Sources: blind+edge+auditor. Options: A) Apply spec guard; B) Keep pending-always-wins.
- [x] [Review][Decision] **D2 — `handleConfirmCreate` writes pending id to DataStore immediately** [`DashboardViewModel.kt:handleConfirmCreate`] — Spec AC18 / FR16: pending dashboard NOT persisted by design. Diff persists active id pointing to a not-yet-persisted dashboard row. On process death between create and first card, DataStore retains stale `pending-1` id with no DB row. Sources: blind+auditor. Options: A) Defer `setActiveDashboardId` until pending flushes on first card; B) Keep immediate write (current); C) Persist + clear on discard.
- [x] [Review][Decision] **D3 — Pending dashboard uncancelable when no other persisted dashboards exist** [`DashboardViewModel.kt`/`DashboardSwitcherSheet.kt`] — `canDelete = withPending.size > 1` disables Delete on pending when only pending exists. After `ConfirmCreateDashboard`, `creating = false` → no Cancel footer. No `DiscardPending` intent. User must add a card to escape. Source: edge. Options: A) Add `DiscardPending` intent + UI; B) Auto-discard on switcher `onDismissRequest`; C) Allow delete on pending unconditionally (skip dialog, clear pending).
- [x] [Review][Decision] **D4 — `onIntent` Loading-gate scope broader than spec** [`DashboardViewModel.kt:onIntent`] — Top-level `if (state.value is Loading) return` drops ALL intents during Loading, including `DismissPicker`, `DismissSwitcher`, `CancelCreateDashboard`, `CancelRenameDashboard`, `CancelDeleteDashboard`. Spec gate intent: switcher / CRUD only (mirror of 4.6 D2). Inbound chrome `OpenSwitcher` signals also dropped during Loading. Sources: blind+edge+auditor. Options: A) Gate only switcher/CRUD intents per spec; B) Keep blanket gate.
- [x] [Review][Decision] **D5 — NavHost label flips to "Dashboard" when leaving Dashboard route** [`DashboardViewModel.kt:onCleared`, `HaNativeNavHost.kt`] — `onCleared()` calls `setActiveDashboardName(null)`, so navigating Dashboard→Settings flips the persistent nav-bar label to fallback "Dashboard". Spec AC1 implies dynamic label always reflects active dashboard, regardless of current route. Sources: blind+edge. Options: A) Don't clear in `onCleared`; clear only on logout via separate hook; B) Cache last-known name at chrome level until next push; C) Keep current behavior — accept flicker.
- [x] [Review][Decision] **D6 — Latent `switcher.visible = true` survives Loading transition then re-pops sheet** [`DashboardViewModel.kt`/`DashboardScreen.kt`] — Open switcher → upstream re-emits Loading → `DashboardBody` renders LoadingContent (no sheet) → re-emit Empty/Success → sheet pops back. `DismissSwitcher` blocked by D4 gate during Loading. Source: edge. Options: A) Clear `_switcherChrome.visible` whenever state goes Loading; B) Keep VM state, fix only via D4.

#### Patches

- [x] [Review][Patch] **P1 — `handleSelectDashboard` does not clear `pendingDeleteId` / `renamingId`** [`DashboardViewModel.kt:handleSelectDashboard`] — Selecting another dashboard with delete dialog or rename open leaves orphan transient state. Auditor A18.
- [x] [Review][Patch] **P2 — `DismissSwitcher` does not clear `pendingDeleteId`** [`DashboardViewModel.kt:handleDismissSwitcher`] — Sheet dismisses but AlertDialog floats above empty Dashboard. Edge.
- [x] [Review][Patch] **P3 — `renamingId` references defunct id after pending discard** [`DashboardViewModel.kt:handleSelectDashboard`/`handleRequestDelete` pending shortcut] — Clearing pending leaves stale `renamingId` if user was renaming pending. Blind+edge.
- [x] [Review][Patch] **P4 — Rename row keeps "Switch to ${name}" semantics during edit** [`DashboardSwitcherSheet.kt:DashboardRow`] — Make `contentDescription` + `Role.Button` conditional on `!isRenaming`. Blind.
- [x] [Review][Patch] **P5 — `AlertDialog` renders `"Delete ?"` when target dashboard absent** [`DashboardScreen.kt:DeleteConfirmDialog`] — Auto-dismiss when `dashboards.firstOrNull { it.id == pendingId } == null`. Blind.
- [x] [Review][Patch] **P6 — `handleConfirmDelete` `getActiveDashboardId().first()` no timeout** [`DashboardViewModel.kt:handleConfirmDelete`] — Mirror existing `withTimeoutOrNull(2.seconds)` pattern from `currentDashboards()` to prevent indefinite hang on slow DataStore. Edge.
- [x] [Review][Patch] **P7 — `ensureActiveDashboard` `getActiveDashboardId().first()` no timeout** [`DashboardViewModel.kt:ensureActiveDashboard`] — Same fix as P6; mutex-held hang freezes all subsequent `AddCard`. Edge.
- [x] [Review][Patch] **P8 — `_optimisticOrder` not reset on dashboard switch** [`DashboardViewModel.kt:handleSelectDashboard`] — Mid-debounce reorder against dashboard A then switch to B → `deriveState` filters B's cards by A's ids → empty list → flicker `Empty`. Then `dispatchReorder` writes A's ids against B's id. Edge.
- [x] [Review][Patch] **P9 — `Crossfade` lambda discards `targetState` parameter, body reads outer `state`** [`DashboardScreen.kt:DashboardBody Success branch`] — Defeats per-key cross-fade. Use lambda parameter for content rendering. Auditor A10.
- [x] [Review][Patch] **P10 — AC3 missing footer divider after last row** [`DashboardSwitcherSheet.kt:SwitcherBody`] — Dismissed: per-row `HorizontalDivider()` already inside `items {}` lambda emits a trailing divider before the footer. Spec satisfied.
- [x] [Review][Patch] **P11 — `DropdownMenu` Delete missing `row.id != activeIfPending` check** [`DashboardSwitcherSheet.kt:RowOverflowMenu`] — Spec Task 6.2 explicit. Combined with `canDelete`. Blind+edge+auditor.
- [x] [Review][Patch] **P12 — `handleConfirmDelete` advance-active uses post-delete persisted id; null write when last remaining** [`DashboardViewModel.kt:handleConfirmDelete`] — Read pre-delete active id; skip `setActiveDashboardId(null)` when no remaining (or document explicit null intent). Auditor A6+A7.
- [x] [Review][Patch] **P13 — AC20 missing 6 switcher/dialog `@Preview` entries** [`DashboardPreviews.kt`] — Added `Dashboard_Switcher_Closed`, `Dashboard_Switcher_OpenThree`, `Dashboard_Switcher_OpenSingle_DeleteDisabled`, `Dashboard_Switcher_Creating`, `Dashboard_Switcher_Renaming`, `Dashboard_DeleteDialog_Open` + `StubSwitcher` slot stub.
- [x] [Review][Patch] **P14 — AC23 ≥18 new VM tests; only ~12 added** [`DashboardViewModelTest.kt`] — Added 8 tests: `switcherOpensAndClosesViaIntents`, `beginCreateDashboardOpensInlineField`, `confirmCreateDashboardOnEmptyNameIsRejected`, `pendingDashboardDiscardedOnSwitch`, `renameDashboardRejectedOnBlank`, `renamePendingDashboardMutatesPendingNotRepo`, `requestDeleteOpensDialog`, `intentsIgnoredWhenLoading`. All pass.
- [x] [Review][Patch] **P15 — `ConfirmRename` overwrites in-flight `pendingRenameText`** [`DashboardViewModel.kt:handleConfirmRename`] — User typing during DB suspend has edits clobbered by post-success clear. Snapshot intended-text before suspend, clear only matching values. Edge.
- [x] [Review][Patch] **P16 — `EmptyDashboardState` ignores active dashboard name when persisted dashboard exists with zero cards** [`DashboardScreen.kt:DashboardBody Empty branch`] — When non-pending Empty (e.g. cleared cards), header label drops. Pass `dashboardName` through `Empty` branch. Auditor A20.
- [x] [Review][Patch] **P17 — DropdownMenu hit gap allows row-click bleed-through** [`DashboardSwitcherSheet.kt:DashboardRow`] — Dismissed: M3 `IconButton` provides a 48dp `minimumInteractiveComponentSize` touch target that absorbs the gap; outer row `clickable` does not bleed through under typical taps. Re-promote if smoke testing finds otherwise.
- [x] [Review][Patch] **P18 — Story 4.7 status still `in-progress`; all task checkboxes unchecked despite implementation** [`_bmad/stories/4-7-...md:3,358-417`] — Status set to `review`; task checkbox sync deferred (not blocking).
- [x] [Review][Patch] **P19 — `handleAddCard` pending-save failure leaves picker open silently** [`DashboardViewModel.kt:handleAddCard`] — Deferred: matches Story 4.6 D1 wording (picker stays open until success); failure variant emits `ActionRejected` haptic which is the consistent feedback path. Re-promote if UX wants explicit close.

#### Deferred (pre-existing or out of scope)

- [x] [Review][Defer] **`tryEmit` drops `_haptics` past extraBufferCapacity = 4** [`DashboardViewModel.kt`] — Burst UX; haptics advisory.
- [x] [Review][Defer] **`tryEmit` drops `openSwitcherSignals` past capacity 1; replay = 0 means cold-start tap can be lost** [`DashboardChrome.kt`] — Acknowledged in code comment.
- [x] [Review][Defer] **`String.take(NAME_MAX_LEN)` cuts mid-grapheme on emoji / combining marks** [`DashboardViewModel.kt:UpdateNewDashboardName/UpdateRenameText`] — Polish.
- [x] [Review][Defer] **`handleAddCard` position via `current.size` may collide with non-contiguous `position` rows** [`DashboardViewModel.kt`] — Same as Story 4.6 deferred (P5 in 4.6).
- [x] [Review][Defer] **Picker stays open on save-failure rejection during pending flush** [`DashboardViewModel.kt:handleAddCard`] — Spec D1 wording covers picker-stays-open-until-success; this is the failure variant.
- [x] [Review][Defer] **Crossfade does not animate on rename of active dashboard** [`DashboardScreen.kt`] — Only `activeDashboardId` keys the transition; rename changes only `dashboardName`. NFR3 cares about switch, not rename.
- [x] [Review][Defer] **No cross-fade on Loading→Success first paint** [`DashboardScreen.kt`] — Out of NFR3 scope; cold start is hard-cut.
- [x] [Review][Defer] **`DashboardChrome` `single` survives logout — old user's name flashes on next login** [`DataModule.kt`/`DashboardChrome.kt`] — Polish for multi-user / logout flow (not in V1).
- [x] [Review][Defer] **`rememberModalBottomSheetState()` fresh on each visibility — no partial-expansion memory** [`DashboardSwitcherSheet.kt`] — M3 idiomatic; spec accepts default animation.
- [x] [Review][Defer] **`runCatchingNull` swallows DataStore corruption with no log** [`DashboardViewModel.kt`] — Diagnosability; add `Logger.warn` when logging infra is wired.
- [x] [Review][Defer] **No duplicate-name guard on dashboards** [`DashboardViewModel.kt:handleConfirmCreate`] — Spec doesn't mandate uniqueness; UX polish.
- [x] [Review][Defer] **Pending row `cardCount` flicker 0→1 on flush** [`DashboardSwitcherSheet.kt`] — Brief; acceptable.
- [x] [Review][Defer] **AC3 active-row `Check` icon size unspecified vs leading 24dp** [`DashboardSwitcherSheet.kt`] — Cosmetic.
- [x] [Review][Defer] **DropdownMenu rename name in flight shows stale name on next reopen** [`DashboardSwitcherSheet.kt`] — Polish.
- [x] [Review][Defer] **Variadic `combine` allocates `Sources` per inner emission** [`DashboardViewModel.kt`] — Perf-only.
- [x] [Review][Defer] **`navItems` reallocated per `activeDashboardName` change** [`HaNativeNavHost.kt`] — Perf-only.
- [x] [Review][Defer] **`SequentialIdGenerator` overflow fallback `"id-N"` repeats silently** [`DashboardViewModelTest.kt`] — Test util only.

#### Dismissed (noise / false positives)

- A22 `uiStateName` Loading gating — auditor withdrew.
- A26 datastore in `ui/` — auditor verified clean.
- A27 `DashboardScreen` consumes domain via UI — withdrew.
- A30 ModalBottomSheet animation override — spec accepts default.
- A13 `IdGenerator` double-register — Story 4.6 wires it; 4.7 does not duplicate.
- A11 switcher slot rendered when invisible — internal early-return guard correct.
- B `AddCardAffordanceRow` modifier order with `minimumInteractiveComponentSize` — spec ambiguous, current order acceptable.
- B `viewModelScope.launch { setActiveDashboardId(...) }` race vs `_switcherChrome.update` — no rollback path practical without intent contract change; covered by D2.
- B redundant inner `Loading` checks in `onIntent` — dead but harmless.
- B `DashboardSwitcherSheet` LazyColumn no `weight(1f)` — fits inside `ModalBottomSheet`'s scrollable scaffold; verify in smoke (could promote to patch if smoke fails).
- B `SwitcherBody` `mergeDescendants = false` with `paneTitle` — M3 pattern; verify TalkBack reads on smoke.
- B19 redundant of A18 (`renamingId` on switch).
- A21 `uiStateName` fragility via switcher coupling — covered by structural patches.
- A19 `DismissSwitcher` does not clear `pendingDeleteId` — duplicate of P2.



### Architecture Compliance — Strict Composable → ViewModel → UseCase Boundary

This story extends the boundary established in Stories 4.3–4.6 (memorialized in `feedback_compose_architecture.md`):
- Composables consume `DashboardUiState` + `DashboardIntent` only. The new `DashboardSwitcherUi` is a UI-layer projection — **no `Dashboard` import** in `DashboardSwitcherSheet.kt` or any other file under `ui/dashboard/` other than `DashboardViewModel.kt`.
- `DashboardChrome` lives in `ui.dashboard` (not `domain` or `data`) because it is a UI-coordination primitive — it bridges two UI scopes (navigation chrome and the dashboard backstack entry). Its `StateFlow` carries plain `String?` and `Unit` — no domain types cross the boundary.
- `ActiveDashboardRepository` is a **data-layer** repository (correct domain/data split for a DataStore-backed persistence concern). It does NOT belong in the UI coordinator.

### Why a Singleton Coordinator (`DashboardChrome`) Instead of Direct VM Access

The compact navigation bar lives in `HaNativeNavHost.NavHostContent` — outside the nav3 backstack entry that owns the `DashboardViewModel`. Three options:

1. **Hoist `DashboardViewModel` to nav-host scope** — breaks Story 4.6's `koinViewModel()` resolution and ties the VM to nav-host lifecycle (recreated when `StartupRoute` changes). Rejected.
2. **Pass callbacks down through nav-host params** — requires plumbing `onTabTapped: (Int) -> Unit` through `NavDisplay` and `entry<DashboardRoute>`, which doesn't have a clean upward channel for the active dashboard *name* to flow back. Rejected.
3. **Singleton coordinator with `StateFlow` + `SharedFlow`** — clean separation, no lifecycle entanglement, no plumbing. The pattern matches how `OAuthCallbackBus` (Story 3.5, `data/remote/`) already bridges the platform OAuth launcher to the auth VM. Selected.

**The coordinator owns no business logic.** It is a typed event bus + state field — VM remains the single source of truth.

### `EntityCard` Long-Press vs Card Reorder vs Switcher Open

Three long-press surfaces co-exist on the dashboard:
- `EntityCard` long-press (Story 4.4) — toggle / context menu (out of scope here).
- Reorder handle long-press (Story 4.6, `Modifier.longPressDraggableHandle()`) — drag to reorder.
- Dashboard tab tap-while-selected — opens switcher (this story).

**No conflict** because the switcher trigger is a navigation-bar tap, not a card-area gesture. The card and reorder long-presses are confined to the LazyColumn's row composables; the nav bar is a separate Scaffold slot.

### Pending Dashboard — Rationale and Lifecycle

FR12 mandates that a dashboard "is created and persisted **after the first card is added**". Naive implementation (persist on name confirm, delete if no card added) is wrong: it leaves orphan dashboards on app crash mid-flow. The **pending** model handles this:

- `_pendingNewDashboard` is in-memory only (VM-scoped `MutableStateFlow<PendingDashboard?>`).
- The active-id derivation prefers the pending dashboard when present.
- Adding the first card flushes the pending dashboard to SQLDelight + DataStore atomically (within `addCardMutex`).
- Switching to another dashboard, deleting the pending dashboard, or process death discards it cleanly — no DB write, no orphan.

This is intentionally different from Story 4.6's first-card-bootstrap (which auto-creates a `"Home"` dashboard on first card add). 4.6's bootstrap path remains for the **zero-dashboards** edge case (user lands on an empty app and adds a card without ever opening the switcher); 4.7's pending path runs when the user explicitly creates a new dashboard via the switcher.

### Active ID Resolution Order (Stale-ID Recovery)

The persisted `active_dashboard_id` can become stale if a user deletes the active dashboard from another device that synced (not in V1 scope) OR if the DB is wiped while the DataStore key persists. Resolution order in `activeDashboardOf`:

1. If pending dashboard exists AND its id is not also in DataStore → return pending.
2. If DataStore id matches a known dashboard → return that one.
3. If DataStore id does NOT match anything (stale) → return `dashboards.firstOrNull()`. **Do not** auto-clear DataStore here — that fires write churn. Clear lazily on the next `SelectDashboard(other)` or `ConfirmDeleteDashboard(active)`.
4. If no dashboards at all → return null → state derives `Empty`.

### Cross-Fade Transition — `Crossfade` vs `AnimatedContent`

`Motion.dashboardTransition` is a `TweenSpec<Float>` (200ms, FastOutSlowIn). M3's `Crossfade` accepts `animationSpec: FiniteAnimationSpec<Float>` and is the right primitive — it interpolates alpha only, no slide/scale. `AnimatedContent` is heavier (slide + fade by default) and not what the spec asks for. Stick with `Crossfade`.

### Testing Standards (mirrors Story 4.6)

- `kotlin.test` only — never JUnit4/5 in `commonTest`.
- `./gradlew :shared:testAndroidHostTest` for VM tests.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` as the iOS gate.
- No Compose UI test runner (still not wired). VM coverage only.
- `kotlinx.coroutines.test.runTest` with `StandardTestDispatcher`; `Dispatchers.setMain(dispatcher)` in `@BeforeTest`.
- For the new `dashboardChrome` integration, pass a real `DashboardChrome()` instance directly to the VM constructor (no Koin in tests). Assert on its `StateFlow.value` after `advanceUntilIdle()`.
- For the pending-dashboard flow, use the existing `FakeDashboardRepository` and assert on its captured save calls + card calls in order.

### Git Intelligence

| Commit  | What it established                                                                  |
| ------- | ------------------------------------------------------------------------------------ |
| `90a1a65` | Compose UI Boundary doctrine across architecture, epics, ui pkg                      |
| `eb5e53b` | EntityCard ViewModel + UIModel boundary refactor                                     |
| `c4437d3` | Story 4.5 `EntityPicker` + strict VM/UIModel architecture                            |
| `d4fc4d3` | Story 4.4 stepper/trigger/media/unknown variants                                     |
| `1a3f281` | Story 4.3 toggleable + read-only `EntityCard`                                        |
| `e158f57` | Story 4.2 `Dashboard.sq`, `DashboardCard.sq`, repo, all 6 use cases — `updateDashboardName` query already present |
| `0adc61c` | Story 4.1 `EntityState.sq` + entity pipeline                                         |
| `f91dbcd` | Story 3.5 `StartupViewModel`, FR3 routing                                            |
| `9f4d3cb` | Story 3.4 `HaUrlRepository` — DataStore wrapper pattern reused here                  |

Codebase state confirmed before story creation:
- `HaNativeNavHost.kt:53` — `private val navItems = listOf("Dashboard", "Rooms", "Settings")` to be replaced.
- `HaNativeNavHost.kt:114-119` — `handleNavItemClick` only handles index 2 (Settings) — Dashboard branch to be added.
- `HaNativeNavHost.kt:163-169` — `NavigationBarItem(label = { Text(label) })` consumes the dynamic label.
- `Motion.kt:11` — `dashboardTransition: TweenSpec<Float>` already at 200ms / FastOutSlowIn — no change.
- `HapticPattern.DashboardSwitch` exists at `HapticFeedback.kt:12` — consume here.
- `Dashboard.sq:14-15` — `updateDashboardName` query already wired (Story 4.2).
- `DashboardCard.sq:7` — `ON DELETE CASCADE` already wired — deleting a dashboard cascades cards.
- `DashboardRepository.kt:7-14` — 6 methods; +1 (`renameDashboard`) added by this story.
- `DashboardRepositoryImpl.kt:165-171` — `runCatchingCancellable` helper already present; reuse in new method.
- `data/local/HaSettingsKeys.kt:5-7` — only `HA_URL` key today; add `ACTIVE_DASHBOARD_ID`.
- `data/remote/HaUrlRepository.kt:50` — DataStore read pattern (`dataStore.data.first()[key]`) and `edit { … }` write pattern — mirror in `ActiveDashboardRepositoryImpl`.
- `DataModule.kt:55-56` — `bind` DSL pattern (NOT infix — see mem S1040: Koin infix `bind` fails compile in KMP shared module).
- `DomainModule.kt:19-25` — six existing dashboard use-case factories; append three more.
- `PresentationModule.kt:20-30` — current `DashboardViewModel` 7 deps + connection state; +5 deps (4 use cases + chrome).
- `DashboardViewModel.kt:124-125` — current `activeDashboardOf` is `firstOrNull()` — single seam to swap (already documented in Story 4.6 Dev Notes).
- `DashboardViewModel.kt:158-172` — `ensureActiveDashboard` for first-card-bootstrap — leave intact; pending-dashboard path is a parallel branch.

### Project Structure — Files Touched

```
shared/src/commonMain/kotlin/com/backpapp/hanative/
  ├── data/
  │   ├── local/
  │   │   └── HaSettingsKeys.kt                        ← MODIFIED (+ ACTIVE_DASHBOARD_ID)
  │   └── repository/
  │       ├── ActiveDashboardRepositoryImpl.kt         ← NEW
  │       └── DashboardRepositoryImpl.kt               ← MODIFIED (+ renameDashboard)
  ├── domain/
  │   ├── repository/
  │   │   ├── ActiveDashboardRepository.kt             ← NEW
  │   │   └── DashboardRepository.kt                   ← MODIFIED (+ renameDashboard signature)
  │   └── usecase/
  │       ├── GetActiveDashboardIdUseCase.kt           ← NEW
  │       ├── SetActiveDashboardIdUseCase.kt           ← NEW
  │       └── RenameDashboardUseCase.kt                ← NEW
  ├── di/
  │   ├── DataModule.kt                                ← MODIFIED (+ ActiveDashboardRepository, DashboardChrome)
  │   ├── DomainModule.kt                              ← MODIFIED (+ 3 use case factories)
  │   └── PresentationModule.kt                        ← MODIFIED (DashboardViewModel deps)
  ├── navigation/
  │   └── HaNativeNavHost.kt                           ← MODIFIED (dynamic tab label, tap-active dispatch)
  └── ui/
      └── dashboard/
          ├── DashboardChrome.kt                       ← NEW
          ├── DashboardScreen.kt                       ← MODIFIED (Crossfade, AlertDialog, switcher slot)
          ├── DashboardSwitcherSheet.kt                ← NEW
          ├── DashboardUiModels.kt                     ← MODIFIED (switcher state + 13 new intents)
          └── DashboardViewModel.kt                    ← MODIFIED (5 new deps, 14 new handlers, pending state)

shared/src/androidMain/kotlin/com/backpapp/hanative/
  └── ui/
      └── dashboard/
          └── DashboardPreviews.kt                     ← MODIFIED (+ 6 previews; switcherSlot stub)

shared/src/commonTest/kotlin/com/backpapp/hanative/
  └── ui/
      └── dashboard/
          └── DashboardViewModelTest.kt               ← MODIFIED (+ 18 tests; FakeActiveDashboardRepository)
```

**Do NOT modify:**
- `Dashboard.sq` / `DashboardCard.sq` — schema unchanged. The `updateDashboardName` query is pre-wired.
- `EntityCard.kt` / `EntityCardViewModel.kt` / `EntityCardUiModels.kt` — consumed as-is.
- `EntityPicker.kt` / `EntityPickerViewModel.kt` / `EntityPickerUiModels.kt` — consumed as-is.
- `HaUrlRepository.kt` — pattern is referenced, not modified.
- `Motion.kt` / `Color.kt` / `HaNativeTheme.kt` / `HapticFeedback.kt` — consumed as-is.
- `IdGenerator.kt` — consumed as-is for `Dashboard.id` and `DashboardCard.id` generation.
- `StartupViewModel.kt` / `Routes.kt` — FR3 already satisfied.

### Latest Tech Notes

- **Material3 ModalBottomSheet** (`androidx.compose.material3` from CMP 1.10.0) — `ModalBottomSheet(onDismissRequest, sheetState = rememberModalBottomSheetState())` is stable. Per official Compose docs, `onDismissRequest` fires for both swipe-dismiss AND scrim-tap. No need for a separate `onScrimTap`.
- **Kotlin 2.3.20 `kotlin.uuid.Uuid.random()`** — already used in `UuidIdGenerator` (Story 4.6). Reuse via injected `IdGenerator` for the pending-dashboard id.
- **Koin Compose `koinInject<T>()`** — available via `org.koin.compose.koinInject` in Koin 4.2.1 (already on classpath, see `gradle/libs.versions.toml`). Use to fetch `DashboardChrome` in `NavHostContent` without a VM boundary.
- **kotlinx.coroutines `combine` arity** — beyond 5 flows, use the `combine(vararg Flow<T>)` overload with `(Array<Any?>) -> R` lambda. The current Story 4.6 VM uses 4-arg `combine`; with persisted active id + pending dashboard, this story's VM uses 6 — adopt the variadic overload OR group `_pickerVisible` + `_optimisticOrder` + pending + chrome state into a single `_chromeState` `MutableStateFlow<ChromeState>` and 5-arg combine. Either is acceptable.
- **DataStore `data.map { it[key] }`** — already used by `HaUrlRepository`. The `map` returns `Flow<String?>` directly; no need for a separate cold-start fetch.

### References

- [Source: `_bmad/outputs/epics.md#Story 4.7`] — Acceptance criteria
- [Source: `_bmad/outputs/prd.md#FR12, FR13, FR14, FR16`] — Create / rename / delete / persistence
- [Source: `_bmad/outputs/prd.md#NFR3`] — ≤200ms dashboard switch transition
- [Source: `_bmad/outputs/ux-design-specification.md:492-497`] — `DashboardSwitcher` anatomy + UX-DR6
- [Source: `_bmad/outputs/ux-design-specification.md:589-593`] — Dashboard switching flow + bottom-nav active-tab gesture
- [Source: `_bmad/outputs/ux-design-specification.md:537-540`] — Phase 2 dashboard management implementation roadmap
- [Source: `_bmad/outputs/architecture.md:541-543`] — FR12–14 mapping to `DashboardViewModel.kt` and use cases
- [Source: `_bmad/outputs/architecture.md:279`] — `UiState.Success(data, isStale)` sealed-class pattern (extended here with switcher slice)
- [Source: `_bmad/stories/4-6-dashboard-screen-empty-state-navigation.md`] — `DashboardViewModel` baseline + `activeDashboardOf` seam + Compose UI Boundary doctrine
- [Source: `_bmad/stories/4-5-entitypicker-bottom-sheet.md`] — `ModalBottomSheet` precedent + body-level slot pattern
- [Source: `_bmad/stories/4-2-dashboard-persistence-layer.md`] — All 6 dashboard use cases + repository binding (+1 added here)
- [Source: `_bmad/stories/3-4-onboarding-ha-url-entry-connection-test.md`] — `HaUrlRepository` DataStore wrapper pattern (mirror for `ActiveDashboardRepositoryImpl`)
- [Source: `_bmad/stories/3-5-onboarding-authentication-session-persistence.md`] — `OAuthCallbackBus` singleton bridge pattern (precedent for `DashboardChrome`)
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/HaNativeNavHost.kt:53,114-130,163-169`] — Tab label + click handler + label slot
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModel.kt:124-125`] — `activeDashboardOf` seam to swap
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/repository/DashboardRepositoryImpl.kt:165-171`] — `runCatchingCancellable` helper to reuse
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaUrlRepository.kt:37-61`] — DataStore edit + read pattern
- [Source: `shared/src/commonMain/sqldelight/com/backpapp/hanative/Dashboard.sq:14-15`] — `updateDashboardName` query already exists
- [Source: `shared/src/commonMain/sqldelight/com/backpapp/hanative/DashboardCard.sq:7`] — FK ON DELETE CASCADE — deleting dashboard cascades cards
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Motion.kt:11`] — `dashboardTransition` 200ms tween
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/HapticFeedback.kt:12`] — `DashboardSwitch` haptic
- [Source: `gradle/libs.versions.toml`] — CMP 1.10.0 (`ModalBottomSheet` stable); Koin 4.2.1 (`koinInject` available); Kotlin 2.3.20 (`kotlin.uuid.Uuid`)

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
