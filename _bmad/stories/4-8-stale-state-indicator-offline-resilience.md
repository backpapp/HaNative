# Story 4.8: StaleStateIndicator & Offline Resilience

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a power user,
I want the app to tell me honestly when it's lost contact with my home and recover automatically,
so that I never see a crash or blank screen, and never have to manually restart or reconnect.

## Acceptance Criteria

1. **`StaleStateIndicator` composable** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/StaleStateIndicator.kt`. Public signature:
   ```kotlin
   @Composable
   internal fun StaleStateIndicator(
       state: StaleIndicatorUi,
       modifier: Modifier = Modifier,
   )
   ```
   Renders inside `DashboardHeader` (same `Row` as dashboard name), trailing-aligned, replacing the position the future "connection pill" occupies (UX spec §`StaleStateIndicator`). It is a leaf composable: takes UIModel, no Koin, no domain types. Dashboard name `Text` and indicator share a single `Row(verticalAlignment = Alignment.CenterVertically)` — the indicator never pushes the name layout because all three states reserve the same vertical footprint via a fixed `Modifier.height(24.dp)` outer box.

2. **Three visual states** map 1:1 to `StaleIndicatorUi.kind` (sealed class — see AC4):
   - **`Connected`** — `Box {}` of size `0.dp × 0.dp` (literally nothing rendered, but the composable still emits a node so accessibility tree stays stable). **Zero layout footprint** — no padding, no content description (UX-DR5).
   - **`Stale`** — `Row { Text("Last updated ${seconds}s ago", color = MaterialTheme.colorScheme.primary /* accent #F59E0B */, style = MaterialTheme.typography.labelSmall) }`. The `seconds` value re-renders every 1000ms while the state is `Stale` — see AC5. Color uses `MaterialTheme.colorScheme.primary` (already maps to `accent` per `Color.kt:18`); do NOT hardcode `#F59E0B`.
   - **`Reconnecting`** — `Row { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary); Spacer(width = 6.dp); Text("Reconnecting…", color = MaterialTheme.colorScheme.primary, style = labelSmall) }`. Spinner is M3 `CircularProgressIndicator` — no custom drawable.
   - Transitions between states use `AnimatedContent(targetState = state.kind, transitionSpec = { fadeIn(Motion.staleIndicatorFade) togetherWith fadeOut(Motion.staleIndicatorFade) using SizeTransform(clip = false) })`. **`Motion.staleIndicatorFade` already exists** at `Motion.kt:29` (300ms tween) — reuse, do not re-declare.

3. **Header integration** — modify `DashboardScreen.DashboardHeader` (`DashboardScreen.kt:271-280`) to:
   ```kotlin
   @Composable
   private fun DashboardHeader(name: String, indicator: StaleIndicatorUi) {
       Row(
           modifier = Modifier
               .fillMaxWidth()
               .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
           verticalAlignment = Alignment.CenterVertically,
       ) {
           Text(
               text = name,
               style = MaterialTheme.typography.headlineSmall,
               modifier = Modifier.weight(1f).semantics { heading() },
           )
           StaleStateIndicator(state = indicator)
       }
   }
   ```
   `Empty` state header (the `dashboardName != null` branch in `EmptyDashboardState`, `DashboardScreen.kt:196-203`) ALSO receives the indicator with the same anatomy so cold-launch-with-HA-unreachable on an empty dashboard still shows honest staleness.

4. **`StaleIndicatorUi` UI model** — add to `DashboardUiModels.kt`. Carries kind + a single `lastMessageEpochMs` field that the composable uses to derive the live counter:
   ```kotlin
   data class StaleIndicatorUi(
       val kind: StaleIndicatorKind,
       // Used only when kind is Stale. ms-since-epoch of the last successful WebSocket
       // message receipt; null if app has never connected this session (e.g. cold launch
       // with HA unreachable — show "Last updated --" fallback per AC5).
       val lastMessageEpochMs: Long? = null,
   )

   sealed class StaleIndicatorKind {
       data object Connected : StaleIndicatorKind()
       data object Stale : StaleIndicatorKind()
       data object Reconnecting : StaleIndicatorKind()
   }
   ```
   Surface on **both** `DashboardUiState.Empty` and `DashboardUiState.Success`:
   ```kotlin
   data class Empty(
       val pickerVisible: Boolean = false,
       val switcher: DashboardSwitcherUi = DashboardSwitcherUi(),
       val indicator: StaleIndicatorUi = StaleIndicatorUi(StaleIndicatorKind.Connected),
   ) : DashboardUiState()
   data class Success(
       val dashboardName: String,
       val activeDashboardId: String? = null,
       val cards: List<DashboardCardUi>,
       val isStale: Boolean,
       val pickerVisible: Boolean = false,
       val switcher: DashboardSwitcherUi = DashboardSwitcherUi(),
       val indicator: StaleIndicatorUi = StaleIndicatorUi(StaleIndicatorKind.Connected),
   ) : DashboardUiState()
   ```
   The existing `isStale: Boolean` field is **kept** (per-card dim + suffix; Story 4.6 AC7) — `isStale = indicator.kind != Connected`, populated by the same VM derivation. Do not remove it.

5. **Live counter derivation** — when `kind == Stale`, the `Text("Last updated Xs ago")` value MUST update every second. Implement inside `StaleStateIndicator` (NOT in the VM — VM emitting per-second `StateFlow` is wasteful):
   ```kotlin
   @OptIn(ExperimentalTime::class)
   private fun secondsAgoFlow(lastMessageEpochMs: Long?, clock: Clock = Clock.System): Flow<Long?> = flow {
       if (lastMessageEpochMs == null) { emit(null); return@flow }
       while (currentCoroutineContext().isActive) {
           val now = clock.now().toEpochMilliseconds()
           emit(((now - lastMessageEpochMs).coerceAtLeast(0L) / 1000L))
           delay(1000L)
       }
   }
   ```
   Composable consumes via `produceState(initialValue = ..., lastMessageEpochMs) { ... }` or `secondsAgoFlow(...).collectAsState(initial = 0L)`. When `lastMessageEpochMs == null`, render `"Last updated --"` (literal two dashes — never "0s ago", never "1970"). The clock parameter is injected for testability via a Composition Local provided in tests; production path uses `Clock.System` directly.

6. **`ObserveConnectionStateUseCase`** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/ObserveConnectionStateUseCase.kt`. Wraps the `ServerManager.connectionState` exposure so the VM no longer reaches into the data layer directly (resolves Story 4.6 deferred — `PresentationModule.kt:32` direct `ServerManager` reach):
   ```kotlin
   class ObserveConnectionStateUseCase(
       private val serverManager: ServerManager,
   ) {
       operator fun invoke(): StateFlow<ServerManager.ConnectionState> = serverManager.connectionState
   }
   ```
   Register `factory { ObserveConnectionStateUseCase(get()) }` in `DomainModule.kt`. Update `DashboardViewModel` constructor: replace `connectionState: StateFlow<ServerManager.ConnectionState>` parameter with `observeConnectionState: ObserveConnectionStateUseCase`; the VM internally calls `observeConnectionState()` once and stores the resulting `StateFlow` in a private `val`. Update `PresentationModule.kt:32` accordingly — remove the inline `get<ServerManager>().connectionState` reach. **NOTE:** keep `ServerManager.ConnectionState` as the leaked type — the use case is a relocation seam, not a domain-purification refactor; introducing a new domain enum is out of scope and would force re-mapping in 4 call sites. Document this as deliberate in Dev Notes. (`ServerManager` already lives in `data.remote`, but the use case is the only piece that imports it from `domain/`; this single seam is the spec-acceptable compromise for V1 — same pattern as `ServerManager` directly imported by `EntityRepositoryImpl`.)

7. **`ObserveLastWebSocketMessageUseCase`** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/ObserveLastWebSocketMessageUseCase.kt`:
   ```kotlin
   class ObserveLastWebSocketMessageUseCase(
       private val webSocketClient: HaWebSocketClient,
   ) {
       operator fun invoke(): StateFlow<Long?> = webSocketClient.lastMessageEpochMs
   }
   ```
   Register `factory { ObserveLastWebSocketMessageUseCase(get()) }` in `DomainModule.kt`. Wire into `DashboardViewModel` constructor.

8. **`HaWebSocketClient.lastMessageEpochMs` exposure** — extend the interface at `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/HaWebSocketClient.kt`:
   ```kotlin
   /**
    * ms-since-epoch of the most recent inbound WebSocket frame parsed successfully.
    * null until the first message of the session arrives. StateFlow so collectors
    * are share-aware and the VM doesn't double-subscribe.
    */
   val lastMessageEpochMs: StateFlow<Long?>
   ```
   Implement in `KtorHaWebSocketClient.kt`:
   - Add `private val _lastMessageEpochMs = MutableStateFlow<Long?>(null)` and expose as read-only.
   - Inside the existing inbound frame loop (`KtorHaWebSocketClient.kt:81` `for (frame in newSession.incoming)`), update timestamp on **every successfully decoded JSON frame** — BEFORE dispatching to handlers. Use `Clock.System.now().toEpochMilliseconds()` (`kotlin.time.Clock`, NOT `kotlinx.datetime` — see Story 4.6 Completion Notes; `kotlin.time` is iOS-Native compatible).
   - On `disconnect()` (line ~231): **do NOT clear** `_lastMessageEpochMs`. The dashboard needs to keep showing "Last updated Xs ago" through the entire reconnect window. Only reset when a *new* message arrives.
   - On a fresh `connect()` with a new session (where the *previous* session's last-message timestamp is no longer relevant because the user has logged out + back in): out of scope here — V1 has no logout flow. Document in Deferred.

9. **`DashboardViewModel.deriveStaleIndicator(connection, lastMessageMs)` mapping rule:**
   ```kotlin
   private fun deriveStaleIndicator(
       connection: ServerManager.ConnectionState,
       lastMessageMs: Long?,
   ): StaleIndicatorUi = when (connection) {
       ServerManager.ConnectionState.Connected -> StaleIndicatorUi(StaleIndicatorKind.Connected)
       ServerManager.ConnectionState.Reconnecting -> StaleIndicatorUi(StaleIndicatorKind.Reconnecting, lastMessageMs)
       ServerManager.ConnectionState.Disconnected,
       ServerManager.ConnectionState.Failed -> StaleIndicatorUi(StaleIndicatorKind.Stale, lastMessageMs)
   }
   ```
   Wire into the existing `combine(...)` in `DashboardViewModel.state` — the inner 4-flow combine already takes `connection`; add `lastMessageMs` flow as the 5th input (or fold into the inner `Sources` data class). Increment the VM's `combine` arity OR group via existing `Sources` pattern; DO NOT cause `combine` to exceed 5 outer flows — refactor into `Sources` if needed.

   **Initial-state edge — `Disconnected` before first connect:** Story 4.6 deferred work item flagged that on cold start, `ConnectionState.Disconnected` is the initial value emitted *before* ServerManager has even attempted to connect, which would currently flash `Stale` for one frame. Resolve by gating `Stale`/`Reconnecting` on having *attempted* a connect: `ServerManager` exposes a derived `hasAttemptedConnection: StateFlow<Boolean>` that flips `true` on the first `attemptConnect()` invocation. The VM treats `Disconnected && !hasAttemptedConnection` as `Connected` (hidden indicator) — equivalently, the initial Loading state of the VM hides the indicator. Cleanest implementation: extend `ServerManager`:
   ```kotlin
   private val _hasAttempted = MutableStateFlow(false)
   val hasAttemptedConnection: StateFlow<Boolean> = _hasAttempted.asStateFlow()
   private suspend fun attemptConnect() {
       _hasAttempted.value = true
       // existing body
   }
   ```
   Add `ObserveHasAttemptedConnectionUseCase` (1-line use case mirroring AC6) and combine in the VM. **Alternative simpler approach acceptable**: in the VM, derive `effectiveConnection = if (lastMessageMs == null && connection != Connected) Connected else connection` — this hides the indicator on cold-start-no-attempt because `lastMessageMs` is null until a real message lands. Use whichever path the dev finds cleaner; the simpler `lastMessageMs == null && connection != Connected → Connected` approach is preferred because it avoids touching `ServerManager`. Document the chosen path in Dev Notes.

10. **Cold-launch with HA unreachable (NFR12, NFR13)** — `DashboardViewModel` already emits `DashboardUiState.Empty`/`Success` from the SQLDelight cache via `getDashboards()` regardless of connection state (Story 4.6 AC4). Verify three behaviors hold:
    - `dashboards.isNotEmpty() && cards.isNotEmpty()` AND `connection != Connected` → `Success(cards = ..., isStale = true, indicator = StaleIndicatorUi(Stale, lastMessageMs = null))`. Cards render at 50% opacity per `EntityCard` `isStale` plumbing (Story 4.4 — already wired). Dashboard fully navigable.
    - `dashboards.isEmpty()` AND `connection != Connected` → `Empty(indicator = StaleIndicatorUi(Stale, null))`. Empty state still shown with indicator.
    - `dashboards.isNotEmpty() && cards.isNotEmpty()` AND `connection == Connected` → indicator `Connected` (hidden), cards at full opacity.
    No new VM code required for this beyond AC9 — the mapping is already correct. **Add a VM unit test asserting all three.**

11. **Reconnect transition (FR32)** — when `ConnectionState` flips `Reconnecting → Connected` AND a new WS frame arrives, the indicator transitions `Reconnecting → Connected` and disappears via `Motion.staleIndicatorFade` fade-out. No user action. Test in VM: emit `Reconnecting` then `Connected` on the connection flow; assert the `state.value.indicator.kind` flips through `Reconnecting → Connected`. Test that `lastMessageEpochMs` updates also propagate (emit a new ms value after reconnect).

12. **Internet-down-LAN-live (FR29)** — when LAN reaches HA but internet is down, `KtorHaWebSocketClient` keeps frames flowing on the LAN URL; `ServerManager.connectionState` stays `Connected`; indicator stays hidden. **No new code** — current `ServerManager.attemptConnect()` already prefers LAN (`KtorHaWebSocketClient.kt:75-85`). Document in Dev Notes that AC is satisfied by existing logic; verify with a `ServerManager` unit test that asserts `connectionState.value == Connected` after a successful LAN connect even with a `null` cloudUrl.

13. **TalkBack/VoiceOver announcement (UX-DR9)** — the indicator's `Modifier.semantics { ... }` MUST set `liveRegion = LiveRegionMode.Polite` and a `contentDescription` that changes between states:
    - `Connected` → `contentDescription = ""` (empty announces nothing).
    - `Stale` with `lastMessageMs != null` → `"Connection lost. Last updated ${seconds} seconds ago."` (matches UX spec §670 verbatim).
    - `Stale` with `lastMessageMs == null` → `"Connection lost. Never connected this session."`
    - `Reconnecting` → `"Reconnecting to your home."`
    The `LiveRegionMode.Polite` makes screen readers announce on change without interrupting current speech. **Important:** the `liveRegion` MUST be applied to a stable parent node, not the per-state inner content (which gets swapped by `AnimatedContent`) — otherwise TalkBack re-announces every animation frame. Apply on the outer `Box(modifier = Modifier.height(24.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = ... })`.

14. **Unknown / malformed WebSocket message resilience (NFR10)** — the data layer must **never crash the dashboard** on:
    - **Unknown HA entity domain** — already absorbed by `HaEntity.kt:167` `else -> HaEntity.Unknown(entityId, state, attributes, lastChanged, lastUpdated, domain)`. Verify by grepping no `error(...)` / `throw` paths in `EntityRepositoryImpl.toHaEntity()` mapping; if any exist, replace with `Unknown` fallback. **Add unit test** `entityRepositoryAbsorbsUnknownDomainAsHaEntityUnknown` in `commonTest`.
    - **Unknown attribute shape** — already absorbed by `MapAnySerializer` (`MapAnySerializer.kt:22`). Non-standard wire types fall through to `toString()` (deferred-work known limitation, Story 1-3 review). Verify by adding a unit test `mapAnySerializerSurvivesUnexpectedJsonShapes` deserializing a payload with mixed primitives + nested arrays + unknown keys.
    - **Malformed JSON frame** (corrupted bytes from the WS) — `KtorHaWebSocketClient.kt:81` frame loop currently does `json.decodeFromString(...)` directly. Wrap in `runCatching { ... }.onFailure { /* log only */ }` so a bad frame logs and is dropped, NOT propagated as an exception that closes the WebSocket. **Add the wrap if not already present** — read the file before editing. The decode wrap MUST NOT fire `_lastMessageEpochMs` update on failure (only on successful decode).
    - **Frame after disconnect** — already safe per `_isConnected.value = false` on close.

15. **Loading state hides the indicator** — `DashboardUiState.Loading` does NOT carry an `indicator` field; the body's `when (state)` branch for `Loading` simply does not render `DashboardHeader`. Verify by reading `DashboardScreen.kt:87` `LoadingContent()` — it renders only the spinner, no header. No change needed.

16. **`DashboardChrome` and switcher behavior unchanged** — Story 4.7 chrome coordinator does not interact with stale state. The dashboard *name* in the navigation bar tab label (Story 4.7 AC1) does NOT receive a stale suffix — the indicator is dashboard-content-level, not chrome-level. Verify `HaNativeNavHost.kt` is untouched in this story.

17. **Previews** — extend `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardPreviews.kt`:
    - `Dashboard_Success_Indicator_Connected` (kind = Connected; verify zero footprint).
    - `Dashboard_Success_Indicator_Stale_RecentMessage` (kind = Stale, lastMessageEpochMs = 30s ago).
    - `Dashboard_Success_Indicator_Stale_NoMessage` (kind = Stale, lastMessageEpochMs = null — verify "--" fallback).
    - `Dashboard_Success_Indicator_Reconnecting` (kind = Reconnecting + spinner visible).
    - `Dashboard_Empty_Indicator_Stale` (cold-launch-no-cards-no-connection; empty state header shows indicator).
    Each preview wraps `HaNativeTheme { ... }`. Drive the body via `DashboardUiState.Success(... indicator = StaleIndicatorUi(...))` directly — no Koin in previews. Total ≥ 5 new previews. Existing ≥ 7 stay; no regressions.

18. **Tests** — extend `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModelTest.kt`:
    - `indicatorIsConnectedWhileServerConnected` — emit `Connected` + non-null `lastMessageMs`; assert `state.indicator.kind == Connected`.
    - `indicatorIsReconnectingDuringReconnect` — emit `Reconnecting`; assert `Reconnecting`.
    - `indicatorIsStaleOnDisconnected` — emit `Disconnected` + non-null `lastMessageMs`; assert `Stale`.
    - `indicatorIsStaleOnFailed` — emit `Failed`; assert `Stale`.
    - `coldLaunchHidesIndicatorBeforeFirstAttempt` — emit `Disconnected` + null `lastMessageMs`; assert `Connected` (hidden) per AC9 simpler-path rule.
    - `coldLaunchWithCacheRendersStaleSuccess` — pre-seed `FakeDashboardRepository` with one dashboard + cards; emit `Disconnected`; assert `Success(isStale=true, indicator.kind=Stale)`.
    - `coldLaunchEmptyAndDisconnectedRendersEmptyWithStale` — pre-seed empty repo; emit `Disconnected`; assert `Empty(indicator.kind=Stale)`.
    - `reconnectFlipsIndicatorToConnected` — sequence `Reconnecting → Connected`; assert state transitions.
    - `lastMessageMsPropagatesToIndicator` — emit a new ms; assert `state.indicator.lastMessageEpochMs` updates.
    - `isStaleCardFlagMatchesIndicatorKind` — for each kind ∈ {Connected, Stale, Reconnecting, Failed}, assert `state.isStale == (indicator.kind != Connected)`.
    Add new `commonTest` for data layer:
    - `KtorHaWebSocketClientTest.lastMessageEpochMsUpdatesOnInboundFrame` — feed a fake `incoming` flow with one valid JSON frame; assert `lastMessageEpochMs.value != null` and approximately equals `Clock.System.now()`.
    - `KtorHaWebSocketClientTest.malformedFrameIsAbsorbedAndDoesNotUpdateTimestamp` — feed a malformed frame; assert no exception propagates AND `lastMessageEpochMs.value` stays null.
    - `EntityRepositoryImplTest.unknownDomainMapsToHaEntityUnknown` — feed a `state_changed` event with `domain = "lawnmower"`; assert the emitted entity is `HaEntity.Unknown` with `domain = "lawnmower"`.
    No Compose UI tests (runner not wired, deferred from earlier stories).

## Tasks / Subtasks

- [x] Task 1: Data layer — last-message timestamp + crash-proofing (AC: 8, 14)
  - [x] 1.1: Extend `HaWebSocketClient` interface with `val lastMessageEpochMs: StateFlow<Long?>`.
  - [x] 1.2: Implement in `KtorHaWebSocketClient` — `MutableStateFlow<Long?>(null)`, update on each successfully decoded inbound frame using `kotlin.time.Clock.System.now().toEpochMilliseconds()`.
  - [x] 1.3: Wrap `json.parseToJsonElement` inside the inbound handler with `runCatching { ... }`; only update timestamp on success. (Already-existing per-frame parse runCatching extended into a dedicated `parseFrameUpdatingTimestamp` helper for testability.)
  - [x] 1.4: Verify unknown-domain routing — `HaEntity.kt:167` `else -> HaEntity.Unknown(...)` factory is the only mapping path; no `error(...)` / `throw` in `EntityRepositoryImpl.toDomain()`.
  - [x] 1.5: No `FakeHaWebSocketClient` in `commonTest` — no fake update needed.

- [x] Task 2: Domain — observe use cases (AC: 6, 7)
  - [x] 2.1: Created `domain/usecase/ObserveConnectionStateUseCase.kt` (accepts `StateFlow<ConnectionState>` directly — relocation seam without forcing a heavy `ServerManager` ctor in tests; identical observable semantics).
  - [x] 2.2: Created `domain/usecase/ObserveLastWebSocketMessageUseCase.kt`.
  - [x] 2.3: Registered both as `factory { ... }` in `di/DomainModule.kt`. Module pulls `serverManager.connectionState` + `webSocketClient.lastMessageEpochMs` here — VM no longer reaches into data layer.
  - [x] 2.4: Replaced `connectionState: StateFlow<...>` VM ctor with `observeConnectionState: ObserveConnectionStateUseCase` + `observeLastWebSocketMessage: ObserveLastWebSocketMessageUseCase`. VM stores the resulting flows in private vals.
  - [x] 2.5: Updated `PresentationModule.kt` `DashboardViewModel { ... }` factory — direct `get<ServerManager>().connectionState` reach removed.

- [x] Task 3: UI models — indicator UIModel + state surface (AC: 4)
  - [x] 3.1: `StaleIndicatorUi` + `StaleIndicatorKind` (sealed class, three data objects) added to `DashboardUiModels.kt`.
  - [x] 3.2: `indicator` field added to `DashboardUiState.Empty` and `.Success` with default `StaleIndicatorUi(StaleIndicatorKind.Connected)`.
  - [x] 3.3: `isStale: Boolean` retained on `Success` — derived from `indicator.kind != Connected`.

- [x] Task 4: ViewModel — wiring + derivation (AC: 9, 10, 11, 12)
  - [x] 4.1: Use cases injected, flows stored as private vals.
  - [x] 4.2: `Sources` data class extended with `lastMessageMs: Long?` — pulled into the inner combine (5-arg overload). Outer combine arity unchanged at 4.
  - [x] 4.3: `deriveStaleIndicator(connection, lastMessageMs)` mapping per AC9 table.
  - [x] 4.4: Cold-start hide rule applied via `effectiveConnection = if (lastMessageMs == null && connection != Connected) Connected else connection` — Path B per AC9.
  - [x] 4.5: `state.indicator` populated in both `Empty` and `Success` branches.
  - [x] 4.6: `isStale` continues to drive per-card opacity (verified by `isStaleCardFlagMatchesIndicatorKind` test).

- [x] Task 5: Composable — `StaleStateIndicator.kt` (AC: 1, 2, 5, 13)
  - [x] 5.1: Three-state composable per AC2 — Connected (0dp Box), Stale (Text), Reconnecting (spinner + Text).
  - [x] 5.2: `Motion.staleIndicatorFade` reused in `AnimatedContent.transitionSpec` via `togetherWith` + `SizeTransform(clip = false)`.
  - [x] 5.3: Live counter implemented inline via `produceState(key1 = lastMessageEpochMs, key2 = state.kind)` ticking once per second from `Clock.System.now()`. `null` lastMessage renders `"Last updated --"` fallback.
  - [x] 5.4: Outer `Box(modifier = ...height(24.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = ... })` per AC13. `liveRegion` lives on the stable parent, not the swapped `AnimatedContent` children.
  - [x] 5.5: `MaterialTheme.colorScheme.primary` (no hardcoded `#F59E0B`).

- [x] Task 6: Integrate into `DashboardScreen` header (AC: 3, 15, 16)
  - [x] 6.1: `DashboardHeader(name, indicator)` now wraps a `Row(verticalAlignment = CenterVertically)` with weighted name `Text` + trailing `StaleStateIndicator`.
  - [x] 6.2: `EmptyDashboardState` extended with `indicator` parameter — renders the indicator next to `dashboardName`, or as a trailing-aligned row when no name is present and the connection is not healthy.
  - [x] 6.3: `LoadingContent` continues to render only the spinner — no header, no indicator.
  - [x] 6.4: `HaNativeNavHost.kt` untouched.

- [x] Task 7: Previews (AC: 17)
  - [x] 7.1: Five new `@Preview`s added to `DashboardPreviews.kt`: Connected, Stale_RecentMessage, Stale_NoMessage, Reconnecting, Empty_Stale.
  - [x] 7.2: `:shared:compileAndroidMain` — green.

- [x] Task 8: Tests (AC: 18)
  - [x] 8.1: Ten indicator tests added to `DashboardViewModelTest.kt`.
  - [x] 8.2: `buildVm` updated with a `lastMessage: StateFlow<Long?>` parameter; both new use cases wired through.
  - [x] 8.3: `KtorHaWebSocketClientTest` added — covers timestamp update on valid frame, malformed-frame absorption (no exception, no timestamp advance), non-object frame absorption, recovery after malformed frame.
  - [x] 8.4: `EntityRepositoryImplTest.unknownDomainMapsToHaEntityUnknown` added — exercises the `HaEntity(...)` factory path used by `EntityRepositoryImpl.toDomain()`.
  - [x] 8.5: `mapAnySerializerSurvivesUnexpectedJsonShapes` added per AC14 to `MapAnySerializerTest.kt`.
  - [x] 8.6: `./gradlew :shared:testAndroidHostTest` — BUILD SUCCESSFUL.
  - [x] 8.7: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL.

## Dev Notes

### Architecture Compliance — Strict Composable → ViewModel → UseCase Boundary

Same boundary as Stories 4.3–4.7 (memorialized in `feedback_compose_architecture.md`). For this story specifically:
- `StaleStateIndicator.kt` lives in `ui.dashboard/` and consumes only `StaleIndicatorUi` + primitives. **No domain imports** in the composable file. The lint grep at `architecture.md` § Compose UI Boundary lines 758-764 must continue to return zero hits — `StaleStateIndicator.kt` and `DashboardPreviews.kt` are NOT in the allowlist (`*ViewModel.kt`, `*UiModels.kt`, `*Mapper.kt`).
- `ObserveConnectionStateUseCase` and `ObserveLastWebSocketMessageUseCase` are 1-line domain-layer wrappers — they import from `data.remote` (`ServerManager`) and `domain.repository` (`HaWebSocketClient`) respectively. The `ServerManager` import in a `domain/` file is a deliberate exception (see AC6 — same pattern as `EntityRepositoryImpl` already imports `ServerManager`); flag in code review if questioned, do not refactor.
- Live-counter timing (every-second tick) lives in the **composable** (`secondsAgoFlow`), not the VM. Rationale: a per-second `StateFlow` upstream would force every collector to re-emit even when no human is looking at the screen, and `produceState` inside the composable scopes the tick to the active composition (paused in background, GC'd on screen leave).

### Resolves Deferred Work from Story 4.6

This story closes two items from `_bmad/outputs/deferred-work.md` § "code review of 4-6-dashboard-screen-empty-state-navigation":
- "`PresentationModule` reads `ServerManager.connectionState` directly (`PresentationModule.kt:25`) — Story 4.8 introduces `ObserveConnectionStateUseCase`." — resolved by AC6.
- "`Disconnected` initial state shows `isStale = true` before first connection attempt (`DashboardViewModel.kt:88`) — revisit alongside Story 4.8 stale indicator." — resolved by AC9 cold-start hide rule.

### Why a Separate UIModel for the Indicator (Not Just `isStale: Boolean`)

The existing `isStale: Boolean` on `DashboardUiState.Success` cannot express the *three-way* split (Connected vs Stale vs Reconnecting) that UX-DR5 requires. We keep `isStale` for backwards compatibility with `EntityCard`'s opacity/suffix wiring (Story 4.4 — already in production) and ADD `indicator: StaleIndicatorUi` for the header-level three-way state. **Both fields are derived from the same `(connection, lastMessageMs)` source — they cannot diverge** because the VM derives them in a single `deriveState` call.

### Why the Composable Owns the Live Counter

Three options were considered:
1. VM exposes `Flow<StaleIndicatorUi>` that re-emits every second with updated `secondsAgo` — wasteful (always-on tick), pollutes downstream `combine` with timer churn, breaks `distinctUntilChanged`.
2. VM exposes `lastMessageEpochMs` only; composable derives `secondsAgo` from `Clock.System.now()` on each recomposition triggered by a 1Hz tick — the chosen path.
3. Push `kotlinx.datetime.Clock` into VM ctor and compute on demand — same complexity as (2) but in the wrong layer.

(2) keeps the VM allocation-free per second, and `produceState(key1 = lastMessageEpochMs)` automatically restarts the tick on key change (e.g. when a new message lands mid-`Stale` — the counter resets to `0s` correctly).

### Initial-State Edge — Cold Start Hide Rule

Two equivalent paths considered (see AC9):
- **Path A — Add `hasAttemptedConnection: StateFlow<Boolean>` to `ServerManager`.** Cleaner conceptually, but requires touching `ServerManager` (production-critical code path), adds a 6th flow to the VM `combine`, and requires a 3rd new use case (`ObserveHasAttemptedConnectionUseCase`).
- **Path B — `lastMessageMs == null && connection != Connected → Connected` in the VM.** No `ServerManager` change, no extra use case, semantically equivalent (an unattempted connect can't have produced a message). Chosen.

Path B has one subtle edge: if `attemptConnect()` succeeds but the *first* message hasn't arrived yet (sub-second window), the indicator briefly shows `Connected` even though `ConnectionState.Connecting` is technically active — but `ServerManager` flips to `Connected` only *after* `webSocketClient.isConnected == true`, which itself flips on auth-success-frame arrival, which DOES update `lastMessageEpochMs`. So in practice the window is zero-width.

### `runCatching` Wrap on JSON Decode

Read `KtorHaWebSocketClient.kt:81-90` before editing. If the decode is already inside a `try { ... } catch` or `runCatching`, expand the catch scope to also gate the `_lastMessageEpochMs.value = ...` update. If raw decode is in flight, wrap with `runCatching { json.decodeFromString(...) }.fold(onSuccess = { _lastMessageEpochMs.value = now; dispatch(it) }, onFailure = { /* log */ })`. Never let a decode failure propagate up the `for (frame in incoming)` loop — that closes the WebSocket session per Ktor semantics.

### Color Token Choice — `colorScheme.primary` Not `colorScheme.error`

Per UX spec line 405-406: "Stale indicator shows timestamp ('43s ago') not generic 'offline' — honest and specific." This is a **trust signal**, not an error. `colorScheme.primary` (amber `#F59E0B`) is the deliberate choice — disconnected ≠ broken. `colorScheme.error` (`#FF453A` red) is reserved for `ActionRejected` / delete confirmation. Do not substitute.

### Testing Standards (mirrors Stories 4.6 / 4.7)

- `kotlin.test` only — never JUnit4/5 in `commonTest`.
- `./gradlew :shared:testAndroidHostTest` for VM + repository + WS-client tests.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` as the iOS gate.
- `kotlinx.coroutines.test.runTest` with `StandardTestDispatcher`; `Dispatchers.setMain(dispatcher)` in `@BeforeTest`.
- Use a `MutableStateFlow<Long?>` for the fake `lastMessageEpochMs` so tests can drive the indicator state directly.
- For `StaleStateIndicator` itself — no Compose UI test runner; coverage via VM-derivation unit tests + `@Preview` matrix only. Document explicitly in Completion Notes that the composable is not under runtime test.
- `KtorHaWebSocketClientTest` lives in `commonTest` if a host-friendly fake `WebSocketSession` exists today (check Story 3.3 fixtures); otherwise place in `androidUnitTest`.

### Build Gotchas

- `kotlin.time.Clock` (NOT `kotlinx.datetime.Clock`) for iOS-Native compatibility — same precedent as `EntityCard.kt` (Story 4.3) and `DashboardViewModel.kt` (Story 4.6 Completion Notes).
- `androidx.compose.ui.semantics.LiveRegionMode` is **stable** in CMP 1.10.0 (`androidx.compose.ui.semantics.liveRegion`). Confirm via `import androidx.compose.ui.semantics.LiveRegionMode`. If the IDE flags it as unresolved, the import path may have moved — fall back to the older `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` import path.
- `AnimatedContent` from `androidx.compose.animation` is stable. `transitionSpec` `togetherWith` + `using SizeTransform(clip = false)` is the idiomatic API for cross-fade-without-layout-thrash. Verify on Android first (CMP iOS pulls the same artifact).

### Git Intelligence

| Commit  | What it established                                                                  |
| ------- | ------------------------------------------------------------------------------------ |
| `48cc892` | Story 4.6 + 4.7 dashboard screen + CRUD/switcher with review patches                |
| `90a1a65` | Compose UI Boundary doctrine across architecture, epics, ui pkg                      |
| `eb5e53b` | EntityCard ViewModel + UIModel boundary refactor — `isStale` plumbing source-of-truth|
| `c4437d3` | Story 4.5 `EntityPicker`                                                             |
| `d4fc4d3` | Story 4.4 stepper/trigger/media/unknown variants — `isStale` per-card dim wired here |
| `e158f57` | Story 4.2 `DashboardRepository` + use cases                                          |
| `0adc61c` | Story 4.1 `EntityState.sq` + entity pipeline                                         |
| (earlier) | Story 3.3 `ServerManager` exponential backoff + `connectionState` flow              |

Codebase state confirmed pre-implementation:
- `Motion.kt:29` — `staleIndicatorFade: TweenSpec<Float> = tween(durationMillis = 300)` already exists. Reuse.
- `Color.kt:10` — `accent = Color(0xFFF59E0B)` mapped to `colorScheme.primary` (`Color.kt:18`). Use `colorScheme.primary`.
- `ServerManager.kt:122-127` — `ConnectionState` sealed class has 4 variants: `Connected, Reconnecting, Disconnected, Failed`. Map per AC9.
- `ServerManager.kt:27` — `connectionState: StateFlow<ConnectionState>` already exposed.
- `KtorHaWebSocketClient.kt:81` — `for (frame in newSession.incoming)` is the inbound loop where the `lastMessageEpochMs` update is wired.
- `KtorHaWebSocketClient.kt:45-46` — `_isConnected: MutableStateFlow<Boolean>` precedent for the new `_lastMessageEpochMs`.
- `HaEntity.kt:5` — `sealed class HaEntity` with `Unknown` variant at line 137. `HaEntity.kt:167` — domain-string fallback to `Unknown` already wired.
- `EntityRepositoryImpl.kt:6` — already imports `MapAnySerializer`.
- `DashboardViewModel.kt:56` — current `connectionState: StateFlow<ServerManager.ConnectionState>` ctor param is the seam to swap.
- `DashboardViewModel.kt:106-124` — current `combine(...)` chain. Inner combine at lines 107-112 takes 4 flows (`Sources`); outer at 106-118 takes 4. Fold `lastMessageMs` into `Sources` to keep outer ≤ 5.
- `DashboardViewModel.kt:232` — current `isStale = connection != ServerManager.ConnectionState.Connected`. Keep.
- `DashboardScreen.kt:271-280` — current `DashboardHeader(name: String)` to extend.
- `DashboardScreen.kt:196-203` — `EmptyDashboardState` dashboard-name slot; receives indicator here too.
- `DashboardUiModels.kt:9-16` — `Success` data class; add `indicator` field.
- `PresentationModule.kt:32` — direct `get<ServerManager>().connectionState` reach to remove.
- `DomainModule.kt` — append two new use case factories.

### Project Structure — Files Touched

```
shared/src/commonMain/kotlin/com/backpapp/hanative/
  ├── data/
  │   └── remote/
  │       └── KtorHaWebSocketClient.kt              ← MODIFIED (+ _lastMessageEpochMs, runCatching wrap)
  ├── domain/
  │   ├── repository/
  │   │   └── HaWebSocketClient.kt                  ← MODIFIED (+ lastMessageEpochMs: StateFlow<Long?>)
  │   └── usecase/
  │       ├── ObserveConnectionStateUseCase.kt      ← NEW
  │       └── ObserveLastWebSocketMessageUseCase.kt ← NEW
  ├── di/
  │   ├── DomainModule.kt                           ← MODIFIED (+ 2 use case factories)
  │   └── PresentationModule.kt                     ← MODIFIED (DashboardViewModel deps swap)
  └── ui/
      └── dashboard/
          ├── DashboardScreen.kt                    ← MODIFIED (header Row; pass indicator to Empty + Success)
          ├── DashboardUiModels.kt                  ← MODIFIED (+ StaleIndicatorUi, StaleIndicatorKind, indicator field)
          ├── DashboardViewModel.kt                 ← MODIFIED (+ 2 use case deps, deriveStaleIndicator)
          └── StaleStateIndicator.kt                ← NEW

shared/src/androidMain/kotlin/com/backpapp/hanative/
  └── ui/
      └── dashboard/
          └── DashboardPreviews.kt                  ← MODIFIED (+ 5 indicator previews)

shared/src/commonTest/kotlin/com/backpapp/hanative/
  ├── data/
  │   ├── remote/
  │   │   └── KtorHaWebSocketClientTest.kt          ← NEW (or MODIFIED if exists)
  │   └── repository/
  │       └── EntityRepositoryImplTest.kt           ← NEW or MODIFIED (unknown-domain test)
  └── ui/
      └── dashboard/
          └── DashboardViewModelTest.kt             ← MODIFIED (+ 10 indicator tests)
```

**Do NOT modify:**
- `Motion.kt` — `staleIndicatorFade` already exists.
- `Color.kt` / `HaNativeTheme.kt` — color tokens already mapped.
- `EntityCard.kt` / `EntityCardViewModel.kt` — `isStale` plumbing already wired (Stories 4.3/4.4).
- `EntityPicker.kt` / `DashboardSwitcherSheet.kt` — switcher chrome unchanged.
- `HaNativeNavHost.kt` — nav-bar chrome unchanged; tab label is dashboard-name only (Story 4.7).
- `Dashboard.sq` / `DashboardCard.sq` / `EntityState.sq` — schema unchanged.
- `IdGenerator.kt` / `HaSettingsKeys.kt` — unrelated.
- `ActiveDashboardRepository*` / `DashboardRepository*` / `RenameDashboardUseCase` — Story 4.7 surface, unchanged.
- `ServerManager.kt` — **prefer** to leave untouched (Path B in AC9). Only edit if the dev chooses Path A — flag in Dev Notes as deviation.

### Latest Tech Notes

- **`androidx.compose.ui.semantics.LiveRegionMode`** — stable in CMP 1.10.0. `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` is the idiomatic announcement primitive for a live-updating staleness counter.
- **`androidx.compose.animation.AnimatedContent`** — stable. `transitionSpec` accepts `(EnterTransition, ExitTransition)` via `togetherWith`. `SizeTransform(clip = false)` prevents layout-shift artifacts when the inner content's intrinsic size differs across states (e.g. `Connected` 0dp vs `Stale` ~100dp).
- **`androidx.compose.runtime.produceState`** — the right primitive for "render a value that changes on a timer scoped to composition lifetime". `LaunchedEffect` + `mutableStateOf` is equivalent but more verbose.
- **`kotlin.time.Clock.System.now().toEpochMilliseconds()`** — KMP-stable replacement for `System.currentTimeMillis()`. Already used in `DashboardViewModel.kt` (Story 4.6).
- **Ktor `incoming` Flow semantics** — `for (frame in incoming)` consumes one frame at a time. A thrown exception inside the loop closes the session; `runCatching` is the correct quarantine.

### References

- [Source: `_bmad/outputs/epics.md#Story 4.8`] — Acceptance criteria
- [Source: `_bmad/outputs/prd.md#FR29, FR32, FR33`] — LAN-first, auto-reconnect, last-known-state-on-disconnect
- [Source: `_bmad/outputs/prd.md#NFR10, NFR12, NFR13`] — Crash resilience, cold launch, offline glanceability
- [Source: `_bmad/outputs/ux-design-specification.md:499-503`] — `StaleStateIndicator` anatomy + three states (UX-DR5)
- [Source: `_bmad/outputs/ux-design-specification.md:620`] — `StaleStateIndicator` fade-in 300ms tween
- [Source: `_bmad/outputs/ux-design-specification.md:670`] — TalkBack/VoiceOver announcement string (UX-DR9)
- [Source: `_bmad/outputs/ux-design-specification.md:478-487`] — `EntityCard` stale state row dimming + inline timestamp (already wired)
- [Source: `_bmad/outputs/ux-design-specification.md:603`] — App-open-HA-unreachable cold-launch behavior
- [Source: `_bmad/outputs/architecture.md:177-181`] — Reconnect with exponential backoff
- [Source: `_bmad/outputs/architecture.md:320-326`] — Cold launch sequence: SQLDelight cache → Success(isStale=true) → live events
- [Source: `_bmad/outputs/architecture.md:54`] — Offline / stale state architectural principle
- [Source: `_bmad/outputs/architecture.md:739-765`] — Compose UI Boundary rules + lint grep
- [Source: `_bmad/outputs/deferred-work.md`] — Closes two Story 4.6 deferred items (PresentationModule reach + cold-start Disconnected flash)
- [Source: `_bmad/stories/4-6-dashboard-screen-empty-state-navigation.md`] — `DashboardViewModel` baseline + `isStale` derivation + slot pattern
- [Source: `_bmad/stories/4-7-dashboard-management-crud-switcher.md`] — `DashboardChrome` + sheet scaffolding (chrome unchanged here)
- [Source: `_bmad/stories/4-4-entitycard-stepper-trigger-media-unknown-variants.md`] — `EntityCard.isStale` plumbing (consumed as-is)
- [Source: `_bmad/stories/3-3-ha-websocket-connection-session-persistence.md`] — `ServerManager` + `KtorHaWebSocketClient` infrastructure
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Motion.kt:29`] — `staleIndicatorFade` 300ms tween
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Color.kt:10,18`] — `accent` → `colorScheme.primary`
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/ServerManager.kt:27,122-127`] — `connectionState: StateFlow<ConnectionState>` + 4-state sealed class
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/KtorHaWebSocketClient.kt:45-46,81-90`] — `_isConnected` precedent + inbound frame loop
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/HaEntity.kt:5,137,167`] — `HaEntity.Unknown` fallback already wired
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/MapAnySerializer.kt:22`] — Unknown-attribute-shape resilience already present
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModel.kt:56,106-124,232`] — Connection-state seam, combine chain, isStale derivation
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardScreen.kt:271-280,196-203,87`] — Header to extend, empty-state header, loading-no-header
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardUiModels.kt:9-16`] — `Success` data class to extend
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt:32`] — Direct `ServerManager` reach to remove
- [Source: `gradle/libs.versions.toml`] — CMP 1.10.0, Kotlin 2.3.20, Koin 4.2.1

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 (Claude Code, 2026-05-01)

### Debug Log References

- `./gradlew :shared:testAndroidHostTest` — BUILD SUCCESSFUL (Android host tests, all VM + WS-client + repository + serializer tests green)
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL (iOS link gate)
- `./gradlew :androidApp:assembleDebug` — BUILD SUCCESSFUL (full debug APK builds, header integration compiles)

### Completion Notes List

- **AC9 cold-start hide path:** chose Path B (VM-level `lastMessageMs == null && connection != Connected → Connected`) per spec preference — `ServerManager` left untouched, no `ObserveHasAttemptedConnectionUseCase` introduced.
- **`ObserveConnectionStateUseCase` / `ObserveLastWebSocketMessageUseCase`** accept the `StateFlow` directly rather than a heavy `ServerManager` / `HaWebSocketClient` ctor. The Koin `DomainModule` does the single-line reach (`get<ServerManager>().connectionState`, `get<HaWebSocketClient>().lastMessageEpochMs`) and the VM no longer touches `data.remote` directly. Same architectural intent as the spec ctor signatures — observability surface unchanged.
- **`KtorHaWebSocketClient.parseFrameUpdatingTimestamp`** is `internal` to the `data.remote` package — single source of truth for the malformed-frame quarantine + timestamp update. Called from `handleFrame`; tested directly.
- **Live counter** runs inside the composable via `produceState(key1 = lastMessageEpochMs, key2 = state.kind)`. The tick auto-restarts on key change (new message lands → counter resets to `0s`) and is GC'd when the dashboard leaves composition. VM stays allocation-free per second.
- **`liveRegion = LiveRegionMode.Polite`** lives on the outer `Box` (stable parent) so TalkBack/VoiceOver does not re-announce on every `AnimatedContent` child swap.
- **Color token:** `MaterialTheme.colorScheme.primary` (already maps to amber `#F59E0B` per `Color.kt:18`). No hardcoded hex.
- **Story 4.6 deferred items resolved:**
  - `PresentationModule` direct `ServerManager.connectionState` reach removed → relocated to `DomainModule` via `ObserveConnectionStateUseCase`.
  - Cold-start `Disconnected` indicator flash eliminated by AC9 Path B rule.
- **No Compose UI test runner wired** — coverage is via VM-derivation tests (10 new) + 5 new `@Preview`s. Documented per Dev Notes "Testing Standards".
- **Out-of-scope confirmed:** `ServerManager` not modified; `HaNativeNavHost` not modified; `EntityCard` `isStale` plumbing reused as-is; logout-flow timestamp reset deferred (no logout in V1).

### File List

**Modified:**
- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` _(P11 — expect class now implements `LifecycleForegrounder`)_
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` _(P11 — actual now `: LifecycleForegrounder`)_
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` _(P11 — actual now `: LifecycleForegrounder`)_
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/ServerManager.kt` _(P11 — `lifecycleObserver` typed as `LifecycleForegrounder` instead of `AppLifecycleObserver`)_
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt` _(P11 — explicit `get<AppLifecycleObserver>()` for ServerManager wire-in)_
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/KtorHaWebSocketClient.kt` — `_lastMessageEpochMs` + `parseFrameUpdatingTimestamp` quarantine helper; `kotlin.time.Clock` import.
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/HaWebSocketClient.kt` — interface adds `lastMessageEpochMs: StateFlow<Long?>`.
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DomainModule.kt` — registers `ObserveConnectionStateUseCase` + `ObserveLastWebSocketMessageUseCase`.
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt` — `DashboardViewModel` factory now wires use cases instead of raw `ServerManager.connectionState`.
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardUiModels.kt` — `StaleIndicatorUi`, `StaleIndicatorKind`; `indicator` field on `Empty` + `Success`.
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModel.kt` — use case injection, `Sources` extended with `lastMessageMs`, `deriveStaleIndicator`, cold-start hide rule.
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardScreen.kt` — `DashboardHeader(name, indicator)` row, `EmptyDashboardState` indicator slot.
- `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/dashboard/DashboardPreviews.kt` — 5 new `@Preview`s.
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/dashboard/DashboardViewModelTest.kt` — 10 new indicator tests, `buildVm` updated.
- `shared/src/commonTest/kotlin/com/backpapp/hanative/data/remote/MapAnySerializerTest.kt` — `mapAnySerializerSurvivesUnexpectedJsonShapes`.

**New:**
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/ObserveConnectionStateUseCase.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/ObserveLastWebSocketMessageUseCase.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/dashboard/StaleStateIndicator.kt`
- `shared/src/commonTest/kotlin/com/backpapp/hanative/data/remote/KtorHaWebSocketClientTest.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/LifecycleForegrounder.kt` _(added during review patch P11 — test-friendly interface seam over `AppLifecycleObserver`)_
- `shared/src/commonTest/kotlin/com/backpapp/hanative/domain/model/HaEntityFactoryTest.kt` _(renamed from `data/repository/EntityRepositoryImplTest.kt` during review patch P6 — file deleted at the old path)_
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/dashboard/StaleStateIndicatorTest.kt` _(added during review patch P15 — boundary tests for `formatStaleAgo`)_
- `shared/src/commonTest/kotlin/com/backpapp/hanative/data/remote/ServerManagerTest.kt` _(added during review patch P11 — AC12 LAN-live-internet-down verification + LAN-preferred-over-cloud)_

### Change Log

| Date       | Change                                                                                                       |
| ---------- | ------------------------------------------------------------------------------------------------------------ |
| 2026-05-01 | Story 4.8 implemented — StaleStateIndicator composable, indicator UIModel, ObserveConnectionStateUseCase + ObserveLastWebSocketMessageUseCase, last-message timestamp on WS client, malformed-frame quarantine, cold-start hide rule, 10 VM tests + 5 previews + 3 data-layer tests. Closes Story 4.6 deferred items. |
| 2026-05-02 | Code review patches applied — 14 of 15 patches batch-applied (P11 deferred to follow-up). Failed-state cold-start carve-out, frame-type-gated timestamp update, Xm/Xh/Xd duration formatter, future-timestamp clamp, sync initial counter, Stale-only ticker, aligned first delay, stable contentDescription via `remember`, outer frame-loop quarantine, `CancellationException` rethrow, shared `HttpClient` in tests, `EntityRepositoryImplTest` renamed → `HaEntityFactoryTest`, new `StaleStateIndicatorTest`. `:shared:testAndroidHostTest` + `:shared:linkDebugFrameworkIosSimulatorArm64` BUILD SUCCESSFUL. |
| 2026-05-02 | P11 follow-up — `LifecycleForegrounder` interface seam introduced; `AppLifecycleObserver` (both actuals) implements it; `ServerManager.lifecycleObserver` typed as the interface so tests inject a no-op fake. New `ServerManagerTest` covers AC12 (LAN-live, null cloud URL → Connected) and LAN-preferred-over-cloud. `UnconfinedTestDispatcher` used to bypass the StandardTestDispatcher ordering race between the `isConnected` collector and `connect()`'s post-attempt state check. Both gates green. Story status → done. |

### Review Findings

_Code review (BMAD adversarial + edge-case + acceptance audit) — 2026-05-01. 4 decisions, 12 patches, 7 deferred, 8 dismissed._

#### Decisions Resolved

- [x] [Review][Decision] **D1 — Cold-start hide rule + `Failed`** — Resolved: carve out `Failed` from Path B. `Failed` always renders `Stale`; `Disconnected`/`Reconnecting` still hidden when `lastMessageMs == null`. Promoted to patch P13.
- [x] [Review][Decision] **D2 — Use case ctor signatures** — Resolved: accept deviation. `StateFlow<...>` ctor stays; Completion Notes already document the rationale. No code change.
- [x] [Review][Decision] **D3 — Timestamp update gate** — Resolved: restrict timestamp write to `event` / `result` frames only. Promoted to patch P14.
- [x] [Review][Decision] **D4 — Seconds unit conversion** — Resolved: Xm / Xh / Xd ladder (`<60s → s`, `<3600s → m`, `<86400s → h`, else `d`). Promoted to patch P15.

#### Patches

- [x] [Review][Patch] **P1** — `liveRegion = Polite` description now `remember(state.kind, state.lastMessageEpochMs)` snapshot — re-announces only on kind / lastMessage change, not on every second tick. [StaleStateIndicator.kt:51]
- [x] [Review][Patch] **P2** — Subsumed by P4 + P9 below. Initial value computed synchronously eliminates the flash; ticker gated on `kind == Stale` eliminates the wasted Reconnecting recomposition. `key2 = state.kind` retained intentionally (re-evaluates initial sync compute on kind change). [StaleStateIndicator.kt:33]
- [x] [Review][Patch] **P3** — Future-timestamp clock-skew now renders `--` fallback instead of `0s ago`. `delta < 0L → value = null`. Both label and contentDescription handle the null case explicitly. [StaleStateIndicator.kt:44]
- [x] [Review][Patch] **P4** — `produceState` `initialValue` computed synchronously via `state.lastMessageEpochMs?.let { ms -> ... (now - ms) / 1000L }`. No more `0s ago` flash on first frame. [StaleStateIndicator.kt:34-37]
- [x] [Review][Patch] **P5** — `KtorHaWebSocketClientTest` now uses a single shared `HttpClient` + `@AfterTest tearDown` — no more leaks across 5 tests. [KtorHaWebSocketClientTest.kt:21-26]
- [x] [Review][Patch] **P6** — `EntityRepositoryImplTest.kt` deleted; replaced by `HaEntityFactoryTest.kt` at `shared/src/commonTest/kotlin/com/backpapp/hanative/domain/model/`. Class name now matches what it actually tests.
- [x] [Review][Patch] **P7** — `KtorHaWebSocketClientTest` file-level comment now documents that the internal-helper test contract is intentional (commonTest can't construct a fake `DefaultClientWebSocketSession` without pulling MockEngine fixture). [KtorHaWebSocketClientTest.kt:13-17]
- [x] [Review][Patch] **P8** — `parseFrameUpdatingTimestamp` `runCatching` now re-throws `CancellationException` via `getOrElse { if (it is CancellationException) throw it; null }`. [KtorHaWebSocketClient.kt:140-144]
- [x] [Review][Patch] **P9** — Ticker gated on `kind == Stale` — `if (state.kind != StaleIndicatorKind.Stale) return@produceState`. No more wasted recomposition during Reconnecting. [StaleStateIndicator.kt:39]
- [x] [Review][Patch] **P10** — First-iteration delay aligned to next second boundary: `(1000L - delta % 1000L).coerceIn(1L, 1000L)`. [StaleStateIndicator.kt:50-53]
- [x] [Review][Patch] **P11** — `ServerManagerTest` added with `lanLiveWithNullCloudUrlReachesConnected` (AC12 verbatim) + `lanReachableEvenIfCloudIsUnreachableStillConnects`. Required a small structural seam: extracted `LifecycleForegrounder` interface, `AppLifecycleObserver` now implements it, `ServerManager.lifecycleObserver` typed as `LifecycleForegrounder` so a test fake can replace the platform actual. Tests use `UnconfinedTestDispatcher` to bypass the StandardTestDispatcher ordering race between the `isConnected` collector and `connect()`'s post-attempt state check. [shared/src/commonTest/kotlin/com/backpapp/hanative/data/remote/ServerManagerTest.kt]
- [x] [Review][Patch] **P12** — Outer frame loop now wraps `handleFrame(...)` in `try/catch` — re-throws `CancellationException`, logs other Throwables. Bad frame no longer closes the session. [KtorHaWebSocketClient.kt:88-99]
- [x] [Review][Patch] **P13** — VM `effectiveConnection` carve-out: `Failed` is never masked by the cold-start hide rule. New test `coldStartFailedShowsStaleNotHidden` asserts the behavior. [DashboardViewModel.kt:240-246]
- [x] [Review][Patch] **P14** — `_lastMessageEpochMs` updates only when `obj["type"] in {"event", "result"}`. New tests: `lastMessageEpochMsUpdatesOnResultFrame`, `lastMessageEpochMsDoesNotUpdateOnAuthFrames`, `lastMessageEpochMsDoesNotUpdateOnUnknownType`. [KtorHaWebSocketClient.kt:138-148]
- [x] [Review][Patch] **P15** — `formatStaleAgo(seconds: Long)` helper added — `<60→Xs`, `<3600→Xm`, `<86400→Xh`, else `Xd`. Applied to both visible label and contentDescription. New `StaleStateIndicatorTest` covers the four boundaries. [StaleStateIndicator.kt:121-126]

#### Deferred

- [x] [Review][Defer] Loading state has no `indicator` field — if WS down at startup, user sees spinner only (no Reconnecting affordance). Out of scope per AC15. Capture for future story.
- [x] [Review][Defer] String "Last updated Xs ago" / "Reconnecting…" hardcoded English — no i18n seam. Out of scope V1.
- [x] [Review][Defer] Path B hides `Reconnecting` when `lastMessageMs == null` — true cold-start reconnecting attempt is rendered as Connected. Spec endorsed Path B trade-off; revisit if user reports confusion.
- [x] [Review][Defer] `combine` ordering race could flash `Stale` for one frame at startup before `connection` and `lastMessageMs` reach a coherent pair. `distinctUntilChanged` mitigates, doesn't eliminate intermediate emissions. Add emission-list tests if regressions appear.
- [x] [Review][Defer] No `CompositionLocal<Clock>` for `StaleStateIndicator` testability — Compose UI test runner not wired (deferred from earlier stories), so testability seam is moot until runner lands.
- [x] [Review][Defer] VM tests don't exercise time-component staleness — Story 4.8 staleness is binary (kind-driven) not time-thresholded. Out of scope.
- [x] [Review][Defer] `Failed` vs `Disconnected` user-facing differentiation — partly captured in Decision 1; if Decision 1 keeps Path B, consider a fourth indicator kind for `Failed` in a follow-up story.

#### Dismissed (8)

- `parseFrameUpdatingTimestamp` monotonicity (single-coroutine reader, atomic StateFlow.value writes — no real hazard).
- `Modifier.size(0.dp)` placeholder + 24.dp reserved height (by design per AC1; layout stability requirement).
- `_lastMessageEpochMs` not cleared on disconnect (explicitly mandated by AC8).
- `System.currentTimeMillis()` in `DashboardPreviews.kt` (file is in `androidMain`; JVM API allowed).
- Modifier order `weight + semantics` (idiomatic, semantics propagate via merge).
- AC9 inner combine arity 5 (compliant — outer combine arity unchanged at 4).
- `Box {}` vs `Box(Modifier.size(0.dp))` for Connected state (functionally identical).
- `DashboardHeader` row jiggle on Stale→Connected transition (visual nit inside fixed `.height(24.dp)` outer Box; not actionable from diff).
