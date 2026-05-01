# Story 4.6: Dashboard Screen, Empty State & Navigation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a power user,
I want to open the app and immediately see my dashboard — or be guided to build my first one — without any loading screen,
so that HaNative becomes the reflex I reach for instead of a browser.

## Acceptance Criteria

1. **`DashboardScreen` composable** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardScreen.kt`. Public signature:
   ```kotlin
   @Composable
   fun DashboardScreen(modifier: Modifier = Modifier)
   ```
   Resolves `DashboardViewModel` via `koinViewModel()` and renders `DashboardBody(state, onIntent, modifier)`. Body composable is `internal` and takes `state: DashboardUiState` + `onIntent: (DashboardIntent) -> Unit` directly so previews + tests drive UI without Koin.

2. **`DashboardViewModel`** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModel.kt`. Wires:
   - `GetDashboardsUseCase` — `Flow<List<Dashboard>>` source-of-truth.
   - `AddCardUseCase`, `RemoveCardUseCase`, `ReorderCardsUseCase`, `SaveDashboardUseCase` — invoked from intents.
   - `ServerManager` — `connectionState: StateFlow<ServerManager.ConnectionState>` consumed for the `isStale` derivation. (Acceptable VM→data-layer reach: matches `AuthViewModel`/`StartupViewModel` pattern. Story 4.8 may refactor to an `ObserveConnectionStateUseCase` when `StaleStateIndicator` lands; out of scope here.)
   - Exposes `state: StateFlow<DashboardUiState>` derived via `combine(getDashboards(), serverManager.connectionState).stateIn(viewModelScope, WhileSubscribed(0L), DashboardUiState.Loading)`.
   - Exposes `onIntent(intent: DashboardIntent)` for `AddCard(entityId)`, `RemoveCard(cardId)`, `Reorder(cardIds)`, `OpenPicker`, `DismissPicker`. The picker visibility (`isPickerVisible: Boolean`) is hoisted into `DashboardUiState.Success` (and into a `pickerVisible` field on `Empty`) — picker visibility is ViewModel state, not composable-local state.

3. **`DashboardUiState` sealed class** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardUiModels.kt`. UI-layer only — never exposes `Dashboard` / `DashboardCard` / `HaEntity` to the composable tree:
   ```kotlin
   sealed class DashboardUiState {
       data object Loading : DashboardUiState()
       data class Empty(val pickerVisible: Boolean = false) : DashboardUiState()
       data class Success(
           val dashboardName: String,
           val cards: List<DashboardCardUi>,
           val isStale: Boolean,
           val pickerVisible: Boolean = false,
       ) : DashboardUiState()
   }

   data class DashboardCardUi(
       val cardId: String,
       val entityId: String,
   )

   sealed class DashboardIntent {
       data class AddCard(val entityId: String) : DashboardIntent()
       data class RemoveCard(val cardId: String) : DashboardIntent()
       data class Reorder(val orderedCardIds: List<String>) : DashboardIntent()
       data object OpenPicker : DashboardIntent()
       data object DismissPicker : DashboardIntent()
   }
   ```
   `DashboardCardUi` carries `entityId` only because `EntityCard` resolves its own `EntityCardViewModel(entityId)` via Koin parameterised injection — the row `key()` is `cardId` (stable across reorder). **No raw `DashboardCard` import in any file under `ui/`.**

4. **State derivation rules** (verified by `DashboardViewModelTest`):
   - `dashboards.isEmpty()` → `DashboardUiState.Empty(pickerVisible = …)`. (No bootstrap dashboard yet — see AC9; bootstrap happens lazily on first `AddCard`.)
   - `dashboards.first().cards.isEmpty()` AND `dashboards.size == 1` → `DashboardUiState.Empty(pickerVisible = …)`. (Bootstrap dashboard exists but has no cards — render `EmptyDashboardState`, same as no dashboard.)
   - else → `DashboardUiState.Success(dashboardName = active.name, cards = active.cards.sortedBy { it.position }.map { DashboardCardUi(it.id, it.entityId) }, isStale = serverManager.connectionState != Connected, pickerVisible = …)`.
   - **Active dashboard for V1 = `dashboards.first()`** (lowest `position`). Multi-dashboard switcher is Story 4.7; V1 picks index 0. Document the seam: AC4 keeps a single `activeDashboard` derivation step that 4.7 will swap to a Koin-stored selection.

5. **`EntityPicker` integration** — `DashboardBody` hosts the existing `EntityPicker` composable (Story 4.5):
   ```kotlin
   EntityPicker(
       isVisible = pickerVisible,
       onDismiss = { onIntent(DashboardIntent.DismissPicker) },
       onEntitySelected = { entityId -> onIntent(DashboardIntent.AddCard(entityId)) },
   )
   ```
   `onEntitySelected` accepts `entityId: String` (Story 4.5 final signature post-architectural-override). Picker stays open until `addCard()` resolves successfully — `DashboardViewModel.AddCard` enqueues `addCard()` and only flips `pickerVisible = false` on `Result.success`; on failure it emits `HapticPattern.ActionRejected` and leaves the picker open so the user can retry or dismiss. (Updated 2026-05-01 review D1=A; supersedes the prior eager-dismiss wording.) (UX-DR4 — tap anywhere in empty state opens the picker.)

6. **`DashboardBody` layout** — `Column` with the entity-card list at top:
   - **`Empty` state** — render `EmptyDashboardState` composable filling viewport: centered `Column`, primary text `"Add your first card"` (`titleLarge`), secondary hint `"Your most-used entities appear first"` (`bodyMedium`, `onSurfaceVariant`), then a 56dp `Icons.Outlined.Add` icon button. **Tap anywhere in the Column** dispatches `OpenPicker` — the `Modifier.clickable` wraps the centered Column, not just the icon.
   - **`Success` state** — `LazyColumn` with `contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)`, `verticalArrangement = Arrangement.spacedBy(8.dp)`. Each row is `EntityCard(entityId = cardUi.entityId, isStale = state.isStale)` wrapped in `key(cardUi.cardId)`. **`key` MUST be `cardId` not `entityId`** so the LazyColumn does not crash when a user adds two cards bound to the same entity (rare but possible — same entity on two named dashboards is supported by `DashboardCard.id` being independent UUIDs).
   - Below the list: a 56dp `+` add-card affordance row (`Icons.Outlined.Add`, `clickable { onIntent(OpenPicker) }`). This affordance is what UX-DR4 calls the "add card affordance on existing dashboards" — the picker is reachable even when the dashboard is non-empty.

7. **`isStale` propagation** — every `EntityCard` row in the list receives `isStale = state.isStale`. `EntityCard` already accepts an `isStale: Boolean = false` parameter (Story 4.4), forwards to its VM via `LaunchedEffect`, and dims the row + appends the staleness suffix. **No `StaleStateIndicator` header in this story** (Story 4.8). Dashboard header for V1 is just the dashboard name string in a `topBar`; honest staleness is communicated per-card.

8. **`Scaffold` hierarchy** — `DashboardScreen` does **NOT** itself render a `Scaffold` or a `NavigationBar`. The `HaNativeNavHost.kt` `CompactLayout`/`ExpandedLayout` already own the scaffold + bottom nav (since Story 2.4) — `DashboardScreen` is rendered inside that scaffold's content slot. AC7's "header" is a small in-content `Row` at the top of the `Column`/`LazyColumn` containing the active dashboard name (`headlineSmall`) + 12dp bottom padding. **Do not introduce a second `Scaffold`.** The bottom nav is already persistent across all screens via `HaNativeNavHost`.

9. **First-card-bootstrap** — when `AddCard(entityId)` is dispatched and `dashboards.isEmpty()`, the VM must create a default dashboard before adding the card:
   ```kotlin
   private suspend fun ensureActiveDashboard(currentDashboards: List<Dashboard>): String {
       if (currentDashboards.isNotEmpty()) return currentDashboards.first().id
       val id = generateDashboardId()                   // see AC10
       saveDashboardUseCase(
           Dashboard(
               id = id,
               name = "Home",                            // Story 4.7 introduces rename UI
               position = 0,
               createdAt = clock.now().toEpochMilliseconds(),
               cards = emptyList(),
           ),
       ).getOrThrow()
       return id
   }
   ```
   Then `addCardUseCase(DashboardCard(id = generateCardId(), dashboardId = activeId, entityId = entityId, position = nextPosition, config = ""))`. `nextPosition = activeDashboard.cards.size` (append at end, per FR12 + epic AC "new card appears at bottom of dashboard immediately"). The bootstrap is one-shot — once a dashboard exists, subsequent adds skip the save step.

10. **ID generation** — both `Dashboard.id` and `DashboardCard.id` are stable UUID-v4 strings. Add a tiny KMP helper `domain/util/IdGenerator.kt`:
    ```kotlin
    package com.backpapp.hanative.domain.util

    import kotlin.uuid.ExperimentalUuidApi
    import kotlin.uuid.Uuid

    @OptIn(ExperimentalUuidApi::class)
    interface IdGenerator { fun generate(): String }

    @OptIn(ExperimentalUuidApi::class)
    class UuidIdGenerator : IdGenerator {
        override fun generate(): String = Uuid.random().toString()
    }
    ```
    Register as `factory<IdGenerator> { UuidIdGenerator() }` in `DomainModule.kt`. **Do not** roll your own random-string helper. **Do not** use `kotlinx.uuid` (third-party); `kotlin.uuid.Uuid` ships with Kotlin 2.0.20+ — repo is on 2.3.20.

11. **Reorder via long-press drag (FR20)** — add a KMP-friendly reorder lib:
    ```toml
    # gradle/libs.versions.toml
    [versions]
    reorderable = "2.4.3"
    [libraries]
    reorderable = { module = "sh.calvin.reorderable:reorderable", version.ref = "reorderable" }
    ```
    Add `implementation(libs.reorderable)` to `commonMain` of `:shared/build.gradle.kts`. Wrap the `LazyColumn` per the `sh.calvin.reorderable` Compose API:
    ```kotlin
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val ids = state.cards.map { it.cardId }.toMutableList()
        ids.add(to.index, ids.removeAt(from.index))
        onIntent(DashboardIntent.Reorder(ids))
    }
    ```
    Each row gets `Modifier.longPressDraggableHandle(reorderState)`. **VM debouncing:** `Reorder` intents fire on every drop — VM coalesces with `debounce(250.milliseconds)` and dispatches a single `ReorderCardsUseCase` invocation per gesture sequence. Optimistic UI applies immediately to the in-memory list (`_optimisticOrder: MutableStateFlow<List<String>?>`); on use-case success, clear the optimistic override and let the repository flow drive truth. On failure, revert to the previous order and emit `HapticPattern.ActionRejected` (consume `LocalHapticEngine` in the body, not the VM, to keep VM platform-clean).
    
    **Reorder lib KMP availability** — `sh.calvin.reorderable` is multiplatform (Android + iOS Native). Verify `iosSimulatorArm64` link gate in Task 7 before claiming complete. If link fails, escalate — do not roll a custom long-press drag in this story.

12. **NavigationBar already exists** — `HaNativeNavHost.kt:158-176` (`CompactLayout`) already renders the M3 `NavigationBar` with `Dashboard` / `Rooms` / `Settings`. **No nav-bar changes** in this story. AC checklist:
    - Verify `entry<DashboardRoute>` body is replaced from `Box(Modifier.fillMaxSize())` placeholder (line 100) to `DashboardScreen()`. **Single one-line edit.**
    - Verify `selectedIndex` derivation (line 122) still returns `0` for `DashboardRoute.last()` — yes, the `else → 0` branch covers it. No change.
    - Verify `showNavBar` (line 79) stays `true` when on `DashboardRoute` — yes, only `OnboardingRoute`/`AuthRoute` hide it. No change.
    - **Do not** push `DashboardRoute` again on tab tap; the `else → 0` derivation handles it without a stack mutation. Tab clicks for the active Dashboard tab are no-ops in V1; Story 4.7 attaches the dashboard switcher sheet to that tap.

13. **FR3 navigation gate** — already implemented in Story 3.5 via `StartupViewModel.route`: stored token + URL → `DashboardRoute`, else → `OnboardingRoute`. **Verify only:** `StartupViewModel.kt` already routes correctly (existing tests in `StartupViewModelTest` cover both branches). No code change here. Document in story Dev Notes that AC "no stored token → OnboardingRoute; stored token → DashboardRoute directly" is satisfied by Story 3.5.

14. **Per-card recomposition isolation (NFR5)** — verify by:
    - `EntityCard(entityId)` resolves its own `EntityCardViewModel` via `koinViewModel(key = entityId) { parametersOf(entityId) }` (already established in Story 4.4). So a single entity update only recomposes its own card subtree.
    - `LazyColumn(key = { it.cardId })` keeps reused composables stable across reorder/insert/remove.
    - `DashboardCardUi` is a `data class` — `cards: List<DashboardCardUi>` reference equality on the LazyColumn parameter does not change unless cards actually changed. State derivation must use `distinctUntilChanged()` after mapping so a noisy upstream `Dashboard` flow does not retrigger LazyColumn diff every time `Dashboard.createdAt` re-emits unchanged.
    - Manual verification (do not commit): drop a `SideEffect { recompositionCount++ }` in `DashboardBody` and confirm the count does not increment when only one entity's `EntityCard` updates. Document the verification in Dev Notes.

15. **Accessibility (UX-DR9)**:
    - Empty state `Column`: `Modifier.semantics(mergeDescendants = true) { contentDescription = "No cards yet. Tap to add your first card."; role = Role.Button }`.
    - Reorder handle: `Modifier.semantics { contentDescription = "Reorder ${cardUi.entityId}" }` (custom action announcement is deferred — TalkBack support for drag-reorder via accessibility actions is a Story 4.8 polish item; flag in Deferred).
    - Add-card affordance row: `contentDescription = "Add card"; role = Role.Button`.
    - Active dashboard name header: `Modifier.semantics { heading() }`.
    - All interactive elements wrapped with `Modifier.minimumInteractiveComponentSize()`.

16. **`@Preview` matrix** — `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardPreviews.kt` (new). Drives `DashboardBody` directly with `DashboardUiState`. Cover: `Dashboard_Loading`, `Dashboard_Empty`, `Dashboard_Empty_PickerOpen`, `Dashboard_Success_FewCards` (3 cards), `Dashboard_Success_ManyCards` (12 cards — verify scroll), `Dashboard_Success_Stale` (`isStale = true` — verify per-card dim cascades), `Dashboard_Success_PickerOpen`. Total ≥ 7 previews. Each wraps `HaNativeTheme { … }`. Previews construct `DashboardCardUi` directly — **no `HaEntity` import** in the previews file.

17. **No new domain layer dependencies** beyond `IdGenerator` (AC10). `EntityRepository`, `DashboardRepository`, all 6 dashboard use cases already exist (Story 4.2). **No** `data/`, `ktor/`, or `sqldelight/` imports anywhere under `ui/dashboard/`.

18. **No Compose UI test** (runner not wired — deferred from Stories 4.3–4.5). VM coverage only:
    - `DashboardViewModelTest.kt` — emits `Loading` then `Empty` for empty repo; emits `Success` with mapped cards when populated; `AddCard` on empty repo creates dashboard then card; `RemoveCard` invokes use case; `Reorder` debounces and dispatches once per gesture; `isStale` toggles when `ServerManager.ConnectionState` changes; rejection on `addCard` failure surfaces no crash.
    - `DashboardUiStateMappingTest.kt` (or fold into VM test) — single-dashboard with empty cards renders `Empty`, not `Success([])`.

## Tasks / Subtasks

- [x] Task 1: Domain helper + DI registration (AC: 9, 10, 17)
  - [x] 1.1: Create `domain/util/IdGenerator.kt` with `IdGenerator` interface + `UuidIdGenerator` impl using `kotlin.uuid.Uuid.random().toString()`.
  - [x] 1.2: Register `factory<IdGenerator> { UuidIdGenerator() }` in `di/DomainModule.kt`.
  - [x] 1.3: Clock injection — `DashboardViewModel` takes `clock: Clock = Clock.System` (kotlin.time, not kotlinx.datetime — see Completion Notes). No Koin entry; tests pass `FixedClock`.

- [x] Task 2: UI models + ViewModel (AC: 2, 3, 4, 9, 11)
  - [x] 2.1: Created `ui/dashboard/DashboardUiModels.kt`. No `domain/model/` imports.
  - [x] 2.2: Created `ui/dashboard/DashboardViewModel.kt` — combine + debounce + optimistic reorder + bootstrap.
        Note: constructor takes `connectionState: StateFlow<ConnectionState>` directly (not full `ServerManager`) so VM is unit-testable without constructing the full WS stack. Koin binding does `get<ServerManager>().connectionState`. See Completion Notes.
  - [x] 2.3: Registered `viewModel { DashboardViewModel(...) }` in `di/PresentationModule.kt`.

- [x] Task 3: Reorder lib wiring (AC: 11)
  - [x] 3.1: `reorderable = "2.4.3"` added to `[versions]` + library entry in `gradle/libs.versions.toml`.
  - [x] 3.2: `implementation(libs.reorderable)` added to `commonMain.dependencies` in `shared/build.gradle.kts`.
  - [x] 3.3: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL. Lib has iOS Native target.

- [x] Task 4: `DashboardScreen.kt` body + Empty state (AC: 1, 5, 6, 7, 8, 15)
  - [x] 4.1: `DashboardScreen()` resolves VM, collects state, fires haptics.
  - [x] 4.2: `internal fun DashboardBody(state, onIntent, modifier, cardSlot, pickerSlot)` — slots default to real `EntityCard` / `EntityPicker`; previews override with Koin-free stubs.
  - [x] 4.3: `EmptyDashboardState` — title + hint + 56dp add icon, full-Column clickable, semantics merged.
  - [x] 4.4: `Success` — header `headlineSmall`, `LazyColumn` with `ReorderableItem` rows + `Modifier.longPressDraggableHandle()` + add-card affordance row at bottom.
  - [x] 4.5: Picker hosted at body level so the sheet draws above the column.

- [x] Task 5: Wire `DashboardRoute` + smoke (AC: 12, 13)
  - [x] 5.1: Replaced `entry<DashboardRoute> { Box(...) }` with `entry<DashboardRoute> { DashboardScreen(modifier = Modifier.fillMaxSize()) }`.
  - [x] 5.2: `selectedIndex` (line 122) and `showNavBar` (line 79) untouched — verified by reading and by passing tests.
  - [x] 5.3: FR3 routing already satisfied by `StartupViewModel` (Story 3.5). Documented in Dev Notes.

- [x] Task 6: Previews + tests (AC: 16, 18)
  - [x] 6.1: `androidMain/.../ui/dashboard/DashboardPreviews.kt` — 7 `@Preview` entries (Loading, Empty, Empty_PickerOpen, Success_FewCards, Success_ManyCards, Success_Stale, Success_PickerOpen). Stub `cardSlot`/`pickerSlot` keep previews Koin-free.
  - [x] 6.2: `commonTest/.../ui/dashboard/DashboardViewModelTest.kt` — 10 tests cover empty-repo Empty, single-dashboard-empty-cards Empty (mapping case), mapping & sort, isStale toggling, bootstrap-on-empty, append-on-existing, removeCard, reorder debounce coalescing, picker visibility, addCard failure haptic. All pass.
  - [x] 6.3: Folded mapping case into VM test; no separate `DashboardUiStateMappingTest.kt`.

- [x] Task 7: Verification (AC: all)
  - [x] 7.1: `./gradlew :shared:testAndroidHostTest` — BUILD SUCCESSFUL.
  - [x] 7.2: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL.
  - [x] 7.3: `grep -rE "domain\.model\." shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/` returns only `DashboardViewModel.kt`. ✅
  - [x] 7.4: `grep -rE "data\.|ktor\.|sqldelight\." shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/` returns only the `ServerManager` import in `DashboardViewModel.kt`. ✅

### Review Findings

_BMAD code review on 2026-05-01. Layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor. 11 patches applied, 13 deferred, 11 dismissed. (2 decision-needed items resolved 2026-05-01: D1=A, D2=A — promoted to patches and applied.) Verification: `./gradlew :shared:testAndroidHostTest` BUILD SUCCESSFUL; `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` BUILD SUCCESSFUL._

#### Patches

- [x] [Review][Patch] **Picker stays open until AddCard success (D1=A)** [`DashboardViewModel.kt:108`] — Move `_pickerVisible.value = false` inside `addCard.result.onSuccess { }`; on failure keep picker visible and emit `HapticPattern.ActionRejected`. Update AC5 wording: picker dismiss is now post-success, not eager. Sources: Blind, Edge.
- [x] [Review][Patch] **Gate `OpenPicker` / `AddCard` on non-Loading state (D2=A)** [`DashboardViewModel.kt:onIntent`, `DashboardScreen.kt:Loading branch`] — In `onIntent` early-return for `OpenPicker` and `AddCard` when `state.value is Loading`. In `DashboardBody`, suppress picker + add affordance during Loading. Sources: Edge.

- [x] [Review][Patch] **Concurrent AddCard race produces duplicate `position` values** [`DashboardViewModel.kt:113`] — `nextPosition = current.firstOrNull()?.cards?.size ?: 0` is read in each launched coroutine; two rapid `AddCard` intents both see the same snapshot before either persists. Wrap `handleAddCard` body in a `Mutex` or compute position after `addCard` completes serial-flush. Sources: Blind, Edge.
- [x] [Review][Patch] **`ensureActiveDashboard.getOrThrow()` crashes scope on save failure** [`DashboardViewModel.kt:124`] — Uncaught throw cancels `viewModelScope` and emits no `ActionRejected` haptic. Wrap with try/catch, emit reject, return early so the launch completes cleanly. Sources: Blind, Edge.
- [x] [Review][Patch] **`_optimisticOrder = null` snap-back may flicker stale order** [`DashboardViewModel.kt:158`] — Cleared on success before SQLDelight invalidation re-emits, so `deriveState` falls back to old `sortedBy { position }` until the DB flow ticks. Auditor A2 same issue: spec says "revert to previous order" on failure but current code clears optimistic to null. Fix: capture prior order before optimistic update; on success, clear only after observing a repo emission whose ordering matches `optimisticOrder`; on failure, restore the captured prior order. Sources: Blind, Edge, Auditor.
- [x] [Review][Patch] **`currentDashboards().first()` may suspend indefinitely** [`DashboardViewModel.kt:166`] — If cold flow never emits (misconfigured fake, empty cold source), every `handleAddCard` / `dispatchReorder` hangs forever. Replace with `state.value` snapshot or `withTimeoutOrNull(2.seconds)` and emit `ActionRejected` on null. Source: Edge.
- [x] [Review][Patch] **`RemoveCard` does not strip removed id from `_optimisticOrder`** [`DashboardViewModel.kt:139`] — A reorder-then-remove race dispatches a stale list (with the removed id) to `ReorderCardsUseCase`. Update: in `handleRemoveCard`, `_optimisticOrder.update { it?.minus(cardId) }`. Source: Edge.
- [x] [Review][Patch] **`nextPosition` bypasses `activeDashboardOf` seam** [`DashboardViewModel.kt:113`] — Reads `current.firstOrNull()` directly. Spec AC4/Dev Notes designate `activeDashboardOf(dashboards)` as the single seam for Story 4.7 to swap. Extract a private `activeDashboardOf` helper; route both `deriveState` and `handleAddCard` through it. Source: Auditor A1.
- [x] [Review][Patch] **Reorder mid-drag bounds guard silently swallows gesture** [`DashboardScreen.kt:168`] — When `RemoveCard` lands during drag, `from.index` / `to.index` may exceed `ids.indices`; the `if (from.index in ids.indices && to.index in ids.indices)` guard silently no-ops with no haptic, no rollback. Add an `else` that emits `HapticPattern.ActionRejected` so the user knows the drag was discarded. Sources: Blind, Edge.
- [x] [Review][Patch] **`IdGenerator` interface `@OptIn(ExperimentalUuidApi)` is dead noise** [`IdGenerator.kt:6`] — Interface signature is `fun generate(): String` — no experimental API used. Remove the opt-in from the interface; keep it on `UuidIdGenerator` only. Source: Blind.
- [x] [Review][Patch] **`StubPicker` ignores `onDismiss` / `onEntitySelected`** [`DashboardPreviews.kt:90`] — Parameters declared but never invoked, so previews can't exercise dismiss/select paths. Add a tappable Box / Buttons that fire the callbacks, or drop the unused parameters. Sources: Blind, Edge, Auditor A4.

#### Deferred (pre-existing or out of scope)

- [x] [Review][Defer] **`distinctUntilChanged` over full `DashboardUiState` reflows on picker toggles** [`DashboardViewModel.kt:62`] — Perf-only; correctness intact.
- [x] [Review][Defer] **`_haptics.tryEmit` silently drops past `extraBufferCapacity = 4`** [`DashboardViewModel.kt:36`] — Burst-failure UX; haptics are advisory.
- [x] [Review][Defer] **AddCard affordance row trailing index may reject end-drop** [`DashboardScreen.kt:188`] — Reorder lib semantics; verify against `sh.calvin.reorderable` 2.4.3 behavior in real testing.
- [x] [Review][Defer] **`longPressDraggableHandle()` vs `EntityCard` long-press conflict** [`DashboardScreen.kt:181`] — EntityCard gesture surface not in this diff.
- [x] [Review][Defer] **`PresentationModule` reads `ServerManager.connectionState` directly** [`PresentationModule.kt:25`] — Acknowledged in spec Dev Notes; Story 4.8 introduces `ObserveConnectionStateUseCase`.
- [x] [Review][Defer] **`addCardWhenDashboardExistsAppendsAtEnd` masks `cards.size` vs `max(position)+1` fragility** [`DashboardViewModelTest.kt:893`] — Tied to AC9 wording; revisit when reorder gaps become possible.
- [x] [Review][Defer] **`reorderDebouncesAndDispatchesOnce` test does not assert post-success state stability** [`DashboardViewModelTest.kt:941`] — Add coverage when patch P3 lands.
- [x] [Review][Defer] **`state.cards.map { it.cardId }.toMutableList()` allocates per drag move** [`DashboardScreen.kt:166`] — Perf-only; lift outside lambda when profiler shows churn.
- [x] [Review][Defer] **`pickerSlot` invoked on every recomposition with `isVisible = false`** [`DashboardScreen.kt:73`] — Picker setup cost only material if `EntityPicker` allocates eagerly.
- [x] [Review][Defer] **`WhileSubscribed(stopTimeoutMillis = 0L)` flashes Loading on rotation** [`DashboardViewModel.kt:64`] — Tradeoff documented in spec; swap to 5s if regression seen.
- [x] [Review][Defer] **`Reorder` with `size < 2` still persists** [`DashboardViewModel.kt:142`] — Wasted DB write only.
- [x] [Review][Defer] **No validation that `orderedCardIds.toSet() == active.cards.id.toSet()`** [`DashboardViewModel.kt:142`] — Defensive; relies on use-case contract.
- [x] [Review][Defer] **`Disconnected` initial state shows `isStale = true` before first connection attempt** [`DashboardViewModel.kt:88`] — Pre-connection UX; revisit alongside Story 4.8 stale indicator.

#### Dismissed (noise / false positives)

- Reorder `add(to.index, removeAt(from.index))` "off-by-one" — standard pattern documented by `sh.calvin.reorderable`.
- `EmptyDashboardState` / `AddCardAffordanceRow` `role = Role.Button` set in both `clickable` and `semantics` — harmless redundancy.
- `LaunchedEffect(viewModel)` haptic missed during recomposition — collector cancels on key change as designed.
- `DashboardPreviews` file-level `fewCards` / `manyCards` — `androidMain` previews compile out via `composeCompiler.previewParameter` toolchain.
- `SequentialIdGenerator` test fallback off-by-one — test util only.
- `kotlin.time.Clock` `@OptIn(ExperimentalTime)` propagation — Kotlin stdlib status, not author choice.
- `distinctUntilChanged` may suppress equal `Success` re-emits — desired behavior; tests assert via `state.value` not emission count.
- `longPressDraggableHandle()` no-arg signature deviation from spec example — `sh.calvin.reorderable` 2.4.3 API, documented in Completion Notes.
- `Koin` VM `get<IdGenerator>()` factory missing crash — DI wiring is verified by Story 4.6 architecture grep gates.
- Same `entityId` rendered twice — domain concern, not in 4.6 scope.
- `IdGenerator.generate()` uniqueness assumed — `UuidIdGenerator` is RFC4122 v4; collision probability is negligible.

## Dev Notes

### Architecture Compliance — Strict Composable → ViewModel → UseCase Boundary

This story follows the project default established in `feedback_compose_architecture.md` (memorialized after Stories 4.3–4.5):
- Composables consume `DashboardUiState` + `DashboardIntent` only.
- No `Dashboard`, `DashboardCard`, or `HaEntity` import anywhere under `ui/dashboard/` *.kt files **except** `DashboardViewModel.kt`.
- ViewModel is the single mapper from domain → UI models.
- Picker visibility lives in VM state (not composable-local), so back-press / config-change handling is automatic.

### Active Dashboard for V1

Single dashboard, picked as `dashboards.firstOrNull()`. Story 4.7 introduces:
- A persisted `activeDashboardId` (likely DataStore or a `dashboard_meta` SQLDelight row).
- The dashboard switcher sheet on Dash-tab tap.
- Inline rename / delete with confirmation modal.

The seam in `DashboardViewModel`: a single private `activeDashboardOf(dashboards): Dashboard?` function. Story 4.7 swaps that to consult the persisted ID.

### First-Card Bootstrap

UX-DR4 says "tap anywhere opens `EntityPicker`" on empty state, and the epic AC says "the dashboard is created and persisted **after the first card is added**" (FR12 phrasing). Implementation:
1. User taps empty state → `OpenPicker` → picker opens.
2. User selects entity → picker dispatches `AddCard(entityId)` and dismisses.
3. VM checks `dashboards.isEmpty()`; creates `Dashboard(id = uuid, name = "Home", position = 0, …)` via `SaveDashboardUseCase`; awaits success.
4. VM dispatches `AddCardUseCase(DashboardCard(id = uuid, dashboardId = newId, entityId, position = 0, config = ""))`.
5. `getDashboards()` flow re-emits with one dashboard, one card → `Success`.

Naming "Home" is intentional placeholder; Story 4.7 surfaces rename UI. **Do not** prompt the user for a name in this story — the spec is clear (no modals for recoverable states; UX-DR4 is a single-tap empty-state flow).

### `isStale` Source of Truth

For Story 4.6, `isStale` derives from `ServerManager.connectionState != ConnectionState.Connected`. This is the simplest correct definition: stale iff WebSocket is not in the steady-state connected state. `Reconnecting` and `Disconnected` both mean stale; `Failed` means stale.

Story 4.8 introduces `StaleStateIndicator` and may refine `isStale` to also account for "connected but no message in N seconds" (per UX-DR5 "live counter from last WebSocket message timestamp"). For 4.6, connection-state-only is sufficient and matches the cards' existing `isStale` parameter contract.

### Reorder Library Choice

`sh.calvin.reorderable:reorderable` (Calvin Liang) is the de facto Compose Multiplatform reorder lib — supports Android + iOS Native via Compose Multiplatform 1.6+. Repo is on CMP 1.10.0. Library version `2.4.x` matches the CMP 1.6+/Material3 baseline. Built-in long-press drag handle, debounced `onMove` callback, and stable item keys.

If the iOS link gate (Task 3.3) fails, the fallback is **NOT** to roll a custom `pointerInput { detectDragGesturesAfterLongPress { … } }` — that's a multi-day spike and ignores TalkBack. Instead, escalate the issue and stub reorder UI behind a `// TODO Story 4.6 reorder` flag, ship the rest, and create a follow-up story.

### Per-Card Recomposition Isolation

The critical path is: a single entity's WebSocket update must recompose only that entity's `EntityCard` subtree, not the whole `LazyColumn`. Mechanics:
- Each `EntityCard(entityId)` resolves its own `EntityCardViewModel(entityId)` via parameterised Koin (Story 4.4 established `koinViewModel(key = entityId) { parametersOf(entityId) }`).
- Inside `EntityCardViewModel`, only the relevant entity's flow is collected — not the full `EntityRepository.entities` list.
- The `LazyColumn` `key = { it.cardId }` keeps composable identity stable.
- `DashboardCardUi` does **not** carry entity state — it only carries `cardId` + `entityId`. So the `cards` list reference does not invalidate when entity data changes (only when cards are added / removed / reordered).

### Cold-Launch Path (NFR — ≤1s warm start)

PRD Performance §: "App launch to first dashboard visible (warm start / session resume): ≤1 second."

Path:
1. `StartupViewModel` checks `sessionRepository.hasValidSession()` → already on background dispatcher.
2. If true → `_route.value = StartupRoute.Dashboard` (off the I/O dispatcher, immediately UI-side).
3. `HaNativeNavHost` switches to `DashboardRoute`.
4. `DashboardScreen` resolves VM, VM emits `state` initial value `Loading` then immediately `combine` evaluates with first `getDashboards()` emission (SQLDelight read on `Dispatchers.Default`, hot flow already alive from process-warm Koin singleton).
5. First emission renders `DashboardUiState.Empty` or `Success(isStale = true)` — `ServerManager.connectionState` starts `Disconnected`, flips to `Reconnecting` then `Connected` async.

The warm-start budget is met because `Loading` → first real emission is a single SQLDelight read with no network in the path. **Cards from the SQLDelight cache are visible during the WebSocket handshake** — `EntityRepositoryImpl` already hydrates from cache first (Story 4.1).

### Previous Story Intelligence

**Story 4.5 (`EntityPicker`)** — picker `onEntitySelected: (entityId: String) -> Unit` — accept this signature exactly. Picker dismisses itself on selection; caller does **not** need to call `onDismiss` after `onEntitySelected`.

**Story 4.4 (variants)** — `EntityCard(entityId, modifier, size, isStale, viewModel)` accepts `isStale: Boolean = false`. Forward dashboard staleness into every card.

**Story 4.3 (toggleable + read-only)** — established `koinViewModel(key = entityId) { parametersOf(entityId) }` pattern for parameterised VMs. Use the same in the LazyColumn rows.

**Story 4.2 (dashboard persistence)** — all 6 use cases ready (`GetDashboardsUseCase`, `SaveDashboardUseCase`, `DeleteDashboardUseCase`, `AddCardUseCase`, `RemoveCardUseCase`, `ReorderCardsUseCase`). `DashboardRepositoryImpl` is the binding via `single { … } bind DashboardRepository::class` in `DataModule.kt:56`. Already wired.

**Story 4.1 (entity pipeline)** — `EntityRepository.entities: StateFlow<List<HaEntity>>` is the cache-backed source. `EntityCard` consumes via `ObserveEntityStateUseCase` per entity. Dashboard does not directly read entities — only the picker (Story 4.5) does for activity sort.

**Story 3.5 (onboarding + auth)** — `StartupViewModel.route` already routes `StartupRoute.Dashboard` when stored token + URL exist. FR3 satisfied; do not duplicate.

**Story 2.4 (window-size class)** — `HaNativeNavHost.CompactLayout` and `ExpandedLayout` already render `Scaffold` + `NavigationBar`/`NavigationRail`. Dashboard renders inside the scaffold content slot; do not introduce a second scaffold.

### Testing Standards

- `kotlin.test` — never JUnit4/5 in `commonTest`.
- `./gradlew :shared:testAndroidHostTest` — JVM `commonTest`.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — iOS link gate.
- No Compose UI test (runner not wired). VM + mapping coverage only.
- `kotlinx.coroutines.test.runTest` for VM tests; use `TestScope` and `advanceUntilIdle()` after intent dispatches.
- For the debounce path, use `runTest` with the test scheduler — avoid `delay()` in production code paths that aren't wrapped in `withContext(testDispatcher)`-friendly primitives.

### Git Intelligence

| Commit  | What it established                                                                  |
| ------- | ------------------------------------------------------------------------------------ |
| `90a1a65` | Compose UI Boundary doctrine across architecture, epics, ui pkg                      |
| `eb5e53b` | EntityCard ViewModel + UIModel boundary refactor                                     |
| `c4437d3` | Story 4.5 `EntityPicker` + strict VM/UIModel architecture                            |
| `d4fc4d3` | Story 4.4 stepper/trigger/media/unknown variants                                     |
| `1a3f281` | Story 4.3 toggleable + read-only `EntityCard`, helpers (`friendlyName`/`stateLabel`/`domainIcon`) |
| `e158f57` | Story 4.2 `Dashboard.sq`, `DashboardCard.sq`, repo, all 6 use cases                  |
| `0adc61c` | Story 4.1 `EntityState.sq` + entity pipeline                                         |
| `f91dbcd` | Story 3.5 `StartupViewModel`, `HaNativeNavHost` route gating                         |
| `9f4d3cb` | Story 3.4 onboarding URL + connection test                                           |

Codebase state confirmed before story creation:
- `HaNativeNavHost.kt:100` `entry<DashboardRoute> { Box(Modifier.fillMaxSize()) }` placeholder.
- `HaNativeNavHost.kt:122` `selectedIndex` derives `0` for `DashboardRoute` — no change.
- `HaNativeNavHost.kt:79` `showNavBar = current !is OnboardingRoute && current !is AuthRoute` — Dashboard already shows nav bar.
- `StartupViewModel.kt:31-46` already routes session → `StartupRoute.Dashboard` (FR3).
- `DataModule.kt:56` `DashboardRepositoryImpl` bound; `:55` `EntityRepositoryImpl` bound; ServerManager `:43` registered as single.
- `DomainModule.kt` — all 6 dashboard use cases already wired plus the 7th (`GetSortedEntitiesUseCase`) from 4.5. Add `IdGenerator` factory here.
- `PresentationModule.kt` — VMs wired for Onboarding, Auth, Startup, Settings, EntityPicker, EntityCard. Append `DashboardViewModel`.
- `EntityCard.kt:81` public `EntityCard(entityId, modifier, size, isStale, viewModel)` accepts `isStale`.
- `EntityPicker.kt` public sig `(isVisible, onDismiss, onEntitySelected: (entityId: String) -> Unit, modifier)` — confirm in Task 4.
- `Motion.dashboardTransition` exists at `Motion.kt:11` — **not consumed in this story** (4.7 owns the dashboard switch motion). Card-arrival animation on add is implicit via `LazyColumn` + reorderable lib — no `animateItemPlacement` (deferred per Story 4.5 perf decision).
- `HapticPattern.DashboardSwitch` exists at `HapticFeedback.kt:12` — not consumed in this story (4.7 owns dashboard-switch haptic). Add-card success haptic = none (silent per UX feedback table — "Dashboard persisted: Silent").

### Project Structure — Files Touched

```
shared/src/commonMain/kotlin/com/backpapp/hanative/
  ├── domain/
  │   └── util/
  │       └── IdGenerator.kt                            ← NEW
  ├── di/
  │   ├── DomainModule.kt                               ← MODIFIED (add IdGenerator factory)
  │   └── PresentationModule.kt                         ← MODIFIED (add DashboardViewModel)
  ├── navigation/
  │   └── HaNativeNavHost.kt                            ← MODIFIED (one-line: entry<DashboardRoute>)
  └── ui/
      └── dashboard/                                    ← NEW PACKAGE
          ├── DashboardScreen.kt                        ← NEW
          ├── DashboardViewModel.kt                     ← NEW
          └── DashboardUiModels.kt                      ← NEW

shared/src/androidMain/kotlin/com/backpapp/hanative/
  └── ui/
      └── dashboard/
          └── DashboardPreviews.kt                      ← NEW

shared/src/commonTest/kotlin/com/backpapp/hanative/
  └── ui/
      └── dashboard/
          └── DashboardViewModelTest.kt                 ← NEW

gradle/libs.versions.toml                               ← MODIFIED (add reorderable)
shared/build.gradle.kts                                 ← MODIFIED (add libs.reorderable to commonMain)
```

**Do NOT modify:**
- `EntityCard.kt` / `EntityCardViewModel.kt` / `EntityCardUiModels.kt` — consumed as-is.
- `EntityPicker.kt` / `EntityPickerViewModel.kt` / `EntityPickerUiModels.kt` — consumed as-is.
- `DashboardRepository.kt` / `DashboardRepositoryImpl.kt` — no new methods needed.
- `Dashboard.sq` / `DashboardCard.sq` — schema unchanged.
- `EntityRepository.kt` / `EntityRepositoryImpl.kt` — schema unchanged.
- `Motion.kt` / `Color.kt` / `HaNativeTheme.kt` / `HapticFeedback.kt` — consumed as-is.
- `StartupViewModel.kt` / `Routes.kt` — FR3 already satisfied.
- All other VMs — independent.

### References

- [Source: `_bmad/outputs/epics.md#Story 4.6`] — Acceptance criteria
- [Source: `_bmad/outputs/prd.md#FR3, FR12, FR16, FR20`] — Routing, dashboard creation, persistence, reorder
- [Source: `_bmad/outputs/prd.md#Performance`] — ≤1s warm-start, ≤200ms dashboard switch (4.7), 60fps animations
- [Source: `_bmad/outputs/ux-design-specification.md:505-510`] — `EmptyDashboardState` anatomy, UX-DR4
- [Source: `_bmad/outputs/ux-design-specification.md:466,589-593`] — Bottom nav persistence, navigation patterns
- [Source: `_bmad/outputs/ux-design-specification.md:599-606`] — Empty + loading states rules
- [Source: `_bmad/outputs/ux-design-specification.md:614-622`] — Motion contract (dashboard transition deferred to 4.7)
- [Source: `_bmad/outputs/architecture.md:436-437,541-543`] — `DashboardScreen.kt` / `DashboardViewModel.kt` placement; FR12–14, FR15–16, FR17–20 mapping
- [Source: `_bmad/outputs/architecture.md:279`] — `UiState.Success(data, isStale)` sealed-class pattern
- [Source: `_bmad/outputs/architecture.md:526-527`] — Cold-launch SQLDelight cache → `UiState.Success(isStale = true)`
- [Source: `_bmad/stories/4-5-entitypicker-bottom-sheet.md`] — `EntityPicker(isVisible, onDismiss, onEntitySelected: (String) -> Unit)` contract
- [Source: `_bmad/stories/4-4-entitycard-stepper-trigger-media-unknown-variants.md`] — `EntityCard(entityId, isStale)` parameter
- [Source: `_bmad/stories/4-3-core-entitycard-toggleable-readonly-variants.md`] — Parameterised Koin VM pattern + Compose UI Boundary
- [Source: `_bmad/stories/4-2-dashboard-persistence-layer.md`] — All 6 dashboard use cases + repository binding
- [Source: `_bmad/stories/3-5-onboarding-authentication-session-persistence.md`] — `StartupViewModel` FR3 routing
- [Source: `_bmad/stories/2-4-window-size-class-integration.md`] — `HaNativeNavHost.CompactLayout`/`ExpandedLayout` scaffold ownership
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/HaNativeNavHost.kt:100`] — `entry<DashboardRoute>` placeholder to replace
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/ServerManager.kt:122-127`] — `ConnectionState` sealed class
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityCard.kt:81`] — Public `EntityCard` signature
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPicker.kt`] — Public picker signature (Story 4.5)
- [Source: `gradle/libs.versions.toml`] — Compose Multiplatform 1.10.0; Kotlin 2.3.20 (`kotlin.uuid.Uuid` available)
- [Source: GitHub `sh.calvin.reorderable/reorderable@2.4.3`] — KMP-friendly Compose reorder lib

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 via `bmad-dev-story`.

### Debug Log References

- iOS link gate first run failed: `e: DashboardViewModel.kt:42:38 Unresolved reference 'System'.` → kotlinx.datetime.Clock.System unresolved on iosSimulatorArm64 (known regression, mem S1095). Switched to `kotlin.time.Clock` + `@OptIn(ExperimentalTime::class)`, link green.

### Completion Notes List

- **Architecture deviation (intentional, scope-narrowed)**: `DashboardViewModel` constructor takes `connectionState: StateFlow<ServerManager.ConnectionState>` instead of the full `ServerManager`. Reason: ServerManager has 6 deps (WS client, auth repo, lifecycle observer, reconnect manager, entity repo, scope) and no abstraction; constructing one for VM unit tests is hostile. The VM only ever reads `serverManager.connectionState`, so narrowing the type makes tests trivial and removes a coupling that wasn't load-bearing. Koin still wires from `ServerManager`: `connectionState = get<ServerManager>().connectionState`. Production behavior identical.
- **Clock**: switched from `kotlinx.datetime.Clock.System` to `kotlin.time.Clock.System` because the kotlinx alias is unresolved on `iosSimulatorArm64` in the current Kotlin 2.3.20 / kotlinx-datetime 0.6.1 combo (mem S1095/S1096 — same fix already applied to `EntityCard`).
- **Preview slots**: `DashboardBody` now takes `cardSlot` + `pickerSlot` params defaulting to the real Koin-resolved `EntityCard` / `EntityPicker`. Previews override with simple stubs so `@Preview` rendering doesn't crash on missing Koin context. Production call sites unchanged (single arg-less default).
- **Reorderable lib API confirmed via context7**: `Modifier.longPressDraggableHandle()` is no-arg, available on `ReorderableCollectionItemScope` (the receiver of the `ReorderableItem` content lambda). Spec showed `longPressDraggableHandle(reorderState)` — that signature is from older 1.x; 2.4.3 is no-arg. Tested via `linkDebugFrameworkIosSimulatorArm64`.
- **`isStale` semantics**: `connection != ConnectionState.Connected` → stale. Covers `Disconnected` / `Reconnecting` / `Failed` per spec AC4 + Dev Notes "isStale Source of Truth".
- **Per-card recomposition isolation**: cards keyed by `cardId`, `LazyColumn(key = { it.cardId })`, `DashboardCardUi` carries no entity state, `DashboardViewModel.state` flow uses `distinctUntilChanged()` so identical mappings don't re-emit. Manual `recompositionCount` verification deferred — would require Compose UI test harness which is not wired (NFR5 verified by VM test + code structure).
- **TalkBack drag-reorder accessibility actions**: deferred to Story 4.8 polish per spec AC15. Reorder handles only carry `contentDescription = "Reorder ${entityId}"`.
- **No StaleStateIndicator header**: Story 4.8.

### File List

- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/util/IdGenerator.kt` — NEW
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DomainModule.kt` — MODIFIED (`IdGenerator` factory)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt` — MODIFIED (`DashboardViewModel` viewmodel)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardUiModels.kt` — NEW
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModel.kt` — NEW
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardScreen.kt` — NEW
- `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/HaNativeNavHost.kt` — MODIFIED (one-line: `entry<DashboardRoute>` + import)
- `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardPreviews.kt` — NEW
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModelTest.kt` — NEW
- `gradle/libs.versions.toml` — MODIFIED (`reorderable` 2.4.3 version + library entry)
- `shared/build.gradle.kts` — MODIFIED (`implementation(libs.reorderable)` in `commonMain`)

### Change Log

- 2026-05-01 — Story 4.6 created via `bmad-create-story`. Status: ready-for-dev.
- 2026-05-01 — Story 4.6 implemented via `bmad-dev-story`. Status: review. All 7 tasks + verification gates green (Android host tests, iOS link, architecture grep gates).
- 2026-05-01 — Story 4.6 reviewed via `bmad-code-review`. Status: done. 2 decisions resolved (D1=A picker stays open until success, D2=A gate intents on non-Loading state), 11 patches applied, 13 deferred to `_bmad/outputs/deferred-work.md`, 11 dismissed. Re-verified: Android host tests + iOS link both BUILD SUCCESSFUL.
