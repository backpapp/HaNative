# Deferred Work

## Deferred from: code review of 1-2-clean-architecture-package-structure-di (2026-04-25)

- **koin.android dual declaration** — declared in both `androidApp/build.gradle.kts` and `shared:androidMain`; spec-compliant, Gradle deduplicates; no action needed until dependency graph grows complex.
- **iOS Koin init order** — `KoinHelperKt.doInitKoin()` called in `App.init()` before UIApplication ready; safe with empty modules; revisit when any Koin binding touches platform APIs requiring a live UIApplication.
- **DataStore iOS storage path** — `datastore-preferences-core` in commonMain has no iOS-side OkioStorage/file path factory wired; required before DataStore is instantiated on iOS (Story 3.x+).
- **koin-compose not in shared module** — `koin-compose` only in `androidApp`; add to shared when shared composables use `koinInject()` or `getViewModel()`.
- **SQLDelight plugin not applied** — runtime artifact declared but no `.sq` schema files and no SQLDelight Gradle plugin in `shared/build.gradle.kts`; add plugin when first `.sq` file is created (Story 4.1).
- **lifecycle-viewmodel:2.9.0 iOS KMP artifact** — iOS build not verified in Story 1.2; confirm native artifact exists and is compatible with Compose Multiplatform 1.10.0 when iOS build pipeline is established.
- **ktor-client-cio TLS/WebSocket gaps on Android API < 29** — CIO engine has TLS cipher gaps on API 24–28; evaluate switching to OkHttp engine when WebSocket networking is implemented (Story 1.3+).
- **iOS Koin main-thread init** — `doInitKoin()` runs on main thread; any future module factory using `runBlocking` or I/O will deadlock; configure a dispatcher when real bindings are added.

## Deferred from: code review of 1-3-port-ha-source-components (2026-04-26)

- **AuthenticationRepositoryImpl has no domain interface** — `ServerManager` couples to concrete `AuthenticationRepositoryImpl` type; no `AuthenticationRepository` interface in `domain/repository/`. Pre-existing architecture gap; revisit when Story 3.x adds OAuth token refresh.
- **MapAnySerializer unknown-type fallback** — non-standard types (Set, custom objects) silently serialize via `toString()`, round-trip back as String. Low real-world impact for HA wire format (HA only sends JSON primitives, arrays, maps).
- **InternalSerializationApi opt-in in MapAnySerializer** — required for `buildSerialDescriptor` with `SerialKind.CONTEXTUAL`; fragile against future kotlinx.serialization upgrades. Track when upgrading serialization library version.
- **counter integer overflow in nextId()** — `counter++` wraps to Int.MIN_VALUE after ~2B calls; negligible probability within a session lifetime. Use `if (counter == Int.MAX_VALUE) 1 else counter + 1` when refactoring KtorHaWebSocketClient.
- **entityId.substringBefore(".") no-dot edge case** — malformed entity id without dot falls to `HaEntity.Unknown` with full string as domain. Safe fallback; acceptable for current scope.

## Deferred from: code review of 1-4-compose-navigation-3-platform-entry-points (2026-04-26)

- **Custom `WindowSizeClass` enum vs Jetpack type** — Hand-rolled `enum class WindowSizeClass` in `LocalWindowSizeClass.kt` duplicates `androidx.compose.material3.windowsizeclass.WindowSizeClass` with identical breakpoints. Intentional placeholder; Story 2.4 replaces with full `androidx.window` integration.
- **`remember(screenWidthDp)` key misses height-only resize** — `MainActivity` keys `WindowSizeClass` computation on `screenWidthDp` only; height-only resize (foldables, split-screen) does not recompute. Acceptable for Story 1.x placeholder; Story 2.4 replaces whole mechanism with lifecycle-aware window metrics.
- **`@Serializable` routes — no state restoration wired** — `OnboardingRoute`/`DashboardRoute` are `@Serializable` but `rememberNavBackStack` has no `Saver` for process-death restoration. Nav3 requires `@Serializable` for type-safe routing; full back-stack persistence is a future concern.

## Deferred from: code review of 1-5-github-actions-ci-ha-upstream-diff-watcher (2026-04-27)

- **`workflow_dispatch` has no `ref` input** — manual runs always diff against the default branch snapshot; cannot target a feature branch with updated snapshots for pre-merge validation. Add `inputs.ref` when branch-based manual validation is needed.
- **Action `uses:` not pinned to commit SHAs** — all three workflow files use floating version tags (`@v4`); supply-chain best practice for workflows with `issues: write` + `GITHUB_TOKEN` is SHA pinning. Harden when security posture requires it.
- **No `timeout-minutes` on any job** — a hung Gradle daemon, stalled `xcodebuild`, or `curl` that never completes will consume runner minutes until GitHub's 6-hour default timeout. Add per-job timeouts (30 min Android, 45 min iOS) when CI costs become a concern.
- **`macos-latest` non-pinned runner** — Xcode version may change silently on GitHub runner image updates, potentially breaking the iOS build without a code change. Pin to a specific `macos-15` or similar when build reproducibility is required.

## Deferred from: code review of 3-2-server-discovery-app-lifecycle-observer (2026-04-28)

- **`stopDiscovery()` no-op on both platforms** — documented design choice; `awaitClose` in `callbackFlow` handles teardown on scope cancellation. Interface method exists for contract completeness. Revisit if callers need mid-stream stop without cancelling the collector.
- **Callbacks fire into `discovered` after flow cancellation** — pre-existing `callbackFlow` limitation; in-flight NSD/NSNetService resolves complete after `awaitClose`. `trySend` is safe post-close; mutations are harmless at MVP scale. Revisit in Story 3.3.
- **Tests cover only FakeServerDiscovery, zero real platform impl coverage** — documented in story; `AppLifecycleObserver` and real `ServerDiscovery` impls require instrumented tests or manual verification. Functional verification deferred to Story 3.3.

## Deferred from: code review of 3-3-ha-websocket-connection-session-persistence (2026-04-30)

- **`HttpClient` never closed** — `httpClientModule()` provides `HttpClient` as Koin `single` with no `onClose` teardown hook. Ktor `HttpClient` holds native resources (CIO thread pool / Darwin NSURLSession) that leak if the module is ever replaced or Koin reloads.
- **`MainScope()` as `single<CoroutineScope>` never cancelled** — `serverManagerModule()` binds `MainScope()` as a DI singleton with no Koin `onClose { scope.cancel() }`. App-level lifecycle concern; revisit when shutdown/test teardown hooks are needed.
- **`onForeground` callback not deregistered after `disconnect()`** — `lifecycleObserver.onForeground { triggerReconnect() }` registered in `initialize()` persists after `disconnect()`; next foreground event re-enters `Reconnecting` state. Requires `AppLifecycleObserver` deregistration API.
- **Race between `reconnectJob?.cancel()` and in-flight `waitThenAttempt`** — coroutine cancellation is cooperative; `attemptConnect()` may complete and call `scheduleReconnect()` after the `isConnected` collector already set state to `Connected`. Mitigated by P2+P3 patches; residual risk acceptable for MVP.
- **`initialize()` allows multiple registrations** — no guard against re-entry; each call appends another `onForeground` callback and launches a duplicate `isConnected` collector. No current multi-call site; add guard when call sites are established.

## Deferred from: code review of 3-4-onboarding-ha-url-entry-connection-test (2026-04-30)

- **`AuthRoute` is placeholder `Box` in `HaNativeNavHost.kt`** — Story 3.5 fills it; required to ship Story 3.4.
- **`AuthRoute` restoration after process death yields blank screen with no nav out** — depends on Story 3.5/3.6 content + back-suppression policy.
- **`stopDiscovery()` double-call risk** — Android `NsdManager.stopServiceDiscovery` after stop throws `IllegalArgumentException`; idempotency not verified for current platform impls. Verify in integration test.
- **`koinViewModel()` scope vs nav3 entry lifecycle** — VM may rebuild on Onboarding → Auth → back navigation; architectural concern when Auth + Dashboard wired.
- **Process death mid-Loading silently resets `uiState` to `Idle`** — `urlInput` restored via `rememberSaveable`, but no in-flight indicator returns; low-pri UX polish.
- **Discovered-server tap silently overwrites typed input; LazyColumn reorder shifts tap targets under the user's finger** — UX polish.
- **`urlInput` has no length cap** — `rememberSaveable` could push pathological paste into Bundle > 1MB on save; defensive edge.
- **`testUrl` appends `/api/` to user-provided path** — `http://host/lovelace` becomes `/lovelace/api/`; user-error tolerance, low-pri.
- **Generic error message collapses 401/auth/TLS/timeout/malformed signals into one bucket** — AC4 pins exact single string for MVP; revisit when error UX is iterated.

## Deferred from: code review of 3-5-onboarding-authentication-session-persistence (2026-04-30)

- **MainScope singleton leaks coroutines across logout** [`DataModule.kt:30`] — pre-existing Story 3.3 architecture; `MainScope` is never cancelled; coroutines launched inside `ServerManager.connect/initialize` outlive the session boundary across logout/re-login cycles.
- **`ServerManager.initialize()` registers duplicate `onForeground` listener on repeated calls** [`ServerManager.kt`] — pre-existing Story 3.3; each `initialize()` appends a new lifecycle listener without deregistering the prior one; repeated initialize (startup + auth success) registers two listeners, triggering duplicate reconnect attempts.
- **OAuth `refresh_token` silently discarded** [`AuthViewModel.onOAuthCallback`] — explicitly out of scope per Story 3.5 Dev Notes; HA OAuth access tokens expire (~30min); Story 3.6 to implement token refresh on 401.
- **`SessionRepository.hasValidSession()` checks presence not expiry** [`SessionRepository.kt`] — stored but expired/revoked token passes the check; app reaches Dashboard then fails at WebSocket auth; Story 3.6 to add expiry/refresh logic.

## Deferred from: code review of 4-2-dashboard-persistence-layer (2026-04-30)

- `combine` exposes transient inconsistent state between dashboard and card flows (DashboardRepositoryImpl.kt:28-54) — architectural, requires single-source query.
- `combine` re-emits on every card change globally; no `distinctUntilChanged` (DashboardRepositoryImpl.kt:28-54) — optimization.
- `position` numeric edge cases (negative, Long.MAX truncation via `.toInt()`) (DashboardRepositoryImpl.kt:41,48) — defensive only.
- `addCard` / `saveDashboard` lack position invariant; duplicate `position` allowed (DashboardRepositoryImpl.kt:56-67,80-92) — related to compaction decision.
- No `UNIQUE(dashboard_id, position)` constraint (DashboardCard.sq) — would conflict with mid-reorder transient states.
- `Dispatchers.Default` vs `Dispatchers.IO` for SQLite (DashboardRepositoryImpl.kt) — spec-conformant (Dev Note prescribes Default).
- `config` column stored as raw `TEXT` with no JSON validation/adapter (DashboardCard.sq:5) — persistence-only scope.
- No tests for repo, SQL, or Result.failure paths; only use-case delegation tested (DashboardUseCaseTest.kt, GetDashboardsUseCaseTest.kt) — manual verification per AC10.
- `GetDashboardsUseCase` Flow has no `catch` operator (GetDashboardsUseCase.kt:8) — surface in ViewModel.
- Use cases pass through with no input validation (AddCardUseCase.kt etc.) — caller responsibility.
- Test names use backticks-with-spaces; KMP Native portability risk (DashboardUseCaseTest.kt, GetDashboardsUseCaseTest.kt) — currently runs on JVM only.
- `dbMutex` redundant for SQLite write serialization and absent from read path (DashboardRepositoryImpl.kt:26,...) — no functional bug.

## Deferred from: code review of story-4.3 (2026-04-30)

- `friendlyName` returns empty string for empty entityId or "light." (no object_id) — edge case unlikely from real HA. [EntityCard.kt:308-317]
- Future timestamps (server clock skew) silently render "0m ago" via `coerceAtLeast(0L)` — masks real bugs but cosmetic. [EntityCard.kt:333-338]
- Unsupported HaEntity subtypes (Climate/Cover/etc.) fall back to `Icons.Outlined.Sensors`, same as real sensors — no visual distinction. Story 4.4+ addresses. [EntityCard.kt:324-331]
- Recomposition isolation (NFR5) verified by code review only; no `Snapshot.withMutableSnapshot` UI test exists. Spec AC5 accepts code review.
- `Motion.entityStateChange` is `TweenSpec<Float>` and can't be passed to `animateColorAsState`; equivalent tween re-declared inline. Defer adding a Color-typed token to Motion until a second Color animation needs it. [EntityCard.kt:235-239]
