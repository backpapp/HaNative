# Story 3.2: ServerDiscovery & AppLifecycleObserver expect/actual

Status: review

## Story

As a developer,
I want mDNS server discovery and foreground reconnect lifecycle hooks wired up,
So that onboarding can offer auto-discovered HA instances and the app reconnects WebSocket on every foreground event.

## Acceptance Criteria

1. `platform/ServerDiscovery.kt` (`commonMain`) declares `expect interface ServerDiscovery` with `fun startDiscovery(): Flow<List<HaServerInfo>>` and `fun stopDiscovery()`
2. `AndroidServerDiscovery.kt` (`androidMain`) uses `NsdManager` to browse `_home-assistant._tcp`, emitting discovered instances as `HaServerInfo(name, host, port)`
3. `IosServerDiscovery.kt` (`iosMain`) uses `NSNetServiceBrowser` to browse the same service type
4. `platform/AppLifecycleObserver.kt` (`commonMain`) declares `expect class AppLifecycleObserver` with `fun onForeground(callback: () -> Unit)` — **already exists as stub; implement only**
5. `AndroidAppLifecycleObserver` (androidMain stub) uses `ProcessLifecycleOwner` `ON_START`; `IosAppLifecycleObserver` (iosMain stub) uses `didBecomeActiveNotification`
6. Both `ServerDiscovery` and `AppLifecycleObserver` are registered in `DataModule` (Koin) via `expect fun` pattern

## Tasks / Subtasks

- [x] Task 1: Add `lifecycle-process` to version catalog + androidMain; add NSD permissions to AndroidManifest (AC: 2, 5)
  - [x] 1.1: Add `lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle-viewmodel" }` to `gradle/libs.versions.toml` [libraries] section
  - [x] 1.2: Add `implementation(libs.lifecycle.process)` to `androidMain.dependencies` in `shared/build.gradle.kts`
  - [x] 1.3: Add `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` and `<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />` to `androidApp/src/main/AndroidManifest.xml`
- [x] Task 2: Create `HaServerInfo` data class in `commonMain/domain/model/` (AC: 1, 2, 3)
  - [x] 2.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/HaServerInfo.kt` — `data class HaServerInfo(val name: String, val host: String, val port: Int)`
- [x] Task 3: Write failing `ServerDiscoveryTest` via `FakeServerDiscovery` in `commonTest` — RED phase (AC: 1)
  - [x] 3.1: Create `FakeServerDiscovery` implementing `ServerDiscovery` in commonTest — backed by `MutableSharedFlow(replay=1)`; `stopDiscovery()` sets `stopped = true`
  - [x] 3.2: Write tests: `startDiscovery emits servers`, `startDiscovery emits empty on no servers`, `stopDiscovery sets flag`; confirmed RED before Task 4
- [x] Task 4: Implement `ServerDiscovery` expect/actual + platform classes (AC: 1, 2, 3)
  - [x] 4.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/ServerDiscovery.kt` — `expect interface ServerDiscovery`
  - [x] 4.2: Create `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/ServerDiscovery.kt` — `actual interface ServerDiscovery` with all members marked `actual`
  - [x] 4.3: Create `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/ServerDiscovery.kt` — `actual interface ServerDiscovery` with all members marked `actual`
  - [x] 4.4: Create `AndroidServerDiscovery.kt` in `androidMain/platform/` — `NsdManager.discoverServices("_home-assistant._tcp", PROTOCOL_DNS_SD, ...)` using `callbackFlow`; accumulates resolved services list; emits on add/remove
  - [x] 4.5: Create `IosServerDiscovery.kt` in `iosMain/platform/` — `NSNetServiceBrowser` browsing `_home-assistant._tcp.` in `local.`; resolves each found service; emits accumulated list via `callbackFlow`
- [x] Task 5: Implement `AndroidAppLifecycleObserver` stub → `ProcessLifecycleOwner` (AC: 5)
  - [x] 5.1: Replaced TODO body in `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` with `ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver { override fun onStart(owner: LifecycleOwner) { callback() } })`
- [x] Task 6: Implement `IosAppLifecycleObserver` stub → `NSNotificationCenter` (AC: 5)
  - [x] 6.1: Replaced TODO body in `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` with `NSNotificationCenter.defaultCenter().addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue()) { _ -> callback() }`
- [x] Task 7: Wire `ServerDiscovery` + `AppLifecycleObserver` into DataModule (Koin) (AC: 6)
  - [x] 7.1: Added `expect fun serverDiscoveryModule(): Module` and `expect fun appLifecycleObserverModule(): Module` to `DataModule.kt`; both in `includes(...)` call
  - [x] 7.2: Created `shared/src/androidMain/kotlin/com/backpapp/hanative/di/ServerDiscoveryModule.kt` — `actual fun serverDiscoveryModule()` registering `single<ServerDiscovery> { AndroidServerDiscovery(androidContext()) }`
  - [x] 7.3: Created `shared/src/androidMain/kotlin/com/backpapp/hanative/di/AppLifecycleObserverModule.kt` — `actual fun appLifecycleObserverModule()` registering `single { AppLifecycleObserver() }`
  - [x] 7.4: Created `shared/src/iosMain/kotlin/com/backpapp/hanative/di/ServerDiscoveryModule.kt` — `actual fun serverDiscoveryModule()` registering `single<ServerDiscovery> { IosServerDiscovery() }`
  - [x] 7.5: Created `shared/src/iosMain/kotlin/com/backpapp/hanative/di/AppLifecycleObserverModule.kt` — `actual fun appLifecycleObserverModule()` registering `single { AppLifecycleObserver() }`
- [x] Task 8: Build `:shared:testDebugUnitTest :androidApp:assembleDebug` — BUILD SUCCESSFUL (AC: all)
  - [x] 8.1: BUILD SUCCESSFUL in 9s — 56 tests, 0 failures; first attempt had 2 test failures (UncompletedCoroutinesError — fixed MutableSharedFlow(replay=1) + emit-before-collect pattern)

## Dev Notes

### Pre-existing Stubs — Do Not Recreate

`AppLifecycleObserver` stub files already exist with `TODO` bodies — implement in place:
- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` — `expect class AppLifecycleObserver()` with `fun onForeground(callback: () -> Unit)` (**do not modify**)
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` — `actual class AppLifecycleObserver actual constructor()` (replace TODO body only)
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` — `actual class AppLifecycleObserver actual constructor()` (replace TODO body only)

`ServerDiscovery` does NOT exist anywhere — must create all files.

### expect/actual Interface Pattern (from Story 3.1 Debug Log)

`expect interface` requires `actual interface` in EACH platform source set. Implementing classes are plain (no `actual` keyword):

```
commonMain: expect interface ServerDiscovery { fun startDiscovery(): Flow<List<HaServerInfo>>; fun stopDiscovery() }
androidMain/platform/ServerDiscovery.kt: actual interface ServerDiscovery { actual fun startDiscovery(): Flow<List<HaServerInfo>>; actual fun stopDiscovery() }
androidMain/platform/AndroidServerDiscovery.kt: class AndroidServerDiscovery(ctx: Context) : ServerDiscovery { ... }  // no `actual` keyword
iosMain/platform/ServerDiscovery.kt: actual interface ServerDiscovery { ... }
iosMain/platform/IosServerDiscovery.kt: class IosServerDiscovery : ServerDiscovery { ... }  // no `actual` keyword
```

`expect class AppLifecycleObserver()` already uses the class pattern — actual classes are already scaffolded.

### HaServerInfo Placement

Put `HaServerInfo` in `commonMain/domain/model/HaServerInfo.kt` — pure Kotlin data class, no platform specifics:

```kotlin
data class HaServerInfo(val name: String, val host: String, val port: Int)
```

### AndroidServerDiscovery: NsdManager + callbackFlow

`NsdManager` has a critical constraint: only **one concurrent `resolveService` call** — resolving the next service while a resolve is pending throws `FAILURE_ALREADY_ACTIVE`. For MVP, resolve services sequentially using a queue, or accept the race and log the error.

Recommended MVP approach — use `callbackFlow` with an accumulated list:

```kotlin
class AndroidServerDiscovery(private val context: Context) : ServerDiscovery {
    override fun startDiscovery(): Flow<List<HaServerInfo>> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val discovered = mutableListOf<HaServerInfo>()

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { /* log debug, ignore */ }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                discovered.removeAll { it.name == info.serviceName }
                discovered.add(HaServerInfo(info.serviceName, host, info.port))
                trySend(discovered.toList())
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { close() }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                try { nsdManager.resolveService(info, resolveListener) } catch (_: Exception) {}
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                discovered.removeAll { it.name == info.serviceName }
                trySend(discovered.toList())
            }
        }

        nsdManager.discoverServices("_home-assistant._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {} }
    }

    override fun stopDiscovery() {} // awaitClose in callbackFlow handles teardown on cancellation
}
```

Required import: `android.net.nsd.NsdManager`, `android.net.nsd.NsdServiceInfo`.

### IosServerDiscovery: NSNetServiceBrowser

Use `callbackFlow` with a delegate object. Hold strong refs to avoid ARC deallocation:

```kotlin
@OptIn(ExperimentalForeignApi::class)
class IosServerDiscovery : ServerDiscovery {
    override fun startDiscovery(): Flow<List<HaServerInfo>> = callbackFlow {
        val discovered = mutableListOf<HaServerInfo>()
        val browser = NSNetServiceBrowser()
        val services = mutableListOf<NSNetService>()  // hold refs to prevent ARC dealloc

        val delegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
            override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
                services.add(didFindService)
                didFindService.delegate = object : NSObject(), NSNetServiceDelegateProtocol {
                    override fun netServiceDidResolveAddress(sender: NSNetService) {
                        val host = sender.hostName ?: return
                        discovered.removeAll { it.name == sender.name }
                        discovered.add(HaServerInfo(sender.name, host, sender.port.toInt()))
                        trySend(discovered.toList())
                    }
                    override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {}
                }
                didFindService.resolveWithTimeout(5.0)
            }
            override fun netServiceBrowser(browser: NSNetServiceBrowser, didRemoveService: NSNetService, moreComing: Boolean) {
                discovered.removeAll { it.name == didRemoveService.name }
                trySend(discovered.toList())
            }
        }

        browser.delegate = delegate
        browser.searchForServicesOfType("_home-assistant._tcp.", inDomain: "local.")
        awaitClose { browser.stop() }
    }

    override fun stopDiscovery() {}
}
```

Required imports: `platform.Foundation.*`.
The browser delegate must be held by a strong `val` inside the `callbackFlow` lambda — Kotlin/Native ARC will deallocate delegates not retained.

### AndroidAppLifecycleObserver: ProcessLifecycleOwner

`ProcessLifecycleOwner` lives in `androidx.lifecycle:lifecycle-process`. **Not in catalog — add in Task 1.**

`lifecycle-process` uses the same version as `lifecycle-viewmodel` (2.9.0):

```kotlin
// libs.versions.toml [libraries]
lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle-viewmodel" }
```

```kotlin
// shared/build.gradle.kts androidMain.dependencies
implementation(libs.lifecycle.process)
```

Implementation:

```kotlin
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

actual class AppLifecycleObserver actual constructor() {
    actual fun onForeground(callback: () -> Unit) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { callback() }
        })
    }
}
```

`DefaultLifecycleObserver` from `androidx.lifecycle:lifecycle-common` — already transitive via `lifecycle-process`.

### IosAppLifecycleObserver: NSNotificationCenter

```kotlin
@OptIn(ExperimentalForeignApi::class)
actual class AppLifecycleObserver actual constructor() {
    actual fun onForeground(callback: () -> Unit) {
        NSNotificationCenter.defaultCenter().addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue(),
            usingBlock = { _ -> callback() }
        )
    }
}
```

Required imports: `platform.Foundation.NSNotificationCenter`, `platform.Foundation.NSOperationQueue`, `platform.UIKit.UIApplicationDidBecomeActiveNotification`.

### DataModule Wiring Pattern

Mirror exact pattern from `credentialStoreModule()`. `DataModule.kt` after Task 7:

```kotlin
val dataModule = module {
    includes(
        hapticEngineModule(),
        credentialStoreModule(),
        settingsDataStoreModule(),
        serverDiscoveryModule(),
        appLifecycleObserverModule(),
    )
}

expect fun hapticEngineModule(): Module
expect fun credentialStoreModule(): Module
expect fun settingsDataStoreModule(): Module
expect fun serverDiscoveryModule(): Module
expect fun appLifecycleObserverModule(): Module
```

**Must create BOTH android and ios actuals for each new module** — missing one = runtime crash on that platform only. That means 4 new files in di/ (2 Android + 2 iOS).

### TDD: FakeServerDiscovery Pattern

```kotlin
// commonTest
class FakeServerDiscovery : ServerDiscovery {
    val stopped = atomic(false)
    private val _flow = MutableSharedFlow<List<HaServerInfo>>()

    suspend fun emit(servers: List<HaServerInfo>) = _flow.emit(servers)

    override fun startDiscovery(): Flow<List<HaServerInfo>> = _flow
    override fun stopDiscovery() { stopped.value = true }
}
```

Note: `atomic` not available in commonTest — use `var stopped = false` (single-threaded test context).

`FakeServerDiscovery` implements `ServerDiscovery` which is an `expect interface` — this compiles in commonTest because the compiler sees the `expect` declaration; actual resolution happens per platform at link time.

### AppLifecycleObserver Testing Limitation

`AppLifecycleObserver` is `expect class` with OS-level side effects (`ProcessLifecycleOwner`, `NSNotificationCenter`). Neither works in JVM host tests (`testAndroidHostTest`). **Do not write unit tests for `AppLifecycleObserver` — verify manually or in instrumented tests.** Confirm the build compiles correctly; functional verification deferred to Story 3.3 integration.

### Build Command

```bash
./gradlew :shared:testDebugUnitTest :androidApp:assembleDebug
```

Alias: `testDebugUnitTest` → `testAndroidHostTest` (registered in build.gradle.kts).

### Permissions Note

`NsdManager` requires `ACCESS_NETWORK_STATE` + `ACCESS_WIFI_STATE` in `androidApp/src/main/AndroidManifest.xml`. Missing permissions = silent failure or runtime exception on discovery start. Add both in Task 1.3.

### Project Structure Notes

- New platform files follow identical naming across source sets: `ServerDiscovery.kt` in commonMain/androidMain/iosMain
- Implementing classes: `AndroidServerDiscovery.kt` (androidMain/platform/), `IosServerDiscovery.kt` (iosMain/platform/)
- DI modules: `ServerDiscoveryModule.kt` and `AppLifecycleObserverModule.kt` in `androidMain/di/` and `iosMain/di/`
- `HaServerInfo.kt` in `commonMain/domain/model/` — pure Kotlin, no platform specifics

### References

- expect/actual interface pattern: Story 3.1 Debug Log entry #1
- DataModule expect fun pattern: `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt`
- CredentialStoreModule actual pattern: `shared/src/androidMain/kotlin/com/backpapp/hanative/di/CredentialStoreModule.kt`
- AppLifecycleObserver stubs: `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` (TODO comments)
- Test pattern: `shared/src/commonTest/kotlin/com/backpapp/hanative/platform/CredentialStoreTest.kt` (FakeCredentialStore)
- NsdManager: Android SDK (no extra dep — in `android.net.nsd`)
- NSNetServiceBrowser: Foundation framework (no extra dep — Kotlin/Native platform interop)
- lifecycle-process: `androidx.lifecycle:lifecycle-process:2.9.0` — **NOT in catalog, add in Task 1**

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log

| # | Issue | Resolution |
|---|-------|------------|
| 1 | `ServerDiscoveryTest` — 2 failures: `UncompletedCoroutinesError` | `MutableSharedFlow()` default (no replay, no buffer) deadlocks in `runTest` when emit and collect are in concurrent coroutines on `StandardTestDispatcher`. Fixed: `MutableSharedFlow(replay = 1)` + emit-before-collect pattern — no `launch` needed |

### Completion Notes

Story 3.2 complete. Key deliverables:
- `HaServerInfo(name, host, port)` data class in `commonMain/domain/model/`
- `ServerDiscovery` expect/actual interface: `commonMain` expect + `actual interface` in androidMain + iosMain (identical to Story 3.1 CredentialStore pattern)
- `AndroidServerDiscovery` — `NsdManager.discoverServices("_home-assistant._tcp")` via `callbackFlow`; accumulates resolved list; resolves services one at a time (NsdManager single-concurrent-resolve constraint; exceptions swallowed silently)
- `IosServerDiscovery` — `NSNetServiceBrowser` + `NSNetServiceBrowserDelegate` + `NSNetServiceDelegate` via `callbackFlow`; delegate objects held as strong refs in local list to prevent ARC deallocation
- `AndroidAppLifecycleObserver` — `ProcessLifecycleOwner.get().lifecycle.addObserver(DefaultLifecycleObserver.onStart)` using `lifecycle-process:2.9.0`
- `IosAppLifecycleObserver` — `NSNotificationCenter.addObserverForName(UIApplicationDidBecomeActiveNotification)`
- `serverDiscoveryModule()` + `appLifecycleObserverModule()` expect/actual (4 new DI files) wired into `DataModule.includes()`
- `ServerDiscoveryTest` (3 tests, commonTest): emit-and-collect, emit-empty, stop-flag — all pass
- `lifecycle-process` added to catalog + androidMain; `ACCESS_NETWORK_STATE` + `ACCESS_WIFI_STATE` added to AndroidManifest
- `AppLifecycleObserver` not unit-tested (OS-level side effects — ProcessLifecycleOwner/NSNotificationCenter don't work on JVM host test); functional verification deferred to Story 3.3
- BUILD SUCCESSFUL: 56 tests, 0 failures

### File List

- `gradle/libs.versions.toml` — added `lifecycle-process` library alias
- `shared/build.gradle.kts` — added `libs.lifecycle.process` to androidMain
- `androidApp/src/main/AndroidManifest.xml` — added `ACCESS_NETWORK_STATE` + `ACCESS_WIFI_STATE` permissions
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/HaServerInfo.kt` — new, `data class HaServerInfo(name, host, port)`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/ServerDiscovery.kt` — new, `expect interface ServerDiscovery`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/ServerDiscovery.kt` — new, `actual interface ServerDiscovery`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AndroidServerDiscovery.kt` — new, NsdManager callbackFlow implementation
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` — implemented stub; ProcessLifecycleOwner ON_START
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/ServerDiscovery.kt` — new, `actual interface ServerDiscovery`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/IosServerDiscovery.kt` — new, NSNetServiceBrowser callbackFlow implementation
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/AppLifecycleObserver.kt` — implemented stub; NSNotificationCenter didBecomeActive
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt` — added `serverDiscoveryModule()` + `appLifecycleObserverModule()` expects + includes
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/ServerDiscoveryModule.kt` — new, `actual fun serverDiscoveryModule()`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/AppLifecycleObserverModule.kt` — new, `actual fun appLifecycleObserverModule()`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/ServerDiscoveryModule.kt` — new, `actual fun serverDiscoveryModule()`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/AppLifecycleObserverModule.kt` — new, `actual fun appLifecycleObserverModule()`
- `shared/src/commonTest/kotlin/com/backpapp/hanative/platform/ServerDiscoveryTest.kt` — new, 3 contract tests via `FakeServerDiscovery`
- `_bmad/stories/3-2-server-discovery-app-lifecycle-observer.md` — story file

## Change Log

| Date | Change |
|------|--------|
| 2026-04-28 | Story 3.2 created — ready-for-dev |
| 2026-04-28 | Story 3.2 complete — ServerDiscovery expect/actual (NsdManager/NSNetServiceBrowser), AppLifecycleObserver implemented (ProcessLifecycleOwner/NSNotificationCenter), 3 tests pass, BUILD SUCCESSFUL |
