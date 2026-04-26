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
