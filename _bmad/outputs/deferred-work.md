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
