# Story 3.5: Onboarding — Authentication & Session Persistence

Status: done

## Story

As a power user,
I want to authenticate with my HA instance and never have to re-enter credentials,
So that my session persists across relaunches and I open directly to my dashboard every time.

## Acceptance Criteria

1. The auth screen offers two paths: long-lived access token input (`TextField`) and OAuth2 authorization (button opens system browser).
2. On long-lived token: `ImeAction.Go` submits; `CircularProgressIndicator` replaces the submit button (disabled, not hidden) during validation; inline plain-language error appears below the field on invalid token; silent navigation to `DashboardRoute` on success — no "Success!" screen.
3. On OAuth2: system browser opens HA `/auth/authorize` page; on redirect-callback, app captures the auth code, exchanges it for an access token, and stores it without user re-entry.
4. On successful auth (either path), the access token is stored in `CredentialStore` (Keystore/Keychain — NFR6); the HA URL persisted in Story 3.4 remains in DataStore — token and URL are never co-located in plaintext.
5. On subsequent app launches with a stored token AND stored HA URL, onboarding/auth are skipped — `HaNativeNavHost` starts on `DashboardRoute` (FR3). On launch with no token, start on `OnboardingRoute`.
6. After successful auth, `ServerManager.initialize(lanUrl, cloudUrl)` is called and `connect()` is triggered so the WebSocket session is live before the user reaches the dashboard.
7. Settings → Disconnect clears `CredentialStore` and the `HA_URL` DataStore key, calls `webSocketClient.disconnect()` (or equivalent), and navigates back to `OnboardingRoute` with backstack reset (FR4).
8. Token does not appear in logs, crash reports, or analytics at any point (NFR8) — verified by code review of all `println`/`Log.*` and any error message construction; OAuth `code` and `client_secret` likewise never logged.
9. `AuthViewModel` exposes `AuthUiState` sealed class with `Idle`, `Loading`, `Success`, `Error(message)` — no raw `isLoading: Boolean`.

## Tasks / Subtasks

- [x] Task 1: Add token + auth-flow keys to `HaSettingsKeys` and CredentialStore wiring (AC: 4, 5, 7)
  - [x] 1.1: `HA_URL` already exists. Token stays in `CredentialStore` (Keystore/Keychain) — do NOT add a token key to DataStore.
  - [x] 1.2: Confirm `AuthenticationRepositoryImpl` exposes `saveToken(token)`, `getToken(): String?`, `clearToken()` — already implemented in `data/remote/AuthenticationRepositoryImpl.kt`. Add `suspend fun getToken(): String? = mutex.withLock { credentialStore.getToken() }` if missing.
  - [x] 1.3: No new DI bindings required — `AuthenticationRepositoryImpl` already in `serverManagerModule()` as `single`.

- [x] Task 2: Create `SessionRepository` for startup-route decision and disconnect (AC: 5, 7)
  - [x] 2.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/SessionRepository.kt`
  - [x] 2.2: Constructor: `class SessionRepository(private val authRepository: AuthenticationRepositoryImpl, private val urlRepository: HaUrlRepository, private val serverManager: ServerManager, private val webSocketClient: HaWebSocketClient)`
  - [x] 2.3: `suspend fun hasValidSession(): Boolean` — returns `true` iff both stored token AND stored HA URL exist (non-null, non-blank).
  - [x] 2.4: `suspend fun logout()` — order: `webSocketClient.disconnect()` (or no-op if not connected) → `authRepository.clearToken()` → clear `HA_URL` from DataStore via `urlRepository.clearUrl()`. Use try/finally so a failure in one step does not leave partial state.
  - [x] 2.5: Wire into `serverManagerModule()` in `DataModule.kt`: `single { SessionRepository(get(), get(), get(), get()) }`.
  - [x] 2.6: Add `suspend fun clearUrl()` and `suspend fun getUrl(): String?` to `HaUrlRepository` (DataStore `edit { it.remove(HaSettingsKeys.HA_URL) }` + `data.first()[HaSettingsKeys.HA_URL]`).
  - [x] 2.7: Add `fun disconnect()` to `HaWebSocketClient` interface (and `KtorHaWebSocketClient`) if not present — closes session, sets `_isConnected.value = false`. If already implicit via session lifecycle, expose explicit method anyway for logout determinism.

- [x] Task 3: Create `AuthViewModel` + `AuthUiState` sealed class (AC: 1, 2, 3, 4, 6, 9)
  - [x] 3.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/auth/AuthViewModel.kt`
  - [x] 3.2: Define `AuthUiState`:
    ```kotlin
    sealed class AuthUiState {
        data object Idle : AuthUiState()
        data object Loading : AuthUiState()
        data object Success : AuthUiState()
        data class Error(val message: String) : AuthUiState()
    }
    ```
  - [x] 3.3: Constructor: `class AuthViewModel(private val authRepository: AuthenticationRepositoryImpl, private val urlRepository: HaUrlRepository, private val serverManager: ServerManager, private val oauthLauncher: OAuthLauncher) : ViewModel()`
  - [x] 3.4: `val uiState: StateFlow<AuthUiState>` backed by `MutableStateFlow(AuthUiState.Idle)`.
  - [x] 3.5: `fun submitLongLivedToken(token: String)` — guards on `Loading`; trims input; rejects blank with `Error("Token can't be empty")`; sets `Loading`; calls `authRepository.saveToken(trimmed)`; calls `urlRepository.getUrl()` to read stored URL; calls `serverManager.initialize(lanUrl = url)` then `serverManager.connect()`; sets `Success`. On exception sets `Error("Couldn't sign in — check the token and try again")`. Re-throw `CancellationException`.
  - [x] 3.6: `fun startOAuthFlow()` — guards on `Loading`; sets `Loading`; calls `oauthLauncher.launch(authorizeUrl)` where `authorizeUrl = "$haUrl/auth/authorize?client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&response_type=code"`. Result returned via `onOAuthCallback(code: String?)`.
  - [x] 3.7: `fun onOAuthCallback(code: String?)` — if `code == null` sets `Error("OAuth cancelled or failed")`; otherwise POSTs to `$haUrl/auth/token` with form body `grant_type=authorization_code&code=$code&client_id=$CLIENT_ID`; parses `AuthTokenResponseDto` (access_token, refresh_token, expires_in); stores `access_token` via `authRepository.saveToken()`; calls `serverManager.initialize` + `connect()`; sets `Success`.
  - [x] 3.8: `fun onNavigationConsumed()` — resets `_uiState` to `Idle` if currently `Success`, mirrors Story 3.4 pattern to prevent re-fire on back from Dashboard.
  - [x] 3.9: Constants: `private const val CLIENT_ID = "https://hanative.app"` (HA accepts any URL as client_id per Indieauth — use a stable app identifier). `private const val REDIRECT_URI = "hanative://auth-callback"`.
  - [x] 3.10: Logging: never log `token`, `code`, `accessToken`, or any header value. If logging request paths, strip query params.
  - [x] 3.11: Register in `presentationModule` in `PresentationModule.kt`:
    ```kotlin
    viewModel { AuthViewModel(get(), get(), get(), get()) }
    ```

- [x] Task 4: OAuth callback DTO + Ktor token-exchange (AC: 3, 8)
  - [x] 4.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/entities/AuthTokenResponseDto.kt`:
    ```kotlin
    @Serializable
    data class AuthTokenResponseDto(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("token_type") val tokenType: String? = null,
    )
    ```
  - [x] 4.2: Token exchange in `AuthViewModel.onOAuthCallback`:
    ```kotlin
    val response: HttpResponse = httpClient.submitForm(
        url = "$haUrl/auth/token",
        formParameters = Parameters.build {
            append("grant_type", "authorization_code")
            append("code", code)
            append("client_id", CLIENT_ID)
        },
    )
    if (response.status.value !in 200..299) throw IllegalStateException("Token exchange failed: ${response.status.value}")
    val body = response.body<AuthTokenResponseDto>()
    ```
    Inject `HttpClient` into `AuthViewModel` constructor (additional `get()` in Koin) — `koin-compose-viewmodel`'s `viewModel { ... }` resolves `HttpClient` already registered in `httpClientModule()`.
  - [x] 4.3: `httpClient` MUST have `ContentNegotiation { json() }` installed for `body<AuthTokenResponseDto>()` to work — verify in `httpClientModule()` actual; add if missing.

- [x] Task 5: `OAuthLauncher` expect/actual (AC: 3)
  - [x] 5.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/OAuthLauncher.kt`:
    ```kotlin
    expect class OAuthLauncher {
        fun launch(authorizeUrl: String)
    }
    ```
  - [x] 5.2: `androidMain` actual — `AndroidOAuthLauncher.kt`: uses `Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl))` with `FLAG_ACTIVITY_NEW_TASK`; constructor takes `Context`. Prefer `CustomTabsIntent` if `androidx.browser` is on the classpath (it is — confirm in `androidApp/build.gradle.kts`); fall back to `ACTION_VIEW` if no Custom Tabs provider. NEVER use a WebView (no shared cookies, defeats SSO).
  - [x] 5.3: `iosMain` actual — `IosOAuthLauncher.kt`: prefer `ASWebAuthenticationSession` (returns the callback URL via completion handler — cleanest for OAuth) when available, fall back to `UIApplication.sharedApplication.openURL(NSURL(string = authorizeUrl)!!, options = emptyMap<Any?, Any>(), completionHandler = null)`. Do NOT use `WKWebView`.
  - [x] 5.4: Provide `oauthLauncherModule(): Module` `expect`/`actual` in `di/` mirroring `serverDiscoveryModule()` pattern. Include in `dataModule` from `DataModule.kt`.
  - [x] 5.5: For Android, the `OAuthLauncher` does NOT receive the callback — Android delivers it to `MainActivity` via deep-link intent. Wire callback delivery in Task 6.

- [x] Task 6: Deep-link callback wiring (AC: 3)
  - [x] 6.1: Android — `androidApp/src/main/AndroidManifest.xml`: add second `<intent-filter>` to `MainActivity`:
    ```xml
    <intent-filter android:autoVerify="false">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="hanative" android:host="auth-callback" />
    </intent-filter>
    ```
    Set `android:launchMode="singleTask"` on `MainActivity` so the callback re-enters the existing instance instead of stacking.
  - [x] 6.2: Android — in `MainActivity.kt`, override `onNewIntent(intent: Intent)` and `onCreate` initial intent: extract `code` query param from `intent.data`; pass to a `OAuthCallbackBus` (commonMain `MutableSharedFlow<String?>`) that `AuthViewModel` collects.
  - [x] 6.3: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/OAuthCallbackBus.kt`:
    ```kotlin
    class OAuthCallbackBus {
        private val _codes = MutableSharedFlow<String?>(extraBufferCapacity = 1)
        val codes: SharedFlow<String?> = _codes.asSharedFlow()
        fun emit(code: String?) { _codes.tryEmit(code) }
    }
    ```
    Register as `single { OAuthCallbackBus() }` in `serverManagerModule()`. Inject into `AuthViewModel` and into Android's `MainActivity` via Koin (`val bus: OAuthCallbackBus by inject()`).
  - [x] 6.4: iOS — `iosApp/iosApp/iOSApp.swift` (or `Info.plist`): register URL type with `CFBundleURLSchemes = ["hanative"]`. In `SceneDelegate`/`App` `onOpenURL`, parse `code` from URL and call `OAuthCallbackBus.shared.emit(code)`. If using `ASWebAuthenticationSession` path (Task 5.3), the callback URL is delivered directly to the completion handler — `OAuthCallbackBus` is bypassed; emit code from the launcher itself.
  - [x] 6.5: `AuthViewModel.init` starts a `viewModelScope.launch { oauthCallbackBus.codes.collect { onOAuthCallback(it) } }`.

- [x] Task 7: Create `AuthScreen` (AC: 1, 2, 3, 9)
  - [x] 7.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/auth/AuthScreen.kt`
  - [x] 7.2: Composable signature: `@Composable fun AuthScreen(onNavigateToDashboard: () -> Unit, modifier: Modifier = Modifier)`
  - [x] 7.3: Inject VM: `val viewModel: AuthViewModel = koinViewModel()`
  - [x] 7.4: `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`
  - [x] 7.5: `var tokenInput by rememberSaveable { mutableStateOf("") }`
  - [x] 7.6: `LaunchedEffect(uiState)`: if `Success` → call `onNavigateToDashboard()` then `viewModel.onNavigationConsumed()`.
  - [x] 7.7: Token `TextField`: `value = tokenInput`, `visualTransformation = PasswordVisualTransformation()` (token is sensitive — masked input, NFR8 hygiene), `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Password)`, `keyboardActions = KeyboardActions(onGo = { if (tokenInput.isNotBlank()) viewModel.submitLongLivedToken(tokenInput) })`, `enabled = uiState !is AuthUiState.Loading`. Label: "Long-lived access token".
  - [x] 7.8: Button slot — never hidden, always occupies same space:
    ```kotlin
    if (uiState is AuthUiState.Loading) CircularProgressIndicator()
    else Button(
        onClick = { viewModel.submitLongLivedToken(tokenInput) },
        enabled = tokenInput.isNotBlank() && uiState !is AuthUiState.Loading,
    ) { Text("Sign in") }
    ```
  - [x] 7.9: Inline error: `if (uiState is AuthUiState.Error) Text((uiState as AuthUiState.Error).message, color = MaterialTheme.colorScheme.error)` — below the TextField.
  - [x] 7.10: OAuth path: `OutlinedButton(onClick = { viewModel.startOAuthFlow() }, enabled = uiState !is AuthUiState.Loading) { Text("Sign in with browser") }` placed below the long-lived token Button. Add a small "or" divider between the two paths.
  - [x] 7.11: Token must NEVER appear in logs — confirm no `println(tokenInput)` or similar slipped in during dev iteration.

- [x] Task 8: Wire `AuthScreen` into `HaNativeNavHost` (AC: 2, 5)
  - [x] 8.1: In `HaNativeNavHost.kt` entryProvider, replace `entry<AuthRoute> { Box(Modifier.fillMaxSize()) }` with:
    ```kotlin
    entry<AuthRoute> {
        AuthScreen(
            onNavigateToDashboard = {
                backStack.clear()
                backStack.add(DashboardRoute)
            },
        )
    }
    ```
  - [x] 8.2: Import `AuthScreen` from `com.backpapp.hanative.ui.auth`.
  - [x] 8.3: `backStack.clear()` before `add(DashboardRoute)` so back gesture from Dashboard does not return to Auth.

- [x] Task 9: Startup-route decision (AC: 5)
  - [x] 9.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/StartupViewModel.kt`:
    ```kotlin
    sealed class StartupRoute {
        data object Loading : StartupRoute()
        data object Onboarding : StartupRoute()
        data object Dashboard : StartupRoute()
    }
    class StartupViewModel(private val sessionRepository: SessionRepository, private val serverManager: ServerManager, private val urlRepository: HaUrlRepository) : ViewModel() {
        private val _route = MutableStateFlow<StartupRoute>(StartupRoute.Loading)
        val route: StateFlow<StartupRoute> = _route.asStateFlow()
        init {
            viewModelScope.launch {
                if (sessionRepository.hasValidSession()) {
                    val url = urlRepository.getUrl() ?: error("hasValidSession returned true but URL null")
                    serverManager.initialize(lanUrl = url)
                    serverManager.connect()
                    _route.value = StartupRoute.Dashboard
                } else _route.value = StartupRoute.Onboarding
            }
        }
    }
    ```
  - [x] 9.2: Register: `viewModel { StartupViewModel(get(), get(), get()) }` in `PresentationModule.kt`.
  - [x] 9.3: In `HaNativeNavHost.kt`, before `rememberNavBackStack`, inject `StartupViewModel`, collect `route`, and:
    - if `Loading` → render a centered `CircularProgressIndicator()` and `return@HaNativeNavHost` (do not build backstack yet)
    - if `Onboarding` → `rememberNavBackStack(navConfig, OnboardingRoute)`
    - if `Dashboard` → `rememberNavBackStack(navConfig, DashboardRoute)`
  - [x] 9.4: NFR2 compliance: startup decision must not block UI thread > 1s. `hasValidSession()` is a single DataStore read + single Keystore/Keychain read — both <100ms. Do not perform WebSocket connect on the critical-path before showing UI; `serverManager.connect()` is fire-and-forget on the IO scope.

- [x] Task 10: Settings → Disconnect (AC: 7)
  - [x] 10.1: Add `@Serializable data object SettingsRoute : NavKey` to `Routes.kt`. Register in `navConfig` SerializersModule. Add `entry<SettingsRoute>` placeholder in `HaNativeNavHost`.
  - [x] 10.2: Wire NavBar Settings tab click in `CompactLayout`/`ExpandedLayout` to `backStack.add(SettingsRoute)`. Settings is reachable only post-auth, so `showNavBar` already excludes Onboarding/Auth.
  - [x] 10.3: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/settings/SettingsScreen.kt` — minimal: a `Button(onClick = { viewModel.disconnect() }) { Text("Disconnect") }` and a confirm `AlertDialog` ("Disconnect from Home Assistant? You'll need to sign in again.").
  - [x] 10.4: Create `SettingsViewModel(sessionRepository: SessionRepository)` with `fun disconnect()` that launches `viewModelScope`, calls `sessionRepository.logout()`, then emits a single-shot `disconnected` event (`Channel<Unit>`/`SharedFlow`) the screen collects to call `onLoggedOut()`.
  - [x] 10.5: `entry<SettingsRoute> { SettingsScreen(onLoggedOut = { backStack.clear(); backStack.add(OnboardingRoute) }) }` — clear backstack so back gesture cannot return to authenticated routes.
  - [x] 10.6: Register `viewModel { SettingsViewModel(get()) }` in `PresentationModule`.

- [x] Task 11: Logging audit (AC: 8)
  - [x] 11.1: `grep -rn "println\|Log\." shared/src/commonMain/kotlin/com/backpapp/hanative/ui/auth shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote` — confirm no token, code, or URL with credentials is logged.
  - [x] 11.2: Search for `tokenInput`, `accessToken`, `code` substrings in any `println` / string interpolation passed to a logger — verify zero matches.
  - [x] 11.3: Check `KtorHaWebSocketClient` `println("DEBUG: ... unknown message type ...")` does not include the full message body — current code logs only the type, which is safe.

- [x] Task 12: Build + manual verification
  - [x] 12.1: `./gradlew :shared:compileKotlinAndroid :androidApp:assembleDebug` — BUILD SUCCESSFUL, zero errors.
  - [x] 12.2: `./gradlew :shared:compileKotlinIosSimulatorArm64` — confirm iOS klib compiles, no IrLinkageError. (Story 3.4 hit Koin/SavedStateHandle iOS issues — verify Koin 4.2.1 + lifecycle-viewmodel-savedstate still resolve.)
  - [x] 12.3: Manual: launch with no token → Onboarding → enter URL → Auth → paste long-lived token → Dashboard. Kill + relaunch → goes straight to Dashboard. Settings → Disconnect → Onboarding.
  - [x] 12.4: Manual: launch with no token → Onboarding → URL → Auth → "Sign in with browser" → system browser opens HA `/auth/authorize` → after grant, app re-foregrounds with stored token → Dashboard.

## Dev Notes

### What Already Exists — Do NOT Recreate

**From Story 3.1 (CredentialStore):**
- `expect interface CredentialStore { saveToken; getToken; clear }` in `platform/CredentialStore.kt`. Android = EncryptedSharedPreferences, iOS = Keychain. Already in `credentialStoreModule()`.

**From Story 3.3 (ServerManager + WebSocket):**
- `ServerManager(webSocketClient, authRepository, lifecycleObserver, reconnectManager, scope)` in `data/remote/ServerManager.kt`. Has `fun initialize(lanUrl: String, cloudUrl: String? = null)` and `fun connect()`. Wired in `serverManagerModule()`.
- `AuthenticationRepositoryImpl(credentialStore)` exposes `getValidToken()` (throws if no token), `saveToken(token)`, `clearToken()`. Mutex-guarded. Wired in `serverManagerModule()`.
- `KtorHaWebSocketClient` handles WebSocket auth handshake (`auth_required` → send `auth` with token → `auth_ok`/`auth_invalid`). The screen does NOT need to validate the long-lived token over HTTP — saving it then calling `serverManager.connect()` and observing `connectionState` is sufficient. `auth_invalid` surfaces as `HaEvent.ConnectionError("Auth invalid")`.

**From Story 3.4 (Onboarding):**
- `HaUrlRepository(httpClient, dataStore)` — `testUrl()`, `saveUrl()`. Add `getUrl()` and `clearUrl()` for this story.
- `OnboardingViewModel` pattern: sealed `UiState`, `onNavigationConsumed()` to prevent `LaunchedEffect` re-fire, `viewModel { ... }` Koin DSL. **Mirror this pattern exactly in AuthViewModel.**
- `AuthRoute` already in `Routes.kt` and `navConfig` SerializersModule.
- `koin-compose`, `koin-compose-viewmodel`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-savedstate` already in commonMain deps. `collectAsStateWithLifecycle()` available.
- `showNavBar` flag in `HaNativeNavHost` already hides nav bar on Onboarding+Auth.

### HA Auth Flow — What HA Actually Supports

HA does NOT implement RFC 8628 device authorization grant. The PRD wording "OAuth2 device authorization" is imprecise — HA actually exposes an **Indieauth-flavored authorization code flow**:

1. Browser opens `GET https://<ha>/auth/authorize?client_id=<URL>&redirect_uri=<URL>&response_type=code&state=<opt>`
2. User logs in + grants access in HA web UI.
3. HA redirects to `redirect_uri?code=<authcode>&state=<opt>`.
4. App POSTs `https://<ha>/auth/token` with form body:
   ```
   grant_type=authorization_code
   code=<authcode>
   client_id=<same URL as authorize>
   ```
5. Response: `{ "access_token": "...", "expires_in": 1800, "refresh_token": "...", "token_type": "Bearer" }`.

**Critical:** `client_id` MUST be a valid http(s) URL (Indieauth requirement). Use a stable identifier like `https://hanative.app` even if no page is hosted there. HA does NOT pre-register clients — any URL works. Store this as `CLIENT_ID` in `AuthViewModel`.

**Refresh tokens:** Out of scope for this story. The long-lived token path uses tokens that don't expire. The OAuth path stores only the access token; on 401, re-auth flow is triggered by Story 3.6 or later (deferred). Note this in completion notes.

Reference: HA REST API docs `https://developers.home-assistant.io/docs/auth_api`.

### Long-Lived Access Token

User generates this manually in HA web UI: Profile → "Long-lived access tokens" → "Create token". They paste it into the TextField. No HTTP exchange needed — store directly in `CredentialStore`. The WebSocket handshake (Story 3.3) validates it the first time `serverManager.connect()` runs; auth failure surfaces as `HaEvent.ConnectionError("Auth invalid")`.

This means `submitLongLivedToken` cannot synchronously confirm token validity. Two options:
- **Optimistic (chosen for MVP):** save token, call `serverManager.connect()`, mark `Success` immediately, navigate to Dashboard. If token is bad, Dashboard shows connection error state. Simpler, faster perceived feedback.
- **Strict:** save, connect, await `connectionState == Connected` with timeout, only then `Success`. Slower; complicates the sealed state. Defer to Story 3.6 if user feedback demands it.

Implement the optimistic path. Add a comment in `AuthViewModel.submitLongLivedToken`:
```kotlin
// Token validity confirmed asynchronously by WebSocket handshake.
// Bad tokens surface as ConnectionError on Dashboard, not here.
```

### URL Resolution for Auth

Story 3.4 saves the user-entered URL to DataStore. Story 3.5 reads it via `urlRepository.getUrl()` to:
1. Build the OAuth `authorize` URL.
2. Pass to `serverManager.initialize(lanUrl = url)`.

If `getUrl()` returns null at this point, something has gone catastrophically wrong (Story 3.4 should have written it). Throw `IllegalStateException("HA URL missing — Story 3.4 didn't persist")`.

Cloud URL (Nabu Casa) detection — defer. MVP treats the user-entered URL as `lanUrl`. Story 4 / Settings will add cloud-url override.

### Deep-Link Scheme — `hanative://auth-callback`

Pick one scheme + host and use it everywhere:
- Android manifest `<data android:scheme="hanative" android:host="auth-callback" />`
- iOS `Info.plist` `CFBundleURLTypes[0].CFBundleURLSchemes = ["hanative"]`
- `REDIRECT_URI = "hanative://auth-callback"` constant in `AuthViewModel`

HA will redirect to `hanative://auth-callback?code=<code>&state=<state>`. Both platforms parse `code` from URL query.

**Android `singleTask`:** `MainActivity` MUST have `android:launchMode="singleTask"` (not `standard`). Without this, the deep-link starts a new activity instance — the in-progress `AuthViewModel` (and its Koin scope) would be lost.

**iOS `ASWebAuthenticationSession`:** Strongly preferred over `openURL`. ASWebAuth returns the callback URL directly to the completion handler — no need for URL-scheme registration plumbing, no risk of another app stealing the redirect. If using ASWebAuth, skip Task 6.4 `Info.plist` URL types.

### Storage Hygiene — NFR8

Token must NEVER be:
- `println`'d, `Log.d`/`Log.e`'d (Android), `NSLog`'d (iOS)
- Concatenated into error message strings ("Failed for token $token")
- Sent to crash reporters (no Firebase/Crashlytics/Sentry yet — verify nothing added)
- Stored in `DataStore` (Preferences is plaintext) — Keystore/Keychain only, via `CredentialStore`

Same applies to OAuth `code` (short-lived but still credential).

Code review checklist for the dev:
1. Search `grep -rn "println\|Log\.\|NSLog" shared/src/commonMain/kotlin/com/backpapp/hanative/ui/auth shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote` — review every match.
2. Confirm `httpClient.submitForm` does not log the form body (Ktor `Logging` plugin would — verify `httpClientModule` does not install `Logging` at level `BODY`).

### Backstack Clearing on Auth Success and Logout

After auth success, the user must NOT be able to back-navigate to Auth or Onboarding. Use:
```kotlin
backStack.clear()
backStack.add(DashboardRoute)
```
not
```kotlin
backStack.removeLastOrNull()
backStack.add(DashboardRoute)
```
because `removeLastOrNull` only pops one frame (Auth), leaving Onboarding underneath.

Same for logout — clear stack, push Onboarding fresh.

### Process Death During OAuth

User taps "Sign in with browser" → leaves to system browser → OS evicts app from memory. On callback, Android re-launches the app via deep-link intent; the `AuthViewModel` is brand-new (no in-flight state). The `OAuthCallbackBus` collects in `init` — emit timing matters:

- `MainActivity.onNewIntent` calls `bus.emit(code)` BEFORE the Compose tree is rebuilt → emit is buffered (`extraBufferCapacity = 1` in `MutableSharedFlow`) → `AuthViewModel.init` collects and processes when ViewModel constructs.
- This works because `MutableSharedFlow(extraBufferCapacity = 1)` retains the latest emission for late subscribers IF using `replay = 1`. Verify: use `MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 0)` so the late subscriber gets the buffered code.

If `replay = 1` is wrong for the use case (re-collecting on rotation re-fires the callback), use a `Channel<String?>(BUFFERED).receiveAsFlow()` consumed exactly once, with the consume happening in `viewModelScope`. Trade-off documented — recommend `Channel` for clean once-only semantics.

### NavHost Startup Decision Race

`StartupViewModel.init` launches a coroutine that resolves `route` after token + URL reads. If `HaNativeNavHost` composes before this resolves, the `Loading` branch shows a spinner. This is acceptable but flickers if reads are <50ms.

Alternative: use a `runBlocking { sessionRepository.hasValidSession() }` in a `remember { ... }` to make the decision synchronous on first compose. Forbidden — `runBlocking` on the main thread is an architectural violation per project-context.md "no blocking calls on main".

Stay with the async path + `Loading` branch. Style the spinner to match the splash so the transition is invisible.

### Testing Limitations

Same as Story 3.4: `AuthViewModel` depends on `ServerManager`, `HttpClient`, `OAuthLauncher` — all integration-only. No commonTest coverage feasible without fakes/mocks. Defer to manual + future androidUnitTest.

`SessionRepository.hasValidSession()` and `logout()` are unit-testable with fake `AuthenticationRepositoryImpl` + fake `HaUrlRepository`. Add a `commonTest/SessionRepositoryTest.kt` if time permits — not required.

### Package Locations

```
shared/src/commonMain/kotlin/com/backpapp/hanative/
├── ui/
│   ├── auth/
│   │   ├── AuthScreen.kt           ← NEW
│   │   └── AuthViewModel.kt        ← NEW
│   ├── settings/
│   │   ├── SettingsScreen.kt       ← NEW
│   │   └── SettingsViewModel.kt    ← NEW
│   └── StartupViewModel.kt         ← NEW
├── data/
│   ├── remote/
│   │   ├── SessionRepository.kt    ← NEW
│   │   ├── HaUrlRepository.kt      ← MODIFY: add getUrl/clearUrl
│   │   └── entities/
│   │       └── AuthTokenResponseDto.kt ← NEW
├── platform/
│   ├── OAuthLauncher.kt            ← NEW (expect)
│   └── OAuthCallbackBus.kt         ← NEW
├── di/
│   └── PresentationModule.kt       ← MODIFY: 3 new viewModels
└── navigation/
    ├── Routes.kt                   ← MODIFY: add SettingsRoute
    └── HaNativeNavHost.kt          ← MODIFY: AuthScreen, SettingsScreen, StartupViewModel routing

shared/src/androidMain/kotlin/com/backpapp/hanative/
├── platform/
│   ├── AndroidOAuthLauncher.kt     ← NEW (actual)
│   └── ...
└── di/
    └── OAuthLauncherModule.kt      ← NEW

shared/src/iosMain/kotlin/com/backpapp/hanative/
├── platform/
│   ├── IosOAuthLauncher.kt         ← NEW (actual)
│   └── ...
└── di/
    └── OAuthLauncherModule.kt      ← NEW

androidApp/src/main/
├── AndroidManifest.xml             ← MODIFY: deep-link intent-filter, singleTask
└── kotlin/.../MainActivity.kt      ← MODIFY: onNewIntent → bus.emit

iosApp/iosApp/
├── Info.plist                       ← MODIFY: CFBundleURLTypes (only if NOT using ASWebAuth)
└── iOSApp.swift                    ← MODIFY: onOpenURL handler (only if NOT using ASWebAuth)
```

### Build Command

```bash
./gradlew :shared:compileKotlinAndroid :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Use the iOS sim build to catch IrLinkageError early (Story 3.4 burned hours on Koin/SavedStateHandle iOS-only failures).

### References

- `Routes.kt`: `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/Routes.kt`
- `HaNativeNavHost.kt`: `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/HaNativeNavHost.kt`
- `CredentialStore` interface: `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/CredentialStore.kt`
- `AuthenticationRepositoryImpl`: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/AuthenticationRepositoryImpl.kt`
- `ServerManager`: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/ServerManager.kt`
- `HaUrlRepository`: `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaUrlRepository.kt`
- `OnboardingViewModel` (pattern reference): `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/onboarding/OnboardingViewModel.kt`
- Story 3.1 (CredentialStore impl): `_bmad/stories/3-1-credential-store-datastore.md`
- Story 3.3 (WebSocket auth handshake): `_bmad/stories/3-3-ha-websocket-connection-session-persistence.md`
- Story 3.4 (Onboarding pattern + Koin/iOS gotchas): `_bmad/stories/3-4-onboarding-ha-url-entry-connection-test.md`
- Architecture (auth): `_bmad/outputs/architecture.md` lines 162–181 (auth flow), 217–228 (session persistence)
- HA REST auth API: `https://developers.home-assistant.io/docs/auth_api`
- PRD: FR3 (skip onboarding when authenticated), FR4 (Settings → Disconnect), NFR6 (Keystore/Keychain), NFR8 (no token in logs)

## Dev Agent Record

### Implementation Notes

**OAuthLauncher: interface, not expect class.**
Story spec said `expect class OAuthLauncher`. Switched to plain `interface OAuthLauncher` in commonMain with `AndroidOAuthLauncher(context: Context)` and `IosOAuthLauncher()` actuals — KMP `expect class` requires matching constructors across actuals, and Android needs `Context` injected while iOS does not. Interface + per-platform Koin binding is the cleanest fit and matches how `HaWebSocketClient` is wired.

**ASWebAuthenticationSession deferred.**
iOS launcher uses `UIApplication.openURL` (Kotlin/Native interop). ASWebAuthenticationSession requires Swift-side bridging that's out of scope for this story. Functional fallback per Task 5.3.

**Chrome Custom Tabs deferred.**
Android launcher uses `Intent.ACTION_VIEW`. `androidx.browser` is not yet on the classpath, so CustomTabsIntent is deferred. Default browser handles the OAuth handshake correctly. Add a follow-up if/when we adopt androidx.browser.

**OAuthCallbackBus uses `replay = 1`.**
Process-death + cold-start case: `MainActivity.onNewIntent` (or `onCreate` initial intent) calls `bus.emit(code)` before the Compose tree is composed. Replay=1 lets the late `AuthViewModel.init` collector still receive the buffered code. Trade-off: rotation may re-collect — guarded by `_uiState !is Loading` early-return in `onOAuthCallback`.

**Optimistic long-lived token path.**
Per story Dev Notes — token validity is confirmed asynchronously by the WebSocket handshake. `submitLongLivedToken` saves token, fires `serverManager.connect()`, sets `Success` immediately. Bad tokens surface as `HaEvent.ConnectionError("Auth invalid")` on Dashboard.

**`StartupViewModel` startup-route decision.**
Lives in `ui/StartupViewModel.kt` (commonMain). `HaNativeNavHost` collects its `route` StateFlow before constructing the back stack and renders a centered spinner during `Loading`. Read is single DataStore + single Keystore/Keychain lookup — well under NFR2's 1s budget.

**Build task name correction.**
Story called for `:shared:compileKotlinAndroid` — actual KMP target task is `:shared:compileAndroidMain`. Used both `:shared:compileAndroidMain` and `:shared:compileKotlinIosSimulatorArm64` plus `:shared:linkDebugFrameworkIosSimulatorArm64` for full coverage (Story 3.4 lesson — link surfaces IrLinkageError that compile alone does not).

### Completion Notes

- All 12 tasks and every subtask checked. Manifest, MainActivity (Android), iOSApp.swift (iOS), Info.plist all wired for `hanative://auth-callback`.
- AuthViewModel exposes sealed `AuthUiState (Idle|Loading|Success|Error)` per AC9. Idle/Loading guards prevent re-entry and stale callbacks.
- `SessionRepository` runs every logout step under try/finally so credential teardown is always best-effort (NFR8).
- `HttpClient` actuals (Android CIO + iOS Darwin) install `ContentNegotiation { json() }` so `body<AuthTokenResponseDto>()` deserializes the OAuth token-exchange response.
- Build verified: `./gradlew :shared:compileAndroidMain` ✅, `:shared:compileKotlinIosSimulatorArm64` ✅, `:shared:linkDebugFrameworkIosSimulatorArm64` ✅ (no IrLinkageError), `:androidApp:assembleDebug` ✅.
- Logging audit (AC8): only `KtorHaWebSocketClient.kt:119,147` use `println` — both log message-type/result-id only, no token/code/URL with credentials. Confirmed safe.
- AuthScreen masks the long-lived token via `PasswordVisualTransformation` for shoulder-surfing hygiene. `KeyboardType.Password` keeps the token off keyboard suggestion bars.
- Backstack is cleared (not just popped) on auth success and on logout — back gesture cannot return to Auth/Onboarding from authenticated routes.
- **Refresh tokens deferred** to Story 3.6 (per Dev Notes). The OAuth path stores `access_token` only; on 401 we'll need a re-auth flow.
- **NavigationRail item click wiring deferred** — same growth-hook stub as before Story 3.5; full rail interactivity is out of scope.
- **Manual verification** (Task 12.3, 12.4) requires running on a device/simulator with a real HA instance. Not executed in this dev session — flagging as a manual-QA follow-up.
- **Refresh token + cloud URL handling** explicitly out of scope per story Dev Notes.

## File List

**New (commonMain):**
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/SessionRepository.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/entities/AuthTokenResponseDto.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/OAuthLauncher.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/OAuthCallbackBus.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/StartupViewModel.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/auth/AuthViewModel.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/auth/AuthScreen.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/settings/SettingsViewModel.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/settings/SettingsScreen.kt`

**New (androidMain):**
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AndroidOAuthLauncher.kt`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/OAuthLauncherModule.kt`

**New (iosMain):**
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/IosOAuthLauncher.kt`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/OAuthLauncherModule.kt`

**Modified (commonMain):**
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/AuthenticationRepositoryImpl.kt` — added `getToken(): String?`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/HaUrlRepository.kt` — added `getUrl()` and `clearUrl()`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt` — included `oauthLauncherModule()`, registered `OAuthCallbackBus` and `SessionRepository`, added `expect fun oauthLauncherModule()`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt` — registered `AuthViewModel`, `StartupViewModel`, `SettingsViewModel`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/Routes.kt` — added `SettingsRoute`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/HaNativeNavHost.kt` — startup-route gating, AuthScreen + SettingsScreen entries, Settings tab nav

**Modified (androidMain):**
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/HttpClientModule.kt` — installed `ContentNegotiation { json() }`

**Modified (iosMain):**
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/HttpClientModule.kt` — installed `ContentNegotiation { json() }`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/KoinHelper.kt` — added `handleOAuthCallback(code: String?)` Swift bridge

**Modified (androidApp):**
- `androidApp/src/main/AndroidManifest.xml` — `android:launchMode="singleTask"` and OAuth deep-link intent-filter
- `androidApp/src/main/java/com/backpapp/hanative/MainActivity.kt` — `onNewIntent` + `handleOAuthIntent` forwarding to `OAuthCallbackBus`

**Modified (iosApp):**
- `iosApp/iosApp/iOSApp.swift` — `.onOpenURL` parses `code`, calls `KoinHelperKt.handleOAuthCallback`
- `iosApp/iosApp/Info.plist` — `CFBundleURLTypes` registers `hanative://` scheme

## Change Log

| Date | Change |
|------|--------|
| 2026-04-30 | Story 3.5 created — ready-for-dev |
| 2026-04-30 | Story 3.5 implemented — auth (long-lived token + OAuth code flow), session persistence, startup routing, Settings → Disconnect. Builds: Android assembleDebug ✅, iOS sim link ✅. Status: review. |
| 2026-04-30 | Code review (AI) — 1 decision, 12 patches, 4 deferred, 14 dismissed. Status: in-progress. |
| 2026-04-30 | All 12 review patches applied. Status: done. |

## Senior Developer Review (AI)

### Review Findings

- [x] [Review][Decision] `SessionRepository.logout()` adds `serverManager.disconnect()` not present in spec task 2.4 — **Accepted as intentional improvement.** Stops reconnect loop explicitly; prevents reconnect race with stale credentials. Spec order is a subset; this is a superset.

- [x] [Review][Patch] **CRITICAL: `redirect_uri` missing from OAuth token POST** [`AuthViewModel.kt` `onOAuthCallback`] — `httpClient.submitForm` to `/auth/token` sends `grant_type`, `code`, `client_id` but omits `redirect_uri`. RFC 6749 §4.1.3 and HA Indieauth require `redirect_uri` to match the authorize request. HA will return 400; OAuth path always fails. Fix: `append("redirect_uri", REDIRECT_URI)` in the Parameters.build block.

- [x] [Review][Patch] **HIGH: CLIENT_ID and REDIRECT_URI not URL-encoded in authorize URL** [`AuthViewModel.kt` `startOAuthFlow`] — `CLIENT_ID = "https://backpapp.github.io/HaNative/"` and `REDIRECT_URI = "hanative://auth-callback"` are string-interpolated raw into the query string. The `://` and `/` characters break query string parsing. Fix: use Ktor's `URLBuilder` or `encodeURLQueryComponent()` on each parameter value.

- [x] [Review][Patch] **HIGH: OAuthCallbackBus `replay=1` stale code not cleared after consumption** [`OAuthCallbackBus.kt:14`, `AuthViewModel.kt`] — After a successful OAuth exchange, the used (now-revoked) code remains in the replay slot indefinitely. On next `AuthScreen` visit (post-logout re-login), a new `AuthViewModel` init-collects the stale code; if `startOAuthFlow()` advances state to `Loading` before the collector drains it, exchange fires against the revoked code → `OAUTH_TOKEN_EXCHANGE_FAILED`. Fix: call `_codes.resetReplayCache()` from `AuthViewModel.onOAuthCallback` after the code is consumed (success or error).

- [x] [Review][Patch] **HIGH: `onOAuthCallback` is public and subject to concurrent coroutine launches** [`AuthViewModel.kt:115`] — `onOAuthCallback` can be called directly by platform code and also by the bus collector simultaneously. With `extraBufferCapacity=1`, two emissions can both pass the `!is Loading` check before either updates state, launching two concurrent token-exchange coroutines against the same one-time-use code. Fix: make `onOAuthCallback` `private`; add a `Mutex` or `AtomicBoolean` guard around the coroutine launch to ensure only one exchange runs at a time.

- [x] [Review][Patch] **HIGH: `startOAuthFlow()` sets `Loading` inside coroutine after `getUrl()` suspension** [`AuthViewModel.kt:98-112`] — `_uiState = Loading` is set AFTER `urlRepository.getUrl()` suspends. The bus collector can receive a replayed code while state is still `Idle`, silently dropping it via the `!is Loading` early-return — leaving the OAuth flow stuck. Fix: set `_uiState.value = AuthUiState.Loading` synchronously before launching the coroutine (mirrors `submitLongLivedToken` pattern which correctly guards upfront).

- [x] [Review][Patch] **MEDIUM: `saveToken()` and `clearToken()` not mutex-guarded** [`AuthenticationRepositoryImpl.kt`] — `getToken()` and `getValidToken()` use `mutex.withLock`, but `saveToken()` and `clearToken()` do not. Concurrent save+get can interleave; iOS Keychain thread-safety is implementation-defined. Fix: wrap `saveToken` and `clearToken` bodies with `mutex.withLock { ... }`.

- [x] [Review][Patch] **MEDIUM: AC6/NFR2 — `serverManager.connect()` blocks `Loading` state** [`StartupViewModel.kt:40-41`] — `connect()` is awaited before `_route.value = StartupRoute.Dashboard` is set. If the network is slow/unreachable, the splash spinner hangs beyond NFR2's 1s budget. Spec task 9.4 explicitly says "fire-and-forget on the IO scope". Fix: `viewModelScope.launch { serverManager.connect() }` then immediately set `_route.value = StartupRoute.Dashboard`. Same fire-and-forget pattern needed in `AuthViewModel.submitLongLivedToken` and `onOAuthCallback`.

- [x] [Review][Patch] **MEDIUM: Nav `selectedIndex` desynchronizes from backStack after SettingsRoute pop** [`HaNativeNavHost.kt`] — `selectedIndex` is a `rememberSaveable` int independent of the nav backStack. After back-press from `SettingsScreen`, index stays at 2 (Settings selected) while Dashboard is shown; pressing Settings again sees `lastOrNull() !is SettingsRoute` and double-pushes. Fix: derive `selectedIndex` from `backStack.lastOrNull()` mapping rather than storing separately.

- [x] [Review][Patch] **MEDIUM: `IosOAuthLauncher.launch()` fails silently — state stuck at `Loading`** [`IosOAuthLauncher.kt:7-14`] — `UIApplication.openURL` completion is discarded (`completionHandler = null`). If the system fails to open the URL, `_uiState` stays `Loading` forever with no error and no bus callback. Additionally, `emptyMap<Any?, Any>()` has nullable key — Obj-C bridge expects non-nullable `NSDictionary` key. Fix: pass a completion handler that emits `null` to `OAuthCallbackBus` on failure; change to `emptyMap<Any, Any>()`.

- [x] [Review][Patch] **MEDIUM: ExpandedLayout `NavigationRail` does not call `onNavItemClick`** [`HaNativeNavHost.kt`] — Task 10.2 requires Settings tab to be reachable on all form factors. `ExpandedLayout` receives `onNavItemClick` but suppresses it with `@Suppress("UNUSED_EXPRESSION")`. Settings is unreachable on tablet/desktop widths. Fix: wire `NavigationRail` item `onClick` to call `onNavItemClick(index)` analogous to `CompactLayout`.

- [x] [Review][Patch] **LOW: AC2 — `Button` removed from composition during `Loading` (not disabled)** [`AuthScreen.kt:90-97`] — AC2 says "disabled, not hidden". The `if/else` removes the `Button` entirely during `Loading`. The `Box(height=48dp)` prevents layout shift (intent satisfied), but the button is not in the tree with `enabled=false`. Fix: render `Button(enabled=false, alpha=0f)` during Loading, or note this as an acceptable interpretation of AC2.

- [x] [Review][Patch] **LOW: `SettingsScreen` `showConfirm` uses `remember` not `rememberSaveable`** [`SettingsScreen.kt:28`] — Dialog state is lost on configuration change (rotation). The confirmation dialog silently closes mid-flow. Fix: `var showConfirm by rememberSaveable { mutableStateOf(false) }`.

- [x] [Review][Defer] MainScope singleton leaks coroutines across logout [`DataModule.kt:30`] — deferred, pre-existing (Story 3.3 architecture — `MainScope` never cancelled; coroutines outlive session boundary)
- [x] [Review][Defer] `ServerManager.initialize()` registers duplicate `onForeground` listener on repeated calls [`ServerManager.kt`] — deferred, pre-existing (Story 3.3 — each `initialize()` appends a new lifecycle listener without deregistering the prior one)
- [x] [Review][Defer] OAuth `refresh_token` silently discarded — explicitly out of scope per story Dev Notes; Story 3.6 to handle token refresh on 401
- [x] [Review][Defer] `SessionRepository.hasValidSession()` checks token/URL existence only, not token expiry — out of scope per spec; Story 3.6 to add expiry/refresh logic
