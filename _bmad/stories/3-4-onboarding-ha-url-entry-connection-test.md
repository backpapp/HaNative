# Story 3.4: Onboarding — HA URL Entry & Connection Test

Status: done

## Story

As a power user,
I want to enter my HA instance URL and immediately verify connectivity,
So that I know the connection works before being asked to authenticate.

## Acceptance Criteria

1. `OnboardingScreen` presents a single `TextField` for HA instance URL with `ImeAction.Go` triggering the connection test — no separate submit tap required
2. `ServerDiscovery` results populate a list of auto-discovered HA instances below the field — tapping one fills the URL
3. During connection test, `CircularProgressIndicator` replaces the submit button (disabled, not hidden)
4. On connection failure, an inline plain-language error appears below the field ("Can't reach this address — check the URL and try again") — no modal, no toast
5. On connection success, the screen advances silently to auth — no "Success!" screen
6. `OnboardingViewModel` exposes `OnboardingUiState` sealed class with `Loading`, `Success(url)`, `Error(message)` — no raw `isLoading: Boolean`

## Tasks / Subtasks

- [x] Task 1: Add `koin-compose` to `shared/build.gradle.kts` (AC: all — enables `koinViewModel()`)
  - [x] 1.1: In `shared/build.gradle.kts` commonMain dependencies block, add `implementation(libs.koin.compose)` alongside `libs.koin.core`
  - [x] 1.2: Confirm `libs.koin.compose` resolves to `io.insert-koin:koin-compose:4.0.0` (already in `gradle/libs.versions.toml` line 43 — no new catalog entry needed)

- [x] Task 2: Add `AuthRoute` to `Routes.kt` (AC: 5 — nav target after URL verified)
  - [x] 2.1: Add `@Serializable data object AuthRoute : NavKey` to `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/Routes.kt`
  - [x] 2.2: Register `AuthRoute::class` in `navConfig` `SerializersModule` in `HaNativeNavHost.kt`
  - [x] 2.3: Add stub `entry<AuthRoute> { Box(Modifier.fillMaxSize()) }` in `NavDisplay` entryProvider — Story 3.5 fills this

- [x] Task 3: Create `HaUrlRepository` in data layer (AC: 5, 6)
  - [x] 3.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaUrlRepository.kt`
  - [x] 3.2: `suspend fun testUrl(url: String): Result<Unit>` — HTTP GET `$url/api/` via `HttpClient`; `Result.success(Unit)` on 2xx/401 (HA returns 401 without token — still means server is up); `Result.failure(e)` on network exception or non-HA response
  - [x] 3.3: `suspend fun saveUrl(url: String)` — writes `HaSettingsKeys.HA_URL` to `DataStore<Preferences>` via `edit { prefs -> prefs[HaSettingsKeys.HA_URL] = url }`
  - [x] 3.4: Constructor: `class HaUrlRepository(private val httpClient: HttpClient, private val dataStore: DataStore<Preferences>)`
  - [x] 3.5: Wire into `serverManagerModule()` in `DataModule.kt`: `single { HaUrlRepository(get(), get()) }`

- [x] Task 4: Create `OnboardingViewModel` (AC: 3, 4, 5, 6)
  - [x] 4.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/onboarding/OnboardingViewModel.kt`
  - [x] 4.2: Define `OnboardingUiState` sealed class in same file:
    ```kotlin
    sealed class OnboardingUiState {
        data object Idle : OnboardingUiState()
        data object Loading : OnboardingUiState()
        data class Success(val url: String) : OnboardingUiState()
        data class Error(val message: String) : OnboardingUiState()
    }
    ```
  - [x] 4.3: `class OnboardingViewModel(private val urlRepository: HaUrlRepository, private val serverDiscovery: ServerDiscovery) : ViewModel()`
  - [x] 4.4: `val uiState: StateFlow<OnboardingUiState>` backed by `MutableStateFlow(OnboardingUiState.Idle)`
  - [x] 4.5: `val discoveredServers: StateFlow<List<HaServerInfo>>` — `serverDiscovery.startDiscovery()` collected in `viewModelScope`, initial `emptyList()`
  - [x] 4.6: `fun testUrl(url: String)` — guards on `uiState.value == Loading`, sets Loading, calls `urlRepository.testUrl(url)` in `viewModelScope.launch`, on success calls `urlRepository.saveUrl(url)` then sets `Success(url)`, on failure sets `Error("Can't reach this address — check the URL and try again")`
  - [x] 4.7: Override `onCleared()` — call `serverDiscovery.stopDiscovery()`
  - [x] 4.8: Register in `presentationModule` in `PresentationModule.kt`:
    ```kotlin
    viewModel { OnboardingViewModel(get(), get()) }
    ```

- [x] Task 5: Create `OnboardingScreen` (AC: 1, 2, 3, 4, 5)
  - [x] 5.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/onboarding/OnboardingScreen.kt`
  - [x] 5.2: Composable signature: `@Composable fun OnboardingScreen(onNavigateToAuth: () -> Unit, modifier: Modifier = Modifier)`
  - [x] 5.3: Inject VM via: `val viewModel: OnboardingViewModel = koinViewModel()`
  - [x] 5.4: `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`
  - [x] 5.5: `val discoveredServers by viewModel.discoveredServers.collectAsStateWithLifecycle()`
  - [x] 5.6: `var urlInput by rememberSaveable { mutableStateOf("") }`
  - [x] 5.7: LaunchedEffect on uiState: `if (uiState is OnboardingUiState.Success) onNavigateToAuth()`
  - [x] 5.8: URL `TextField`: `value = urlInput`, `onValueChange = { urlInput = it }`, `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go)`, `keyboardActions = KeyboardActions(onGo = { viewModel.testUrl(urlInput) })`, `enabled = uiState !is OnboardingUiState.Loading`
  - [x] 5.9: Submit button / progress indicator: if `uiState is Loading` show `CircularProgressIndicator()` else show `Button(onClick = { viewModel.testUrl(urlInput) }, enabled = urlInput.isNotBlank() && uiState !is Loading) { Text("Connect") }` — never hide the slot, always occupy same space
  - [x] 5.10: Error text: `if (uiState is Error) Text(text = (uiState as Error).message, color = MaterialTheme.colorScheme.error)` — below the TextField, not a Snackbar/dialog
  - [x] 5.11: Discovered servers list: `LazyColumn` below error — each item is a `Text` or `ListItem` showing `server.name` + `server.host`; `clickable { urlInput = "http://${server.host}:${server.port}" }`

- [x] Task 6: Wire `OnboardingScreen` into `HaNativeNavHost` (AC: 1–5)
  - [x] 6.1: In `HaNativeNavHost.kt` entryProvider, replace `entry<OnboardingRoute> { Box(Modifier.fillMaxSize()) }` with:
    ```kotlin
    entry<OnboardingRoute> {
        OnboardingScreen(
            onNavigateToAuth = {
                backStack.add(AuthRoute)
            }
        )
    }
    ```
  - [x] 6.2: Import `OnboardingScreen` from `com.backpapp.hanative.ui.onboarding`

- [x] Task 7: Build verification
  - [x] 7.1: `./gradlew :shared:compileKotlinAndroid :androidApp:assembleDebug` — BUILD SUCCESSFUL, zero errors
  - [x] 7.2: Confirm no unresolved references to `koinViewModel`, `collectAsStateWithLifecycle`, `AuthRoute`

## Dev Notes

### What Already Exists — Do NOT Recreate

**Navigation (fully implemented):**
- `OnboardingRoute`, `DashboardRoute` in `Routes.kt` (`shared/.../navigation/Routes.kt`)
- `HaNativeNavHost.kt` has stub `entry<OnboardingRoute> { Box(...) }` — **only replace this entry, leave rest unchanged**
- Backstack: `rememberNavBackStack(navConfig, OnboardingRoute)` — initial route is already `OnboardingRoute`

**Data / DI (fully wired, Story 3.1–3.3):**
- `DataStore<Preferences>` provided as `single` in `settingsDataStoreModule()` — `get()` resolves it
- `HttpClient` provided as `single` in `httpClientModule()` — `get()` resolves it
- `ServerDiscovery` provided in `serverDiscoveryModule()` — already in `dataModule`
- `HaSettingsKeys.HA_URL = stringPreferencesKey("ha_url")` in `data/local/HaSettingsKeys.kt`
- `presentationModule` exists but is empty — add `viewModel {}` binding here

**Lifecycle ViewModel:**
- `androidx.lifecycle:lifecycle-viewmodel:2.9.0` already in `shared/build.gradle.kts` commonMain
- `androidx.lifecycle:lifecycle-runtime-compose:2.9.0` already in catalog — add to `shared/build.gradle.kts` commonMain if `collectAsStateWithLifecycle()` is missing

### HaUrlRepository — URL Test Logic

HA exposes `GET /api/` which returns `{"message": "API running."}` when authenticated or `{"message": "401: Unauthorized"}` when not. Both responses confirm HA is reachable. Only use 401 as "server found" — treat any successful TCP connection + HTTP response as success.

```kotlin
suspend fun testUrl(url: String): Result<Unit> = runCatching {
    val normalized = url.trimEnd('/')
    val response = httpClient.get("$normalized/api/")
    // 200 = authenticated, 401 = HA found but no token yet — both mean server is up
    if (response.status.value !in 200..401) {
        throw IllegalStateException("Unexpected response: ${response.status}")
    }
}
```

Ktor `get()` throws `ConnectException` / `UnresolvedAddressException` on unreachable host — `runCatching` catches these. The ViewModel maps `Result.failure` to `Error("Can't reach this address — check the URL and try again")` — exact string per AC4.

Required Ktor imports (already in catalog, no new deps):
```kotlin
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
```

### ViewModel Injection — koin-compose

`koin-compose` (catalog alias `libs.koin.compose`) provides `koinViewModel()` for Compose-aware ViewModel injection. It is NOT yet in `shared/build.gradle.kts` — add to commonMain block:

```kotlin
commonMain.dependencies {
    implementation(libs.koin.core)
    implementation(libs.koin.compose)   // ADD THIS
    // ... existing deps
}
```

`koin-compose` 4.0.0 includes `koinViewModel()` directly — no separate `koin-compose-viewmodel` artifact needed at this version.

Register in `PresentationModule.kt` using `viewModel {}` (Koin's ViewModel scope, not `single`):

```kotlin
import org.koin.compose.viewmodel.dsl.viewModel

val presentationModule = module {
    viewModel { OnboardingViewModel(get(), get()) }
}
```

### collectAsStateWithLifecycle

Requires `androidx.lifecycle:lifecycle-runtime-compose`. If it's not already on the shared commonMain classpath, add it:

```kotlin
implementation(libs.lifecycle.runtime.compose)
```

Check `libs.lifecycle.runtime.compose` in catalog — catalog alias is `lifecycle-runtime-compose` at line 53 of `gradle/libs.versions.toml`.

If `collectAsStateWithLifecycle()` causes compile errors, fall back to `collectAsState()` (no lifecycle dep needed).

### Navigation — Backstack Push Pattern

Compose Navigation 3 (JetBrains) in this project uses `rememberNavBackStack` + `backStack.add()` for forward navigation. From `HaNativeNavHost.kt`:

```kotlin
val backStack = rememberNavBackStack(navConfig, OnboardingRoute)
// ...
onBack = { if (backStack.size > 1) backStack.removeLastOrNull() }
```

To navigate OnboardingRoute → AuthRoute on success:
```kotlin
backStack.add(AuthRoute)
```

Do NOT use `backStack.removeLastOrNull()` before adding — keep OnboardingRoute in stack so back gesture goes back (Story 3.5 or 3.6 may handle back suppression).

### SerializersModule Registration

`navConfig` in `HaNativeNavHost.kt` uses a `SerializersModule` with explicit `subclass` registrations. Must add `AuthRoute` here or app crashes on back-navigation restoration:

```kotlin
private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(OnboardingRoute::class, OnboardingRoute.serializer())
            subclass(AuthRoute::class, AuthRoute.serializer())   // ADD
            subclass(DashboardRoute::class, DashboardRoute.serializer())
        }
    }
}
```

### URL Normalisation

Users commonly enter URLs without protocol (`homeassistant.local:8123`). Prepend `http://` if no scheme detected:

```kotlin
fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
    else "http://$trimmed"
}
```

Call `normalizeUrl(urlInput)` before passing to `viewModel.testUrl()` in the keyboard action. Place `normalizeUrl` in `OnboardingViewModel` or as a private function in the screen file — not in the data layer.

### ServerDiscovery → URL Fill Pattern

`HaServerInfo(name: String, host: String, port: Int)` — construct fill URL as:
```kotlin
"http://${server.host}:${server.port}"
```

Set `urlInput = "http://${server.host}:${server.port}"` in the tap handler — this populates the TextField and lets the user tap Go or press `ImeAction.Go`.

Do NOT auto-submit on discovery tap — let the user confirm the prefilled URL first.

### NavigationBar Visibility During Onboarding

`CompactLayout` in `HaNativeNavHost.kt` currently shows a `NavigationBar` (Dashboard/Rooms/Settings) for all routes. Onboarding should NOT show the nav bar. Options:

1. Check if `backStack.last()` is `OnboardingRoute` or `AuthRoute` → conditionally hide `Scaffold(bottomBar = ...)` 
2. Move nav bar logic to `DashboardRoute` entry only

Simplest fix: add a `showNavBar` flag in `HaNativeNavHost`:
```kotlin
val showNavBar = backStack.lastOrNull() !is OnboardingRoute && backStack.lastOrNull() !is AuthRoute
```
Pass to `CompactLayout(showNavBar = showNavBar)` and `ExpandedLayout`.

This is a necessary UX fix — do NOT ship Story 3.4 with nav bar visible on onboarding screen.

### Testing Limitations

`OnboardingViewModel` depends on `ServerDiscovery` (`expect interface`, OS-level mDNS) and `HaUrlRepository` (live HTTP). Both are integration-only testable — no commonTest coverage feasible without mocks/fakes.

`HaUrlRepository.testUrl()` and `HaUrlRepository.saveUrl()` can be unit-tested with a fake `HttpClient` in `androidUnitTest` if needed — defer to integration validation for MVP.

No new commonTest files required for this story.

### Package Location

All new files follow architecture spec at `_bmad/outputs/architecture.md` section "Project Structure":
```
shared/src/commonMain/kotlin/com/backpapp/hanative/
├── ui/
│   └── onboarding/
│       ├── OnboardingScreen.kt    ← NEW
│       └── OnboardingViewModel.kt ← NEW
├── data/
│   └── remote/
│       └── HaUrlRepository.kt     ← NEW
└── navigation/
    ├── Routes.kt                  ← MODIFY: add AuthRoute
    └── HaNativeNavHost.kt         ← MODIFY: wire screen + AuthRoute
```

### Build Command

```bash
./gradlew :shared:compileKotlinAndroid :androidApp:assembleDebug
```

iOS compile not needed for this story unless Koin module changes break iOS DI registration — if iOS build requested, use `:shared:compileKotlinIosArm64`.

### References

- `Routes.kt`: `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/Routes.kt`
- `HaNativeNavHost.kt`: `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/HaNativeNavHost.kt`
- `HaSettingsKeys.kt`: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/local/HaSettingsKeys.kt`
- `ServerDiscovery` interface: `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/ServerDiscovery.kt`
- `HaServerInfo`: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/HaServerInfo.kt`
- `DataModule.kt`: `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt`
- `PresentationModule.kt`: `shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt`
- Story 3.3 (WebSocket + ServerManager): `_bmad/stories/3-3-ha-websocket-connection-session-persistence.md`
- Architecture (navigation + ViewModel patterns): `_bmad/outputs/architecture.md` sections "Frontend Architecture", "Project Structure"

## Dev Agent Record

### Implementation Notes

**Key decision — `koin-compose-viewmodel`:** Story Dev Notes stated `koin-compose:4.0.0` includes `koinViewModel()` with no separate artifact needed. In practice, `org.koin.compose.viewmodel` package is in `io.insert-koin:koin-compose-viewmodel`, not `koin-compose`. Added `koin-compose-viewmodel = "io.insert-koin:koin-compose-viewmodel:4.0.0"` to catalog and commonMain deps. Build succeeds after this fix.

**`viewModel {}` DSL:** Used `org.koin.compose.viewmodel.dsl.viewModel` from `koin-compose-viewmodel` (not `koin-core`'s `viewModelOf`) to stay consistent with the KMP compose ViewModel pattern.

**Nav bar hiding:** Implemented `showNavBar` flag in `HaNativeNavHost` checking `backStack.lastOrNull()` — hides bottom nav + rail for both `OnboardingRoute` and `AuthRoute` per story spec.

**URL normalisation:** `normalizeUrl()` placed as private function in `OnboardingScreen.kt`. Called before `viewModel.testUrl()` in both ImeAction.Go and Button click handlers.

**No commonTest added:** Per story Dev Notes — `OnboardingViewModel` depends on `ServerDiscovery` (expect interface, OS-level mDNS) and `HaUrlRepository` (live HTTP). Both are integration-only. BUILD SUCCESSFUL with no new test failures.

### Completion Notes

All 7 tasks + subtasks complete. BUILD SUCCESSFUL (`:shared:testAndroidHostTest :androidApp:assembleDebug`). All 6 ACs satisfied:
- AC1: TextField with ImeAction.Go ✓
- AC2: ServerDiscovery list with tap-to-fill ✓
- AC3: CircularProgressIndicator during loading (replaces button slot) ✓
- AC4: Inline error text below TextField (exact string per spec) ✓
- AC5: Silent nav to AuthRoute on success via LaunchedEffect ✓
- AC6: OnboardingUiState sealed class with Idle/Loading/Success/Error ✓

## File List

- `gradle/libs.versions.toml` — added `koin-compose-viewmodel` and `lifecycle-viewmodel-savedstate` catalog entries
- `shared/build.gradle.kts` — added `koin.compose`, `koin.compose.viewmodel`, `lifecycle.runtime.compose`, `lifecycle.viewmodel.savedstate` to commonMain (savedstate added for iOS — `koin-compose-viewmodel` references `androidx.lifecycle.SavedStateHandle`, missing artifact caused IrLinkageError on iOS)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/Routes.kt` — added `AuthRoute`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/HaNativeNavHost.kt` — registered AuthRoute, wired OnboardingScreen, added showNavBar logic
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaUrlRepository.kt` — NEW; review patches: 2xx+401 range, 10s timeout, CancellationException rethrow, `saveUrl` returns `Result`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt` — added HaUrlRepository binding
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt` — added OnboardingViewModel binding
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/onboarding/OnboardingViewModel.kt` — NEW; review patches: scheme-probe `candidateUrls()`, blank-input guard, `onNavigationConsumed()`, saveUrl failure handling
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/onboarding/OnboardingScreen.kt` — NEW; review patches: ImeAction.Go isNotBlank guard, IPv6 bracketing in discovered fill, `onNavigationConsumed()` after navigate, `normalizeUrl` removed (moved to VM)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaReconnectManager.kt` — added `import kotlin.concurrent.Volatile` (KMP commonMain requirement; carried from Story 3.3 patch work)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/ServerManager.kt` — added `import kotlin.concurrent.Volatile` (KMP commonMain requirement; carried from Story 3.3 patch work)
- `androidApp/src/main/AndroidManifest.xml` — added `android.permission.INTERNET` (NsdManager + Ktor HTTP both require it; runtime crash fix)
- `shared/src/iosMain/kotlin/com/backpapp/hanative/MainViewController.kt` — swapped from `HaNativePlaceholderScreen` to `HaNativeNavHost` wrapped in `HaNativeTheme` + `LocalWindowSizeClass.COMPACT` (iOS was rendering blank because nav host was never wired)

## Change Log

| Date | Change |
|------|--------|
| 2026-04-30 | Story 3.4 created — ready-for-dev |
| 2026-04-30 | Story 3.4 implemented — status → review |
| 2026-04-30 | Code review complete — 3 decisions, 8 patches, 9 deferred, 10 dismissed |

## Review Findings

### Decision-Needed

_All resolved 2026-04-30: D1→patch (tighten to 2xx+401), D2→dismissed (keep AC4 single bucket), D3→patch (probe https then http)._

### Patch (all applied 2026-04-30, BUILD SUCCESSFUL)

- [x] [Review][Patch] **Tighten `testUrl` accept range to 2xx + 401 only** — applied: `status !in 200..299 && status != 401` [HaUrlRepository.kt]
- [x] [Review][Patch] **Probe `https://` first, fall back to `http://` for scheme-less input** — applied: `candidateUrls()` in `OnboardingViewModel.kt` returns `[https://, http://]` when no scheme; `testUrl` iterates until success. Discovered-server fill now omits scheme so VM probes both.
- [x] [Review][Patch] **HTTP request timeout (10s) on `testUrl`** — applied via `withTimeout(TEST_URL_TIMEOUT_MS)` [HaUrlRepository.kt]
- [x] [Review][Patch] **`normalizeUrl` case-sensitivity fixed (no longer needed)** — applied: scheme detection moved to VM `candidateUrls()` using `lowercase()` prefix check; `normalizeUrl` removed from screen.
- [x] [Review][Patch] **`saveUrl` IOException now returns `Result.failure` instead of crashing scope** — applied: try/catch in repo with `CancellationException` rethrow; VM maps failure to AC4 error string [HaUrlRepository.kt, OnboardingViewModel.kt]
- [x] [Review][Patch] **`LaunchedEffect(uiState)` re-fire trap fixed** — applied: VM exposes `onNavigationConsumed()` that resets state to `Idle` after navigate; screen calls it inside the `LaunchedEffect`.
- [x] [Review][Patch] **`ImeAction.Go` blank-input guard** — applied: `onGo = { if (urlInput.isNotBlank()) viewModel.testUrl(urlInput) }`; VM also rejects empty after trim.
- [x] [Review][Patch] **`runCatching` cancellation hazard fixed** — applied: explicit try/catch with `CancellationException` rethrow in both `testUrl` and `saveUrl` [HaUrlRepository.kt]
- [x] [Review][Patch] **Volatile imports in `HaReconnectManager.kt` / `ServerManager.kt` acknowledged in File List** — required for KMP commonMain `@Volatile` resolution; documented under File List below.
- [x] [Review][Patch] **IPv6 host bracketing in discovered-server fill** — applied: host wrapped in `[...]` when `:` present [OnboardingScreen.kt]

- [ ] [Review][Patch] **No HTTP timeout on `testUrl` — UI hangs on black-holed IP** [shared/.../HaUrlRepository.kt:15]
- [ ] [Review][Patch] **`normalizeUrl` scheme check is case-sensitive — `"HTTP://x"` becomes `"http://HTTP://x"`** [shared/.../OnboardingScreen.kt:97-99]
- [ ] [Review][Patch] **`saveUrl` IOException unhandled — leaves uiState stuck in Loading + crashes scope** [shared/.../OnboardingViewModel.kt:36-40]
- [ ] [Review][Patch] **`LaunchedEffect(uiState)` re-fires `onNavigateToAuth` on back from Auth — user trapped** [shared/.../OnboardingScreen.kt:39-41] — fix: reset `_uiState` to `Idle` after success, or use single-shot Channel/SharedFlow event
- [ ] [Review][Patch] **`ImeAction.Go` bypasses `urlInput.isNotBlank()` guard — fires `testUrl("http://")` on empty/whitespace input** [shared/.../OnboardingScreen.kt:54-56]
- [ ] [Review][Patch] **`runCatching` in `testUrl` swallows `CancellationException`** [shared/.../HaUrlRepository.kt:13] — fix: catch + rethrow `CancellationException`, or use explicit try/catch
- [ ] [Review][Patch] **Volatile imports added to `HaReconnectManager.kt` and `ServerManager.kt` are out of Story 3.4 scope** [shared/.../HaReconnectManager.kt:3, ServerManager.kt:3] — Story 3.3 patch leaked into 3.4 commit; either revert or update File List and commit separately
- [ ] [Review][Patch] **Discovered-server fill ignores IPv6 bracketing — `http://fe80::1:8123` malformed** [shared/.../OnboardingScreen.kt:81]

### Deferred

- [x] [Review][Defer] **`AuthRoute` is placeholder `Box` — Story 3.5 fills it** [shared/.../HaNativeNavHost.kt:54] — deferred, planned in Story 3.5
- [x] [Review][Defer] **`AuthRoute` restoration after process death = blank screen, no nav out** [shared/.../HaNativeNavHost.kt:54] — deferred, resolves with Story 3.5/3.6 content
- [x] [Review][Defer] **`stopDiscovery()` double-call risk — Android NsdManager throws `IllegalArgumentException` on second stop** [shared/.../OnboardingViewModel.kt:55-57] — deferred, verify in integration test against platform impls
- [x] [Review][Defer] **`koinViewModel()` scope vs nav3 entry lifecycle — VM may rebuild on back-and-forth** [shared/.../HaNativeNavHost.kt:54-58] — deferred, architectural concern; revisit when Auth + Dashboard wired
- [x] [Review][Defer] **Process death mid-Loading: `urlInput` restored, `uiState` resets to `Idle` silently** [shared/.../OnboardingScreen.kt:36] — deferred, low-pri UX polish
- [x] [Review][Defer] **Discovered-server tap silently overwrites typed input; LazyColumn reorder shifts targets** [shared/.../OnboardingScreen.kt:80-83] — deferred, UX polish
- [x] [Review][Defer] **`urlInput` has no length cap — pathological paste could exceed Bundle 1MB on save** [shared/.../OnboardingScreen.kt:35] — deferred, defensive edge
- [x] [Review][Defer] **`testUrl` appends `/api/` to user-provided path — `http://x/lovelace` becomes `/lovelace/api/`** [shared/.../HaUrlRepository.kt:14] — deferred, user-error tolerance
- [x] [Review][Defer] **Generic error message hides 401/auth/TLS/timeout signal** [shared/.../OnboardingViewModel.kt:42-44] — deferred, AC4 pins single string for MVP
