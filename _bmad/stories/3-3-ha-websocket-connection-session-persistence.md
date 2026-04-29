# Story 3.3: HA WebSocket Connection & Session Persistence

Status: done

## Story

As a developer,
I want the WebSocket client connected with LAN-first logic, Nabu Casa fallback, and automatic foreground reconnect,
So that the app always uses the fastest available connection and restores it without user action.

## Acceptance Criteria

1. `KtorHaWebSocketClient` attempts local LAN WebSocket first; falls back to Nabu Casa cloud relay only if LAN attempt fails
2. `HaReconnectManager` implements exponential backoff (1s → 2s → 4s → max 30s); first reconnect fires within 5 seconds of connection loss detected (NFR9)
3. `AppLifecycleObserver.onForeground` triggers an immediate reconnect attempt on every foreground event
4. `ServerManager` exposes connection state as `StateFlow<ConnectionState>` (Connected, Reconnecting, Disconnected)
5. Nabu Casa relay uses HTTPS/TLS — no unencrypted fallback (NFR7)
6. Unknown HA message types are absorbed without crash — logged at debug level, not thrown (FR34)
7. App startup with a stored token does NOT block on WebSocket connection — UI renders from SQLDelight cache immediately (NFR2, NFR13)

## Tasks / Subtasks

- [x] Task 1: Create `HaReconnectManager` — extract backoff logic from `ServerManager` (AC: 2)
  - [x] 1.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaReconnectManager.kt` with `reset()`, `nextDelayMs(): Long`, `runWithBackoff(block: suspend () -> Boolean)` pattern
  - [x] 1.2: Backoff sequence: 1000ms → 2000ms → 4000ms → 8000ms → 16000ms → 30000ms cap
  - [x] 1.3: `reset()` sets backoff back to 1000ms (call on successful connect)

- [x] Task 2: Write `HaReconnectManagerTest` — RED before Task 1 (AC: 2)
  - [x] 2.1: Create `shared/src/commonTest/kotlin/com/backpapp/hanative/data/remote/HaReconnectManagerTest.kt`
  - [x] 2.2: Tests: `backoff sequence matches spec`, `caps at 30000ms`, `reset restores 1000ms`

- [x] Task 3: Refactor `ServerManager` — use `HaReconnectManager` + LAN-first logic (AC: 1, 3, 4, 5, 7)
  - [x] 3.1: Replace inline backoff loop in `scheduleReconnect()` with `HaReconnectManager`
  - [x] 3.2: Change `initialize(serverUrl: String)` → `initialize(lanUrl: String, cloudUrl: String? = null)` — store both
  - [x] 3.3: In `attemptConnect()`: try `webSocketClient.connect(lanUrl, token)` first; on exception try `cloudUrl` (if set); on both fail call `reconnectManager.reset()` then schedule next backoff
  - [x] 3.4: Validate `cloudUrl` starts with `https://` before use — throw `IllegalArgumentException` if not (NFR7: no unencrypted cloud relay)
  - [x] 3.5: Call `reconnectManager.reset()` in the `isConnected` collector when connected → `true`

- [x] Task 4: Patch `KtorHaWebSocketClient` — log unknown message types (AC: 6)
  - [x] 4.1: Add `else` branch in `handleFrame` `when` block: `else -> println("DEBUG: unknown WS message type: ${obj["type"]?.jsonPrimitive?.content}")`
  - [x] 4.2: Confirm no exception is thrown for unknown types — just log + return

- [x] Task 5: Wire DI — `HttpClient`, `KtorHaWebSocketClient`, `HaReconnectManager`, `ServerManager` (AC: all)
  - [x] 5.1: Add `expect fun httpClientModule(): Module` to `DataModule.kt` + include in `dataModule`
  - [x] 5.2: Create `shared/src/androidMain/kotlin/com/backpapp/hanative/di/HttpClientModule.kt` — `actual fun httpClientModule()` providing `HttpClient(CIO) { install(WebSockets) }`
  - [x] 5.3: Create `shared/src/iosMain/kotlin/com/backpapp/hanative/di/HttpClientModule.kt` — `actual fun httpClientModule()` providing `HttpClient(Darwin) { install(WebSockets) }`
  - [x] 5.4: Add `serverManagerModule()` to `DataModule.kt` (non-expect, commonMain) providing `KtorHaWebSocketClient`, `HaWebSocketClient` binding, `AuthenticationRepositoryImpl`, `HaReconnectManager`, `ServerManager` — `scope` bound via `single<CoroutineScope> { MainScope() }`

- [x] Task 6: Build `:shared:testDebugUnitTest :androidApp:assembleDebug` — BUILD SUCCESSFUL (AC: all)
  - [x] 6.1: `HaReconnectManagerTest` tests pass (3/3)
  - [x] 6.2: No compilation errors from `ServerManager` refactor or `KtorHaWebSocketClient` patch

## Dev Notes

### What Already Exists — Do NOT Recreate

**Fully implemented in `commonMain/data/remote/`:**
- `KtorHaWebSocketClient.kt` — 240 lines, complete Ktor WebSocket impl. **Only change: add `else` branch in `handleFrame` `when` block (Task 4)**
- `ServerManager.kt` — 87 lines, has `ConnectionState` sealed class, `AppLifecycleObserver` integration, inline backoff. **Refactor per Tasks 3.1–3.5 only**
- `AuthenticationRepositoryImpl.kt` — thin wrapper; `getValidToken()` reads from `CredentialStore`

**Already done in Story 3.2:**
- `AppLifecycleObserver` is already wired in `ServerManager.initialize()` — `lifecycleObserver.onForeground { triggerReconnect() }` already there
- `HaSettingsKeys.HA_URL` exists in `commonMain/data/local/HaSettingsKeys.kt` (DataStore key for HA URL)
- `NSLocalNetworkUsageDescription` + `NSBonjourServices` already in `iosApp/iosApp/Info.plist` — **do not touch**

**Interface in `domain/repository/`:**
- `HaWebSocketClient` interface — `connect()`, `events()`, `callService()`, `getStates()`, `disconnect()`, `isConnected: StateFlow<Boolean>`

### HaReconnectManager Design

Pure Kotlin, no platform deps. `data/remote/HaReconnectManager.kt`:

```kotlin
class HaReconnectManager {
    private var backoffMs = 1_000L

    fun reset() { backoffMs = 1_000L }

    suspend fun waitThenAttempt(attempt: suspend () -> Unit) {
        delay(backoffMs)
        backoffMs = minOf(backoffMs * 2, 30_000L)
        attempt()
    }
}
```

`waitThenAttempt` is called from inside a `while` loop in `ServerManager.scheduleReconnect()`. Do NOT move the loop into `HaReconnectManager` — `ServerManager` controls the loop lifecycle via `reconnectJob?.cancel()`.

Test the pure calculator behavior independently:

```kotlin
class HaReconnectManagerTest {
    private val manager = HaReconnectManager()

    @Test
    fun `backoff sequence matches spec`() = runTest {
        val delays = (1..6).map {
            val d = manager.nextDelayMs()
            manager.advanceBackoff()
            d
        }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L), delays)
    }
}
```

Adapt to your actual `HaReconnectManager` API shape — the above assumes `nextDelayMs()` returns current delay and `advanceBackoff()` advances it, or collapse into a single `consumeDelay()`. Keep the test pattern: call N times, verify sequence.

### ServerManager Refactor — LAN-First Logic

```kotlin
class ServerManager(
    private val webSocketClient: HaWebSocketClient,
    private val authRepository: AuthenticationRepositoryImpl,
    private val lifecycleObserver: AppLifecycleObserver,
    private val reconnectManager: HaReconnectManager,
    private val scope: CoroutineScope,
) {
    private var lanUrl: String? = null
    private var cloudUrl: String? = null
    // ... rest unchanged except attemptConnect + scheduleReconnect

    fun initialize(lanUrl: String, cloudUrl: String? = null) {
        require(cloudUrl == null || cloudUrl.startsWith("https://")) {
            "Nabu Casa cloud URL must use HTTPS (NFR7): $cloudUrl"
        }
        this.lanUrl = lanUrl
        this.cloudUrl = cloudUrl
        // ... same lifecycle observer + isConnected collector wiring as before
    }

    private suspend fun attemptConnect() {
        val lan = lanUrl ?: return
        _connectionState.value = ConnectionState.Reconnecting
        val token = runCatching { authRepository.getValidToken() }.getOrNull() ?: return

        val lanOk = runCatching { webSocketClient.connect(lan, token) }.isSuccess
        if (lanOk) {
            reconnectManager.reset()
            return
        }

        val cloud = cloudUrl
        if (cloud != null) {
            val cloudOk = runCatching { webSocketClient.connect(cloud, token) }.isSuccess
            if (cloudOk) { reconnectManager.reset(); return }
        }

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        _connectionState.value = ConnectionState.Reconnecting
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (_connectionState.value != ConnectionState.Connected) {
                reconnectManager.waitThenAttempt { attemptConnect() }
            }
        }
    }
}
```

**Critical:** `webSocketClient.connect()` throws if `httpClient.webSocketSession(url)` fails (TCP unreachable / connection refused). This is how LAN failure is detected. If TCP connects but auth fails, `handleFrame` emits `ConnectionError` and closes the session — `isConnected` collector detects the `false` and calls `scheduleReconnect()`.

### KtorHaWebSocketClient Unknown Message Types

`handleFrame` current `when` block (around line 90):

```kotlin
when (obj["type"]?.jsonPrimitive?.content) {
    "auth_required" -> { ... }
    "auth_ok" -> { ... }
    "auth_invalid" -> { ... }
    "event" -> handleEvent(obj)
    "result" -> handleResult(obj)
    // ADD THIS:
    else -> println("DEBUG: KtorHaWebSocketClient unknown message type: ${obj["type"]?.jsonPrimitive?.content}")
}
```

No exception, no crash, no `return`. Just log. FR34 satisfied.

### DI Wiring Pattern

`HttpClient` requires platform-specific engine — same expect/actual pattern as Story 3.1/3.2:

```kotlin
// androidMain/di/HttpClientModule.kt
actual fun httpClientModule(): Module = module {
    single {
        HttpClient(CIO) {
            install(WebSockets)
        }
    }
}

// iosMain/di/HttpClientModule.kt
actual fun httpClientModule(): Module = module {
    single {
        HttpClient(Darwin) {
            install(WebSockets)
        }
    }
}
```

Imports:
- Android: `io.ktor.client.engine.cio.CIO`
- iOS: `io.ktor.client.engine.darwin.Darwin`
- Both: `io.ktor.client.HttpClient`, `io.ktor.client.plugins.websocket.WebSockets`

These deps already in `shared/build.gradle.kts` — `libs.ktor.client.cio` (androidMain), `libs.ktor.client.darwin` (iosMain), `libs.ktor.client.websockets` (commonMain). **No new deps needed.**

`serverManagerModule()` in commonMain `DataModule.kt`:

```kotlin
fun serverManagerModule(): Module = module {
    single { MainScope() as CoroutineScope }
    single { KtorHaWebSocketClient(get()) }
    single<HaWebSocketClient> { get<KtorHaWebSocketClient>() }
    single { AuthenticationRepositoryImpl(get()) }
    single { HaReconnectManager() }
    single {
        ServerManager(
            webSocketClient = get(),
            authRepository = get(),
            lifecycleObserver = get(),
            reconnectManager = get(),
            scope = get(),
        )
    }
}
```

`AppLifecycleObserver` is already provided by `appLifecycleObserverModule()` in both platforms — `get()` resolves it.

`DataModule.kt` after Task 5:

```kotlin
val dataModule = module {
    includes(
        hapticEngineModule(),
        credentialStoreModule(),
        settingsDataStoreModule(),
        serverDiscoveryModule(),
        appLifecycleObserverModule(),
        httpClientModule(),
        serverManagerModule(),
    )
}

expect fun hapticEngineModule(): Module
expect fun credentialStoreModule(): Module
expect fun settingsDataStoreModule(): Module
expect fun serverDiscoveryModule(): Module
expect fun appLifecycleObserverModule(): Module
expect fun httpClientModule(): Module
```

### App Startup Non-Blocking (AC 7)

`ServerManager.connect()` already calls `scope.launch { attemptConnect() }` — non-blocking by design. The UI should read from SQLDelight cache first (Story 4.1 implements this). No change needed for Story 3.3 beyond ensuring `connect()` is not called on the main thread synchronously at startup. Call site (iOS/Android entry) should be: `serverManager.connect()` after `initialize()`, not `runBlocking { serverManager.connect() }`.

### Testing Limitations

`ServerManager` cannot be unit tested in commonTest — depends on `AppLifecycleObserver` (`expect class`, OS-level side effects). Same constraint as Story 3.2 `AppLifecycleObserver`. Test `HaReconnectManager` in isolation. `ServerManager` functional verification deferred to instrumented tests.

`KtorHaWebSocketClient` cannot be tested without a live WS server in commonTest. Functional verification of unknown-type absorption confirmed by absence of crash — tested via integration / manual.

### Ktor WebSocket Engine Versions

From `gradle/libs.versions.toml` (confirmed Apr 28 snapshot):
- `ktor = "3.x"` — `ktor.client.core`, `ktor.client.websockets`, `ktor.client.cio`, `ktor.client.darwin`, `ktor.client.content.negotiation`, `ktor.serialization.kotlinx.json` all present. No version changes needed.

### Project Structure

New files:
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaReconnectManager.kt`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/HttpClientModule.kt`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/HttpClientModule.kt`
- `shared/src/commonTest/kotlin/com/backpapp/hanative/data/remote/HaReconnectManagerTest.kt`

Modified files:
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/ServerManager.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/KtorHaWebSocketClient.kt` (else branch only)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt`

### Build Command

```bash
./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug
```

### References

- `ServerManager.kt` current impl: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/ServerManager.kt`
- `KtorHaWebSocketClient.kt`: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/KtorHaWebSocketClient.kt`
- `HaWebSocketClient` interface: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/HaWebSocketClient.kt`
- DI pattern: `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt`
- Story 3.2 `AppLifecycleObserver` testing note: AppLifecycleObserver OS-side effects — no commonTest coverage possible
- Architecture LAN-first NFR: `_bmad/outputs/architecture.md` (FR1–8 HA connectivity section)
- Ktor KMP AtomicInt gotcha: use `Mutex + var counter` not `java.util.concurrent.atomic.AtomicInteger` (prev session obs #296)

## Senior Developer Review (AI)

### Review Findings

- [x] [Review][Decision] Infinite reconnect loop has no terminal state — resolved: added `ConnectionState.Failed` + `MAX_RECONNECT_ATTEMPTS = 10`; loop exits with `Failed` state after 10 attempts.

- [x] [Review][Patch] Auth token failure leaves `_connectionState` stuck at `Reconnecting` with no retry [ServerManager.kt:59] — fixed: token failure now sets `_connectionState = Disconnected` and returns; while-loop exits (checks `== Reconnecting`).

- [x] [Review][Patch] `attemptConnect()` calls `scheduleReconnect()` creating recursive job restart inside the loop [ServerManager.kt:76] — fixed: removed `scheduleReconnect()` from `attemptConnect()`; `connect()` now calls `scheduleReconnect()` if state remains `Reconnecting` after initial attempt.

- [x] [Review][Patch] `triggerReconnect()` launches parallel `attemptConnect()` without cancelling existing reconnect loop [ServerManager.kt:50-53] — fixed: `triggerReconnect()` now calls `scheduleReconnect()` which cancels existing `reconnectJob` before starting new loop.

- [x] [Review][Patch] `HaReconnectManager.backoffMs` is unsynchronized mutable state accessed from two coroutines [HaReconnectManager.kt:3] — fixed: added `@Volatile` to `backoffMs`.

- [x] [Review][Patch] No timeout on `webSocketClient.connect()` — LAN TCP stall hangs reconnect loop indefinitely [ServerManager.kt:~61] — fixed: wrapped both LAN and cloud `connect()` calls with `withTimeout(10_000L)`.

- [x] [Review][Patch] `waitThenAttempt` has zero test coverage [HaReconnectManagerTest.kt] — fixed: added `waitThenAttempt advances backoff and invokes attempt` test using `runTest`; 4/4 tests GREEN.

- [x] [Review][Defer] `HttpClient` never closed — no `onClose` teardown in Koin module [DataModule.kt / HttpClientModule.kt] — deferred, pre-existing architectural gap
- [x] [Review][Defer] `MainScope()` registered as `single<CoroutineScope>` is never cancelled [DataModule.kt:20] — deferred, app-level lifecycle concern
- [x] [Review][Defer] `onForeground` callback not deregistered after `disconnect()` [ServerManager.kt] — deferred, requires AppLifecycleObserver deregistration API
- [x] [Review][Defer] Race between `reconnectJob?.cancel()` and in-flight `waitThenAttempt` [ServerManager.kt] — deferred, mitigated by P2+P3 fixes; residual cooperative-cancellation is acceptable
- [x] [Review][Defer] `initialize()` allows multiple registrations of callbacks + collector [ServerManager.kt] — deferred, no current multi-call site; guard when call sites are known

## Dev Agent Record

### Completion Notes

- `HaReconnectManager` uses `nextDelayMs()` / `advanceBackoff()` / `reset()` API — separates pure backoff math from `delay()` for testability; `waitThenAttempt()` wraps both for production use in `ServerManager.scheduleReconnect()`
- `ServerManager` now takes `HaReconnectManager` as constructor param; `initialize()` signature changed to `(lanUrl, cloudUrl?)` — callers must update
- `reconnectManager.reset()` called in two places: `isConnected` collector on `true`, and `attemptConnect()` on successful LAN or cloud connect
- `single<CoroutineScope> { MainScope() }` used instead of cast to avoid Kotlin warning
- `HaReconnectManagerTest`: 3 tests, 0 failures — backoff sequence, cap at 30s, reset
- `ServerManager` and `KtorHaWebSocketClient` not unit-testable in commonTest (OS deps, live WS server required)
- Build: `./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug` — BUILD SUCCESSFUL

### File List

New files:
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaReconnectManager.kt`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/HttpClientModule.kt`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/HttpClientModule.kt`
- `shared/src/commonTest/kotlin/com/backpapp/hanative/data/remote/HaReconnectManagerTest.kt`

Modified files:
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/ServerManager.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/KtorHaWebSocketClient.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt`

## Change Log

| Date | Change |
|------|--------|
| 2026-04-30 | Story 3.3 created — ready-for-dev |
| 2026-04-30 | Story 3.3 implemented — status: review |
