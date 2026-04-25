---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories', 'step-04-final-validation']
inputDocuments:
  - '_bmad/outputs/prd.md'
  - '_bmad/outputs/architecture.md'
  - '_bmad/outputs/ux-design-specification.md'
---

# HaNative - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for HaNative, decomposing the requirements from the PRD, UX Design, and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

**HA Connection & Authentication**
- FR1: User can connect the app to their HA instance by providing its URL
- FR2: User can authenticate with their HA instance using a long-lived access token or OAuth
- FR3: App maintains an authenticated session across relaunches without requiring re-login
- FR4: User can disconnect from a HA instance and connect to a different one

**Entity State & Control**
- FR5: User can view real-time state of HA entities within all supported domains
- FR6: User can control HA entities within supported domains (toggle, set value, trigger)
- FR7: App reflects entity state change immediately following a user control action
- FR8: App reflects entity state changes triggered by HA automations or other clients
- FR9: Sensor and binary_sensor entities display read-only state without control affordances
- FR10: App supports entity domains: `light`, `switch`, `climate`, `cover`, `media_player`, `sensor`, `binary_sensor`, `input_boolean`, `input_select`, `script`, `scene`
- FR11: App displays non-core entity types with last-known state in read-only mode without crashing

**Dashboard Management**
- FR12: User can create a named dashboard
- FR13: User can rename a dashboard
- FR14: User can delete a dashboard
- FR15: User can switch between named dashboards with a single interaction
- FR16: App persists all dashboard configurations across sessions and relaunches

**Card Configuration & Entity Picker**
- FR17: User can add an entity card to a dashboard via an entity picker
- FR18: Entity picker presents entities filtered to supported domains, sorted by recent HA activity
- FR19: User can remove an entity card from a dashboard
- FR20: User can reorder entity cards within a dashboard
- FR21: User can configure entity-specific card properties where applicable (e.g., light brightness range, climate temperature limits)

**Theme & Physical Design Language**
- FR22: App applies one curated visual theme consistently across all screens and surfaces
- FR23: App provides haptic feedback per entity domain on control interactions
- FR24: App provides micro-animations on entity state transitions as defined by the theme contract
- FR24b: App provides animated transitions between dashboards as defined by the theme contract
- FR25: Theme visual, haptic, and animation behavior is authored as part of the component contract — not configurable by user in V1

**Home Screen Widget**
- FR26: User can add a home screen widget that displays entity state from their dashboards
- FR27: Widget displays last known entity state when HA is unreachable
- FR28: Widget reflects current entity state when HA connectivity is available

**Connectivity & Resilience**
- FR29: App connects to HA via local LAN WebSocket as the primary connection method
- FR30: App falls back to Nabu Casa cloud relay when local WebSocket connection is unavailable
- FR31: App displays a staleness indicator with last-updated timestamp when entity state cannot be refreshed
- FR32: App automatically reconnects to the HA WebSocket on connection restore without user action
- FR33: App displays last-known entity states during disconnection — no crash, no empty screen, no error state
- FR34: App handles HA API version mismatches gracefully without crashing

**Privacy & Data**
- FR35: App stores HA credentials (URL and auth token) locally on-device only — no external sync
- FR36: App does not transmit entity names, entity state, or home topology to any external server beyond the user-configured Nabu Casa relay
- FR37: App does not request location, camera, microphone, or push notification permissions

**Context Engine *(Growth)***
- FR38: User can define a named context using entity-state conditions with an HA-pattern rule builder (AND/OR logic, entity picker, condition blocks)
- FR39: App evaluates active context rules against live entity state and transitions the active dashboard automatically when a matching context is detected
- FR40: When multiple contexts match simultaneously, the first-created rule takes priority
- FR41: User can select from a gallery of pre-built context rule templates
- FR42: App exposes `active_context` and `current_dashboard` as controllable entities within HA, allowing HA automations to trigger context switches

**Kiosk Mode *(Growth)***
- FR43: User can enable kiosk mode to display the app fullscreen with OS chrome hidden
- FR44: User can lock the active dashboard in kiosk mode to prevent accidental switching
- FR45: App dims the display after a configurable idle period in kiosk mode and wakes on any tap

**Onboarding *(Growth)***
- FR46: App presents a demo experience with simulated entity data and theme preview before HA connection is required
- FR47: App auto-generates a starter dashboard populated with the user's most-recently-active entities immediately after HA connection

### NonFunctional Requirements

**Performance**
- NFR1: Entity control action to visible state confirmation ≤500ms on local LAN WebSocket
- NFR2: App launch to first dashboard visible (warm start / session resume) ≤1 second
- NFR3: Dashboard switch (named dashboard, one-tap) ≤200ms
- NFR4: Entity picker open and populated ≤500ms for up to 500 entities
- NFR5: UI animations run at 60fps on supported hardware — dropped frames are a theme contract failure

**Security**
- NFR6: HA auth token stored in platform secure storage — Android Keystore on Android, iOS Keychain on iOS; not in SharedPreferences, UserDefaults, or unencrypted local storage
- NFR7: All Nabu Casa cloud relay traffic over HTTPS/TLS — no unencrypted fallback
- NFR8: Auth token and HA instance URL must not appear in crash reports, analytics payloads, or log output

**Reliability**
- NFR9: WebSocket reconnection attempted within 5 seconds of connection loss detection
- NFR10: App must not crash on receipt of unknown entity types, malformed HA API responses, or unexpected WebSocket message formats
- NFR11: Dashboard configuration persists locally — connection loss or app restart must not clear or corrupt dashboard data
- NFR12: Stale state display is preferable to an error screen in all disconnection scenarios
- NFR13: App startup must succeed even when HA is unreachable — cached dashboard layout renders, entity state shows as stale

### Additional Requirements

Architecture-derived technical requirements affecting implementation:

- AR1: Project scaffold — KMP Wizard (kmp.jetbrains.com), three-module structure: `:shared`, `:androidApp`, `:iosApp`; port `applicationId = "com.backpapp.hanative"`, `rootProject.name = "HaNative"`
- AR2: Port HA open-source components (KMP-idiomatic, not verbatim copy): `Entity.kt`, `WebSocketCoreImpl` → Ktor, `AuthenticationRepositoryImpl` → Ktor + Mutex dedup, `MapAnySerializer` (verbatim — pure kotlinx.serialization), `entities/` WS message types (verbatim), `ServerManager` → Ktor + `AppLifecycleObserver`
- AR3: Commit initial `ha-upstream-refs/` snapshots at project init; add `NOTICE` file (Apache 2.0 attribution)
- AR4: GitHub Actions weekly diff watcher (`ha-upstream-watch.yml`) monitoring 8 files across HA Android + iOS repos; opens issue with diff on change; label `ha-upstream-change`
- AR5: SQLDelight 2.x `kotlinx.datetime` adapter must be explicitly wired in `EntityDomainAdapter.kt` — first data-layer story must call this out
- AR6: Clean Architecture import rules enforced — `domain/` must have zero Ktor/SQLDelight/Android SDK imports; enforced via Koin module boundaries
- AR7: Four `expect/actual` platform interfaces required: `CredentialStore`, `HapticFeedback`, `ServerDiscovery` (mDNS), `AppLifecycleObserver` (foreground reconnect)
- AR8: Compose Navigation 3 (JetBrains) — type-safe routes via `@Serializable` data classes; single `NavHost` in `:shared`
- AR9: Koin DI — three modules: `dataModule`, `domainModule`, `presentationModule`; loaded in platform entry points
- AR10: V1 internal theme must implement full component contract (all entity domains × all size buckets × all state variants) as V2 SDK reference implementation before any screen is built
- AR11: Widget surfaces are platform-specific — Glance in `:androidApp`, WidgetKit in `:iosApp`; NOT in `:shared`; CMP does not cover widget surfaces
- AR12: iOS `Info.plist` requires `NSLocalNetworkUsageDescription` + `NSBonjourServices` (`_home-assistant._tcp`, `_hass-mobile-app._tcp`) for LAN WebSocket — mandatory before iOS build
- AR13: `ContextRule.sq` schema must be designed before Growth context engine story is assigned (currently undefined — Growth blocker)

### UX Design Requirements

Component and interaction requirements from UX Design Specification:

- UX-DR1: Complete M3 custom token set (ColorScheme, Typography, Shapes) authored before any screen is built — tokens are the V2 SDK contract surface. Colors: `#0D1117` background, `#161B22` surface, `#1C2333` surface-elevated, `#F59E0B` accent; Typeface: Inter; base unit 8dp; card radius 18dp
- UX-DR2: `EntityCard` component — row-based layout (D4/Minimal-Ambient direction), 2 sizes (standard, compact), 5 states (default, active, stale, optimistic, error), 5 variants (toggleable, read-only, stepper, trigger, media); anatomy: `[domain-icon] [entity-name + state-label] [right-action]`; stale state = 50% opacity + inline timestamp
- UX-DR3: `EntityPicker` bottom sheet — 4 states (loading, loaded, empty, search-active); domain filter chips at top; activity-sorted rows; same visual language as `EntityCard`; skeleton shimmer on load (not spinner)
- UX-DR4: `EmptyDashboardState` component — designed invitation (not blank/error); single CTA "Add your first card" with `+` affordance; secondary hint "Your most-used entities appear first"; tap anywhere in prompt area opens `EntityPicker`
- UX-DR5: `StaleStateIndicator` inline header component — 3 states: connected (hidden, zero footprint), stale (amber, live counter "Last updated Xs ago" updating every second), reconnecting (spinner + "Reconnecting…"); replaces connection pill in same position with no layout shift
- UX-DR6: `DashboardSwitcher` — bottom nav active tab shows current dashboard name (not fixed "Dash" label); tapping active tab opens bottom sheet with all named dashboards; Growth variant adds context badge pill
- UX-DR7: `HapticEngine` `expect/actual` — 7 `HapticPattern` variants: `ToggleOn`, `ToggleOff`, `StepperInc`, `StepperDec`, `ActionTriggered`, `ActionRejected`, `DashboardSwitch`; Android actual: `VibrationEffect` per-pattern waveform + amplitude; iOS actual: `UIImpactFeedbackGenerator`/`UINotificationFeedbackGenerator` per pattern type; theme contract calls interface — never platform API directly
- UX-DR8: `Motion.kt` spring constants — dashboard cross-fade `tween(200ms)`; bottom sheet spring `stiffness=400, dampingRatio=0.8`; card press compress spring `stiffness=600, dampingRatio=0.7`; entity state change `tween(200ms, EaseInOut)`; snap-back rejection spring `stiffness=500, dampingRatio=0.6`; staleness indicator fade `tween(300ms)`. Every animation has semantic justification.
- UX-DR9: Accessibility (WCAG 2.1 AA) — `contentDescription` on all `EntityCard` variants ("Living Room light, on at 80%"); `Role.Switch` + `stateDescription` on toggles; `StaleStateIndicator` TalkBack/VoiceOver announcement; `EmptyDashboardState` reads as button; `Modifier.minimumInteractiveComponentSize()` on all interactive elements (48dp floor); 2dp amber focus indicator; dynamic text support to 1.3× `fontScale`
- UX-DR10: `WindowSizeClass` integration — queried in `MainActivity`, passed via `CompositionLocal`; COMPACT (< 600dp) = phone single column + bottom nav; EXPANDED (> 840dp) = tablet Growth layout
- UX-DR11: Onboarding form patterns — HA URL entry + auth token forms; inline validation on blur (not keystroke); plain-language error messages; `CircularProgressIndicator` replaces submit button during async ops (not hidden); `ImeAction.Go` on last field triggers submit; success = silent navigation forward (no success screen)
- UX-DR12: Navigation patterns — bottom nav persists across all screens (Dash / Rooms / Settings); all contextual flows (entity picker, dashboard switcher, card config) use bottom sheets, never full-screen push; dashboard switching is tap active nav item → sheet → one-tap selection → ≤200ms cross-fade; no nested nav stacks; modals only for destructive confirmations (delete dashboard)

### FR Coverage Map

| FR | Epic | Description |
|---|---|---|
| FR1–4 | Epic 3 | HA connection + auth |
| FR5–11 | Epic 4 | Entity state subscription + control + 10 domains |
| FR12–16 | Epic 4 | Dashboard CRUD + persistence |
| FR17–21 | Epic 4 | Card add/remove/reorder/configure + entity picker |
| FR22–25, FR24b | Epic 2 | Theme + haptics + animations |
| FR26–28 | Epic 5 | Home screen widget |
| FR29–30 | Epic 3 | LAN WebSocket + Nabu Casa fallback |
| FR31–33 | Epic 4 | Stale state indicator + auto-reconnect + graceful degradation |
| FR34–37 | Epic 3 | API mismatch handling + privacy |
| FR38–42 | Epic 6 | Context engine (Growth) |
| FR43–45 | Epic 6 | Kiosk mode (Growth) |
| FR46–47 | Epic 6 | Demo onboarding (Growth) |

## Epic List

### Epic 1: Project Foundation & CI
Users get a compilable, runnable three-module KMP project with full architecture skeleton, HA source components ported, CI/CD pipelines, and upstream diff watcher — every subsequent epic is buildable from this base.
**Covers:** AR1, AR2, AR3, AR4, AR6, AR8, AR9, AR12

### Epic 2: Theme & Design System
Users get the app's distinctive visual identity and physical feel — custom color tokens, Inter typography, spring motion constants, and per-entity haptic patterns. V1 internal theme is the V2 SDK reference implementation; must be complete before any card is built.
**Covers:** FR22, FR23, FR24, FR24b, FR25, AR7 (HapticFeedback), AR10, UX-DR1, UX-DR7, UX-DR8, UX-DR10

### Epic 3: HA Connection & Authentication
Users can connect to their Home Assistant instance, authenticate, and maintain a persistent session across relaunches. App handles LAN-first connection, Nabu Casa fallback, and credential security.
**Covers:** FR1, FR2, FR3, FR4, FR29, FR30, FR34, FR35, FR36, FR37, AR7 (CredentialStore, ServerDiscovery, AppLifecycleObserver), UX-DR11, UX-DR12 (partial)

### Epic 4: Dashboard & Entity Control
Users can build named dashboards, add entity cards for all 10 domains, control entities with immediate feedback, and trust the app during disconnection — the complete MVP core loop delivering "first dashboard in ≤10 minutes."
**Covers:** FR5–21, FR31, FR32, FR33, AR5, UX-DR2, UX-DR3, UX-DR4, UX-DR5, UX-DR6, UX-DR9, UX-DR12 (navigation)

### Epic 5: Home Screen Widget
Android users can add a home screen widget showing glanceable entity state from their dashboards — live when connected, last-known when HA is unreachable.
**Covers:** FR26, FR27, FR28, AR11

### Epic 6: Growth — Context Engine, Kiosk & Demo *(Phase 2)*
Users get automatic dashboard switching based on live home state, wall tablet kiosk mode, and a demo experience before connecting to HA.
**Covers:** FR38–47, AR13

---

## Epic 1: Project Foundation & CI

Compilable, runnable three-module KMP + CMP project with full Clean Architecture skeleton, HA source components ported, CI/CD pipelines, and HA upstream diff watcher. Every subsequent epic builds on this base.

### ✅ Story 1.1: KMP Wizard Project Scaffold

As a developer,
I want a correctly structured three-module KMP + CMP project,
So that all subsequent epics build on a valid scaffold with the right Gradle configuration from day one.

**Acceptance Criteria:**

**Given** a fresh project directory
**When** the KMP Wizard scaffold is generated (kmp.jetbrains.com — Android + iOS, "Share UI" enabled) and `applicationId`/`rootProject.name` are ported
**Then** the project has three modules: `:shared`, `:androidApp`, `:iosApp`
**And** `applicationId = "com.backpapp.hanative"` and `rootProject.name = "HaNative"` are set
**And** generated stub code (Hello World screens) is replaced with empty placeholder composables
**And** `libs.versions.toml` version catalog is created with all V1 dependency versions (Kotlin 2.3.20, CMP 1.10.x, AGP 8.8+, Ktor 3.x, SQLDelight 2.x, Koin 4.x, Navigation 3)
**And** the project builds successfully on Android (debug APK) and iOS (debug scheme) with no errors

### Story 1.2: Clean Architecture Package Structure & DI

As a developer,
I want the full Clean Architecture package structure and Koin DI wired up,
So that every subsequent story drops code into the correct layer with zero import rule violations.

**Acceptance Criteria:**

**Given** the Story 1.1 scaffold
**When** package structure is established and Koin is configured
**Then** `commonMain` contains: `domain/model/`, `domain/repository/`, `domain/usecase/`, `data/remote/dto/`, `data/local/adapter/`, `data/repository/`, `di/`, `ui/components/`, `ui/navigation/`, `platform/`
**And** `DataModule`, `DomainModule`, `PresentationModule` are created (empty, no bindings yet) and loaded in `HaNativeApplication.kt` (Android) and `iOSApp.swift` (iOS)
**And** all core dependencies are declared in `libs.versions.toml` and compile clean: Ktor (CIO + Darwin engines), SQLDelight 2.x, DataStore KMP, `kotlinx.serialization`, `kotlinx.datetime`, `kotlinx.coroutines`, `lifecycle-viewmodel` (KMP)
**And** a lint rule or comment block documents the import enforcement: no Ktor/SQLDelight/Android SDK imports in `domain/`
**And** project builds clean on both platforms with full dependency set resolved

### Story 1.3: Port HA Source Components

As a developer,
I want the HA WebSocket protocol, entity model, and auth components ported to KMP-idiomatic Kotlin,
So that the app starts with a production-grade HA integration layer rather than building from scratch.

**Acceptance Criteria:**

**Given** the Story 1.2 package structure
**When** HA Android + iOS source components are ported
**Then** `HaEntity.kt` is a sealed class with 10 domain subtypes (`Light`, `Switch`, `Climate`, `Cover`, `MediaPlayer`, `Sensor`, `BinarySensor`, `InputBoolean`, `InputSelect`, `Script`, `Scene`) plus `Unknown` — zero platform imports, all fields `val`, `kotlinx.datetime.Instant` for timestamps
**And** `data/remote/entities/` contains ported WS message types (verbatim from HA Android, pure Kotlin, zero platform deps)
**And** `MapAnySerializer.kt` is ported verbatim (pure `kotlinx.serialization`)
**And** `HaWebSocketClient.kt` (interface in domain) + `KtorHaWebSocketClient.kt` (Ktor `DefaultClientWebSocketSession` impl) are created
**And** `AuthenticationRepositoryImpl.kt` uses Ktor `HttpClient` with `Mutex` + `Deferred` token refresh deduplication (ported from iOS `TokenManager` pattern)
**And** `ServerManager.kt` uses Ktor + `AppLifecycleObserver` integration (no OkHttp)
**And** `ha-upstream-refs/android/` and `ha-upstream-refs/ios/` contain initial snapshots of all 8 watched files committed to the repo
**And** `NOTICE` file is added at project root with Apache 2.0 attribution for ported HA source
**And** all ported files compile clean with no platform-specific imports in `commonMain`

### Story 1.4: Compose Navigation 3 & Platform Entry Points

As a developer,
I want type-safe navigation routes and platform entry points wired up,
So that all subsequent UI stories can add screens to a working nav graph without structural changes.

**Acceptance Criteria:**

**Given** Stories 1.1–1.3 complete
**When** navigation and platform host files are created
**Then** `Routes.kt` contains `@Serializable` data classes: `OnboardingRoute`, `DashboardRoute`
**And** `HaNativeNavHost.kt` defines a `NavHost` with empty placeholder composables for all route destinations
**And** `MainActivity.kt` hosts the Compose `NavHost` and passes `WindowSizeClass` via `CompositionLocal`
**And** `HaNativeApplication.kt` calls `startKoin` with all three Koin modules
**And** `iOSApp.swift` initialises Koin and wires scene lifecycle
**And** `ContentView.swift` hosts `ComposeUIViewController`
**And** `iosApp/Info.plist` contains `NSLocalNetworkUsageDescription` ("Connect directly to your Home Assistant instance on your local network.") and `NSBonjourServices` (`_home-assistant._tcp`, `_hass-mobile-app._tcp`)
**And** app launches to a blank placeholder screen on both Android and iOS with no crash

### Story 1.5: GitHub Actions CI & HA Upstream Diff Watcher

As a developer,
I want automated build CI and a weekly HA upstream diff watcher,
So that broken builds are caught immediately and HA API breaking changes surface as GitHub issues without manual monitoring.

**Acceptance Criteria:**

**Given** the project is on GitHub
**When** CI workflows are added to `.github/workflows/`
**Then** `android-ci.yml` runs on every push and PR to `main`, executes `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest`, and fails the build on test failure
**And** `ios-ci.yml` runs on every push and PR to `main` and builds the iOS scheme via xcodebuild
**And** `ha-upstream-watch.yml` runs on a weekly cron (Sunday 00:00 UTC), fetches all 8 watched files from the HA Android + iOS repos, diffs each against its `ha-upstream-refs/` snapshot, and opens a GitHub issue with the diff content and label `ha-upstream-change` if any file has changed
**And** all three workflows are valid YAML and pass GitHub Actions syntax validation
**And** `android-ci.yml` and `ios-ci.yml` pass on the current project state (empty placeholder app)

---

## Epic 2: Theme & Design System

App's distinctive visual identity and physical feel — custom color tokens, Inter typography, spring motion constants, and per-entity haptic patterns. V1 internal theme is the V2 SDK reference implementation; complete before any card is built.

### Story 2.1: M3 Custom Token Set (Color, Typography, Shape)

As a developer,
I want the complete M3 custom token set defined before any screen is built,
So that every composable consumes design tokens and the V2 SDK contract surface is established from day one.

**Acceptance Criteria:**

**Given** the Epic 1 scaffold
**When** the design system tokens are authored
**Then** `Color.kt` defines all 10 color roles: `background` `#0D1117`, `surface` `#161B22`, `surfaceElevated` `#1C2333`, `surfaceActive` `#2D1E06`, `accent` `#F59E0B`, `textPrimary` `#E6EDF3`, `textSecondary` `rgba(230,237,243,0.5)`, `connected` `#3FB950`, `border` `#21262D`, `toggleOff` `#30363D`
**And** `Typography.kt` defines all 6 type roles (dashboard name 20sp/800, card value 13sp/800, card label 11sp/500, status/metadata 10sp/600, nav label 9sp/700, temp/large value 20sp/800) using Inter typeface with negative letter-spacing (`-0.02em`) on dashboard name
**And** `Shape.kt` defines card corner radius `18dp` and all other shape tokens
**And** `HaNativeTheme.kt` wraps `MaterialTheme` with the custom `ColorScheme`, `Typography`, and `Shapes` — no M3 component uses its visual default
**And** a `@Preview` composable demonstrates all color roles rendered correctly
**And** amber `#F59E0B` on `#0D1117` contrast is ≥ 6.5:1 (WCAG AA confirmed); primary text on surface ≥ 11:1

### Story 2.2: Motion Constants

As a developer,
I want all spring physics and animation constants defined in a single `Motion.kt` file,
So that every animation in the app is authored from theme contract values, not per-screen magic numbers.

**Acceptance Criteria:**

**Given** Story 2.1 complete
**When** `Motion.kt` is created in `ui/theme/`
**Then** it defines all 6 named animation specs: `dashboardTransition` (`tween(200ms, EaseInOut)` cross-fade), `bottomSheetOpen`/`bottomSheetDismiss` (spring `stiffness=400, dampingRatio=0.8`), `cardPress` (spring `stiffness=600, dampingRatio=0.7`), `entityStateChange` (`tween(200ms, EaseInOut)`), `snapBackRejection` (spring `stiffness=500, dampingRatio=0.6`), `staleIndicatorFade` (`tween(300ms)`)
**And** each constant has a one-line comment stating its semantic justification
**And** no composable outside `ui/theme/` hardcodes animation durations, stiffness, or damping values — all reference `Motion.*`

### Story 2.3: HapticEngine expect/actual

As a developer,
I want a platform-bridged `HapticEngine` with 7 semantic patterns,
So that every entity control interaction fires entity-typed haptic feedback through the theme contract without any composable touching platform APIs directly.

**Acceptance Criteria:**

**Given** Story 2.1 complete
**When** `HapticEngine` expect/actual is implemented
**Then** `platform/HapticFeedback.kt` (`commonMain`) declares `expect interface HapticEngine` with `fun fire(pattern: HapticPattern)`
**And** `HapticPattern` is a sealed class with 7 variants: `ToggleOn`, `ToggleOff`, `StepperInc`, `StepperDec`, `ActionTriggered`, `ActionRejected`, `DashboardSwitch`
**And** `AndroidHapticFeedback.kt` (`androidMain` actual) implements each pattern using `VibrationEffect` with distinct waveform + amplitude per pattern
**And** `IosHapticFeedback.kt` (`iosMain` actual) implements via `UIImpactFeedbackGenerator` / `UINotificationFeedbackGenerator` per pattern type
**And** `HapticEngine` is registered in `DataModule` (Koin) and exposed via `LocalHapticEngine` composition local — composables call `LocalHapticEngine.current.fire(pattern)`, never platform API directly

### Story 2.4: WindowSizeClass Integration

As a developer,
I want `WindowSizeClass` queried at the host level and passed via `CompositionLocal`,
So that every composable adapts to phone vs. tablet layout without querying the window directly.

**Acceptance Criteria:**

**Given** Story 2.1 complete
**When** `WindowSizeClass` integration is wired
**Then** `MainActivity.kt` computes `WindowSizeClass` via `androidx.compose.material3.windowsizeclass` and provides it via `LocalWindowSizeClass` composition local
**And** `COMPACT` (< 600dp) routes to single-column phone layout with bottom nav; `MEDIUM` falls back to `COMPACT`; `EXPANDED` (> 840dp) has a navigation rail placeholder (Growth hook, not implemented V1)
**And** all text uses `sp` units; all layout uses `dp` — no text locked to `dp`
**And** a `@Preview` at `COMPACT` and `EXPANDED` sizes confirms the `CompositionLocal` resolves correctly

---

## Epic 3: HA Connection & Authentication

Users can connect to their Home Assistant instance, authenticate, and maintain a persistent session across relaunches. App handles LAN-first WebSocket connection, Nabu Casa cloud fallback, and secure credential storage.

### Story 3.1: CredentialStore & DataStore expect/actual

As a developer,
I want secure credential storage and lightweight settings persistence wired up,
So that auth tokens never touch unencrypted storage and onboarding screens can persist and retrieve connection config.

**Acceptance Criteria:**

**Given** Epics 1 and 2 complete
**When** `CredentialStore` expect/actual and DataStore are implemented
**Then** `platform/CredentialStore.kt` (`commonMain`) declares `expect interface CredentialStore` with `suspend fun saveToken(token: String)`, `suspend fun getToken(): String?`, `suspend fun clear()`
**And** `AndroidCredentialStore.kt` (`androidMain`) stores the token in `EncryptedSharedPreferences` (Android Keystore-backed) — not in plain `SharedPreferences`
**And** `IosCredentialStore.kt` (`iosMain`) stores the token in `Security.framework` Keychain via `SecItemAdd`/`SecItemCopyMatching`
**And** DataStore (KMP) is configured separately for HA instance URL and user settings — URL is not a secret and must not be stored in `CredentialStore`
**And** auth token and HA URL do not appear in any log output, crash report, or analytics payload — confirmed by code review
**And** `CredentialStore` is registered in `DataModule` (Koin)

### Story 3.2: ServerDiscovery & AppLifecycleObserver expect/actual

As a developer,
I want mDNS server discovery and foreground reconnect lifecycle hooks wired up,
So that onboarding can offer auto-discovered HA instances and the app reconnects WebSocket on every foreground event.

**Acceptance Criteria:**

**Given** Story 3.1 complete
**When** `ServerDiscovery` and `AppLifecycleObserver` expect/actual are implemented
**Then** `platform/ServerDiscovery.kt` (`commonMain`) declares `expect interface ServerDiscovery` with `fun startDiscovery(): Flow<List<HaServerInfo>>` and `fun stopDiscovery()`
**And** `AndroidServerDiscovery.kt` (`androidMain`) uses `NsdManager` to browse `_home-assistant._tcp`, emitting discovered instances as `HaServerInfo(name, host, port)`
**And** `IosServerDiscovery.kt` (`iosMain`) uses `NSNetServiceBrowser` to browse the same service type
**And** `platform/AppLifecycleObserver.kt` (`commonMain`) declares `expect class AppLifecycleObserver` with `fun onForeground(callback: () -> Unit)`
**And** `AndroidAppLifecycleObserver.kt` uses `ProcessLifecycleOwner` ON_START; `IosAppLifecycleObserver.kt` uses `didBecomeActiveNotification`
**And** both are registered in `DataModule` (Koin)

### Story 3.3: HA WebSocket Connection & Session Persistence

As a developer,
I want the WebSocket client connected with LAN-first logic, Nabu Casa fallback, and automatic foreground reconnect,
So that the app always uses the fastest available connection and restores it without user action.

**Acceptance Criteria:**

**Given** Stories 3.1–3.2 complete and a stored HA URL + token
**When** the app initialises the WebSocket connection
**Then** `KtorHaWebSocketClient` attempts local LAN WebSocket first; falls back to Nabu Casa cloud relay only if LAN attempt fails
**And** `HaReconnectManager` implements exponential backoff (1s → 2s → 4s → max 30s); first reconnect fires within 5 seconds of connection loss detected (NFR9)
**And** `AppLifecycleObserver.onForeground` triggers an immediate reconnect attempt on every foreground event
**And** `ServerManager` exposes connection state as `StateFlow<ConnectionState>` (Connected, Reconnecting, Disconnected)
**And** Nabu Casa relay uses HTTPS/TLS — no unencrypted fallback (NFR7)
**And** unknown HA message types are absorbed without crash — logged at debug level, not thrown (FR34)
**And** app startup with a stored token does NOT block on WebSocket connection — UI renders from SQLDelight cache immediately (NFR2, NFR13)

### Story 3.4: Onboarding — HA URL Entry & Connection Test

As a power user,
I want to enter my HA instance URL and immediately verify connectivity,
So that I know the connection works before being asked to authenticate.

**Acceptance Criteria:**

**Given** the app has no stored HA URL
**When** the onboarding screen is shown
**Then** `OnboardingScreen` presents a single `TextField` for HA instance URL with `ImeAction.Go` triggering the connection test — no separate submit tap required
**And** `ServerDiscovery` results populate a list of auto-discovered HA instances below the field — tapping one fills the URL
**And** during connection test, `CircularProgressIndicator` replaces the submit button (disabled, not hidden)
**And** on connection failure, an inline plain-language error appears below the field ("Can't reach this address — check the URL and try again") — no modal, no toast
**And** on connection success, the screen advances silently to auth — no "Success!" screen
**And** `OnboardingViewModel` exposes `OnboardingUiState` sealed class with `Loading`, `Success(url)`, `Error(message)` — no raw `isLoading: Boolean`

### Story 3.5: Onboarding — Authentication & Session Persistence

As a power user,
I want to authenticate with my HA instance and never have to re-enter credentials,
So that my session persists across relaunches and I open directly to my dashboard every time.

**Acceptance Criteria:**

**Given** HA URL verified in Story 3.4
**When** the auth screen is shown
**Then** the screen offers two paths: long-lived access token input (TextField) and OAuth2 device authorization (button opens system browser)
**And** on long-lived token: `ImeAction.Go` submits; `CircularProgressIndicator` replaces button during validation; inline error on invalid token; silent navigation to dashboard on success
**And** on OAuth2: system browser opens HA auth page; on callback token is captured and stored without user re-entry
**And** on successful auth, token is stored in `CredentialStore` (Keystore/Keychain — NFR6); HA URL stored in DataStore
**And** on subsequent app launches with stored token, onboarding is skipped — `NavHost` routes directly to `DashboardRoute` (FR3)
**And** Settings → Disconnect clears `CredentialStore` and DataStore and returns to onboarding (FR4)
**And** token does not appear in logs, crash reports, or analytics at any point (NFR8)

---

## Epic 4: Dashboard & Entity Control

Complete MVP core loop — named dashboards, entity cards for all 10 domains, immediate haptic control feedback, and graceful offline handling. Delivers "first dashboard in ≤10 minutes."

### Story 4.1: SQLDelight Schema & Entity State Pipeline

As a developer,
I want the entity state data pipeline from WebSocket to StateFlow — with offline cache — fully operational,
So that every card composable gets live entity state with zero per-screen network calls.

**Acceptance Criteria:**

**Given** Epics 1–3 complete
**When** SQLDelight schemas and the entity state pipeline are implemented
**Then** `EntityState.sq` defines the `entity_state` table (`entity_id TEXT PRIMARY KEY`, `domain TEXT`, `state TEXT`, `attributes TEXT`, `last_updated INTEGER` mapped to `kotlinx.datetime.Instant`)
**And** `EntityDomainAdapter.kt` wires the `kotlinx.datetime` adapter explicitly in `HaNativeDatabase` — AR5 satisfied
**And** `EntityRepositoryImpl` collects `Flow<HaEvent>` from `KtorHaWebSocketClient`, maps to domain `HaEntity`, writes to SQLDelight, and emits `StateFlow<List<HaEntity>>`
**And** `ObserveEntityStateUseCase` returns `Flow<HaEntity>` for a single entity ID — card composables subscribe to their own slice only
**And** `CallServiceUseCase` sends a `call_service` WebSocket command and returns `Result<Unit>`
**And** on cold launch, `EntityRepositoryImpl` reads SQLDelight cache first and emits `UiState.Success(entities, isStale = true)` before WebSocket connects — dashboard visible in ≤1s (NFR2)
**And** on WebSocket reconnect, live events replace stale cache and `isStale` flips to `false`
**And** `EntityRepository` interface lives in `domain/`; `EntityRepositoryImpl` in `data/repository/` — no Ktor or SQLDelight imports above data layer

### Story 4.2: Dashboard Persistence Layer

As a developer,
I want dashboard and card configuration persisted in SQLDelight and exposed via repository and use cases,
So that dashboard CRUD operations in subsequent stories have a complete data layer to call.

**Acceptance Criteria:**

**Given** Story 4.1 complete
**When** dashboard persistence is implemented
**Then** `Dashboard.sq` defines `dashboard` table (`id TEXT PRIMARY KEY`, `name TEXT`, `position INTEGER`, `created_at INTEGER`)
**And** `DashboardCard.sq` defines `dashboard_card` table (`id TEXT PRIMARY KEY`, `dashboard_id TEXT` FK, `entity_id TEXT`, `position INTEGER`, `config TEXT`)
**And** `DashboardRepositoryImpl` implements create, read, update, delete, and reorder operations via SQLDelight generated queries
**And** `GetDashboardsUseCase` returns `Flow<List<Dashboard>>` with their cards — updates whenever dashboard or card data changes
**And** `SaveDashboardUseCase`, `DeleteDashboardUseCase`, `AddCardUseCase`, `RemoveCardUseCase`, `ReorderCardsUseCase` are implemented as single-responsibility use cases
**And** dashboard config survives process death — verified by: write dashboard, kill app, relaunch, dashboard present (NFR11)
**And** all use cases registered in `DomainModule`; `DashboardRepositoryImpl` registered in `DataModule`

### Story 4.3: Core EntityCard — Toggleable & Read-Only Variants

As a power user,
I want to tap entity cards and have my home respond with haptic confirmation before I lift my thumb,
So that every control interaction builds trust through speed and physical feedback.

**Acceptance Criteria:**

**Given** Stories 4.1–4.2 and Epic 2 theme complete
**When** `EntityCard` is implemented for toggleable and read-only variants
**Then** `EntityCard` is a row-based composable in `ui/components/` with anatomy `[domain-icon] [entity-name + state-label] [right-action]`, available in standard and compact sizes
**And** **toggleable variant** (light, switch, input_boolean): full card row is tappable; tap fires `HapticPattern.ToggleOn` or `ToggleOff` on touch-down (not release); `CallServiceUseCase` executes; card immediately renders optimistic new state (amber `surfaceActive` background); on WebSocket confirm state holds; on rejection card snaps back via `Motion.snapBackRejection` + `HapticPattern.ActionRejected`
**And** **read-only variant** (sensor, binary_sensor): displays state value and unit; no toggle widget; no tap action registered
**And** stale state: card dims to 50% opacity + inline staleness timestamp (not a separate overlay)
**And** `EntityCard` subscribes only to its own entity's `StateFlow` slice via `ObserveEntityStateUseCase`; `key(card.entityId)` on card lists — no full dashboard recomposition on single entity update (NFR5)
**And** `Modifier.semantics { contentDescription = "Living Room light, on at 80%"; role = Role.Switch; stateDescription = "on" }` on toggleable; read-only has contentDescription only (UX-DR9)
**And** `Modifier.minimumInteractiveComponentSize()` enforces 48dp touch floor on all interactive elements
**And** `@Preview` covers all 5 states (default, active, stale, optimistic, error) for both variants

### Story 4.4: EntityCard — Stepper, Trigger, Media & Unknown Variants

As a power user,
I want climate, script, scene, and media player cards to feel as physical and immediate as light cards,
So that every entity domain in my home is controllable with the same level of trust.

**Acceptance Criteria:**

**Given** Story 4.3 complete
**When** remaining `EntityCard` variants are implemented
**Then** **stepper variant** (climate): temperature at 20sp/800 weight; `−` and `+` are 48dp targets each; tap fires `HapticPattern.StepperInc`/`StepperDec`; `CallServiceUseCase` sends `climate.set_temperature`; optimistic state shows new temp immediately
**And** **trigger variant** (script, scene): tap fires `HapticPattern.ActionTriggered`; brief visual confirmation pulse via `Motion.entityStateChange`; no persistent state change displayed (fire-and-forget)
**And** **media variant** (media_player): displays current media title + play/pause state; play/pause tap fires `HapticPattern.ActionTriggered`; `CallServiceUseCase` sends `media_player.media_play_pause`; state updates via WebSocket subscription
**And** **unknown variant** (FR11): displays entity ID + last-known state string in read-only mode; no control affordances; no crash on any unknown attribute shape; uses `HaEntity.Unknown` sealed subtype
**And** all variants meet the same accessibility requirements as Story 4.3
**And** `@Preview` covers all new variants with all 5 states

### Story 4.5: EntityPicker Bottom Sheet

As a power user,
I want to browse and add entities sorted by what I've used most recently,
So that building my first dashboard takes minutes, not half an hour of scrolling through 180 entities.

**Acceptance Criteria:**

**Given** Stories 4.1–4.4 complete
**When** `EntityPicker` bottom sheet is implemented
**Then** `EntityPicker` opens as `ModalBottomSheet` (M3) from the empty state prompt and from an "add card" affordance on existing dashboards
**And** domain filter chips appear at the top — one per supported domain; tapping filters the list; all domains shown by default
**And** entity list is activity-sorted by `last_updated` from `EntityState` SQLDelight table (FR18)
**And** entity rows use the same visual language as `EntityCard` — no new patterns
**And** loading state shows skeleton shimmer rows (not a spinner)
**And** empty domain state shows "No [domain] entities found in your HA" with domain icon
**And** selecting an entity calls `AddCardUseCase`, closes the sheet, new card appears at bottom of dashboard immediately
**And** picker populates in ≤500ms for up to 500 entities (NFR4)
**And** bottom sheet opens with `Motion.bottomSheetOpen`; swipe-down dismisses without adding a card

### Story 4.6: Dashboard Screen, Empty State & Navigation

As a power user,
I want to open the app and immediately see my dashboard — or be guided to build my first one — without any loading screen,
So that HaNative becomes the reflex I reach for instead of a browser.

**Acceptance Criteria:**

**Given** Stories 4.1–4.5 complete
**When** `DashboardScreen` and navigation are fully wired
**Then** `DashboardScreen` renders entity cards grouped by domain type in D4 list-row layout (`screen padding 12dp`, `card gap 8dp`)
**And** on first launch with no cards: `EmptyDashboardState` shows centered "Add your first card" CTA + `+` affordance + secondary hint "Your most-used entities appear first"; tap anywhere opens `EntityPicker` (UX-DR4)
**And** on subsequent launches: dashboard renders from SQLDelight cache with `isStale = true` in ≤1s — no loading screen (NFR2)
**And** cards update in-place as WebSocket events arrive — `key(card.entityId)` on card list; no full recomposition
**And** card reorder: long-press activates drag; `ReorderCardsUseCase` persists new positions (FR20)
**And** `HaNativeNavHost.kt` routes: no stored token → `OnboardingRoute`; stored token → `DashboardRoute` directly (FR3)
**And** M3 `NavigationBar` (Dash / Rooms / Settings) persists across all screens; does not push navigation stack
**And** `DashboardViewModel` exposes `DashboardUiState` sealed class — no raw `isLoading: Boolean`

### Story 4.7: Dashboard Management — CRUD & Switcher

As a power user,
I want to create multiple named dashboards and switch between them in one tap,
So that "Morning", "Living Room", and "Kitchen Wall" each have exactly the cards I need, accessible instantly.

**Acceptance Criteria:**

**Given** Story 4.6 complete
**When** dashboard CRUD and the switcher are implemented
**Then** tapping the active Dash nav tab opens `ModalBottomSheet` listing all named dashboards — active tab label shows current dashboard name (not a fixed "Dash" label) (UX-DR6)
**And** selecting a dashboard closes the sheet and transitions via `Motion.dashboardTransition` cross-fade in ≤200ms (NFR3), firing `HapticPattern.DashboardSwitch`
**And** "New Dashboard" in the sheet opens an inline name-entry field; after first card is added the dashboard is created and persisted (FR12)
**And** dashboard rename via M3 `DropdownMenu` overflow → inline `TextField` → confirm (FR13)
**And** dashboard delete requires confirmation modal ("Delete [name]? This cannot be undone.") before `DeleteDashboardUseCase` executes — the only modal in V1 (FR14)
**And** all dashboard configs persist across sessions and relaunches (FR16)
**And** delete is disabled when only one dashboard remains

### Story 4.8: StaleStateIndicator & Offline Resilience

As a power user,
I want the app to tell me honestly when it's lost contact with my home and recover automatically,
So that I never see a crash or blank screen, and never have to manually restart or reconnect.

**Acceptance Criteria:**

**Given** Story 4.6 complete
**When** `StaleStateIndicator` and offline resilience are implemented
**Then** `StaleStateIndicator` appears inline in the dashboard header in three states: connected (hidden — zero layout footprint), stale (amber, live counter "Last updated Xs ago" incrementing every second from last WebSocket message timestamp), reconnecting (spinner + "Reconnecting…") (UX-DR5)
**And** transition from connected → stale uses `Motion.staleIndicatorFade`; no layout shift — same position as connection pill
**And** during disconnection, `DashboardViewModel` emits `DashboardUiState.Success(cards, isStale = true)` — dashboard fully navigable, all cards show last-known state at 50% opacity with inline timestamps (FR33)
**And** on cold launch with HA unreachable: SQLDelight cache renders with `isStale = true` — no empty screen, no spinner (NFR12, NFR13)
**And** on WebSocket reconnect: `isStale` flips to `false`, indicator hides, states refresh — no user action required (FR32)
**And** internet-down-but-LAN-live: full WebSocket functionality; `StaleStateIndicator` never appears (FR29)
**And** `StaleStateIndicator` announces via TalkBack/VoiceOver: "Connection lost. Last updated [N] seconds ago." (UX-DR9)
**And** malformed WebSocket messages and unknown entity attribute shapes never crash the app — absorbed by `HaEntity.Unknown` and `MapAnySerializer` (NFR10)

---

## Epic 5: Home Screen Widget

Android users get glanceable entity state on their home screen without opening the app.

### Story 5.1: Glance Widget — Entity State Display

As a power user,
I want a home screen widget showing entity state from my dashboards,
So that I can glance at my home without opening the app.

**Acceptance Criteria:**

**Given** Epics 1–4 complete
**When** the Android Glance widget is implemented in `:androidApp`
**Then** `HaNativeWidget` (`GlanceAppWidget`) lives in `:androidApp/widget/` — not in `:shared` (AR11)
**And** the widget displays a configurable set of entity cards (up to 4) from the user's dashboards
**And** widget reads entity state from the SQLDelight cache in `:shared` — no separate network call from the widget process
**And** when HA is reachable: `GlanceAppWidgetManager.updateIf` is triggered by `EntityRepositoryImpl` on each WebSocket state event (FR28)
**And** when HA is unreachable: widget displays last-known entity state with a small staleness indicator ("Last updated Xs ago") — no blank widget, no error state (FR27)
**And** tapping the widget opens HaNative directly to the dashboard containing the tapped entity
**And** widget uses `HaNativeTheme` color tokens (`#0D1117` background, `#F59E0B` accent) for visual consistency
**And** widget is declared in `AndroidManifest.xml` with correct `AppWidgetProviderInfo` metadata

### Story 5.2: Widget Configuration & Multi-Entity Selection

As a power user,
I want to choose which entities appear on my widget,
So that my home screen shows the controls I actually reach for.

**Acceptance Criteria:**

**Given** Story 5.1 complete
**When** widget configuration is implemented
**Then** adding the widget from the launcher opens `HaNativeWidgetConfigActivity` before placement
**And** configuration presents a list of all entity cards across all dashboards — user selects up to 4
**And** selection persists in DataStore and survives device reboot
**And** user can reconfigure an existing widget via long-press → "Edit widget" on supported launchers
**And** configuration activity uses `HaNativeTheme` tokens — consistent visual language with the main app
**And** if no entities are configured, widget shows "Tap to configure" prompt

---

## Epic 6: Growth — Context Engine, Kiosk & Demo *(Phase 2)*

Automatic dashboard switching based on live home state, wall tablet kiosk mode, and a demo experience before connecting to HA.

### Story 6.1: ContextRule SQLDelight Schema & Domain Model

As a developer,
I want the context rule data schema designed and persisted,
So that the context engine and rule builder stories have a complete data layer with no schema retrofits.

**Acceptance Criteria:**

**Given** Epic 4 complete
**When** the context rule schema is designed and implemented (AR13 resolved)
**Then** `ContextRule.sq` defines `context_rule` table (`id TEXT PRIMARY KEY`, `name TEXT`, `priority INTEGER`, `created_at INTEGER`) and `context_condition` table (`id TEXT`, `rule_id TEXT` FK, `entity_id TEXT`, `operator TEXT`, `value TEXT`, `logic_group INTEGER`)
**And** `ContextRule` and `ContextCondition` domain model data classes are defined in `domain/model/` with all `val` fields
**And** `ContextRepository` interface in `domain/repository/`; `ContextRepositoryImpl` in `data/repository/`
**And** `SaveContextRuleUseCase`, `DeleteContextRuleUseCase`, `GetContextRulesUseCase` implemented as single-responsibility use cases
**And** schema migrations handled via SQLDelight `.sqm` files — no manual migration code

### Story 6.2: Context Engine — Rule Evaluation & Auto Dashboard Switch

As a power user,
I want my dashboards to switch automatically based on my home's live state,
So that I see the right controls at the right time without tapping navigation.

**Acceptance Criteria:**

**Given** Story 6.1 complete
**When** the context engine is implemented
**Then** `ContextEngineViewModel` implements MVI state machine: `sealed class ContextAction` (`EntityStateChanged`, `ManualContextSwitch`, `Refresh`) + pure reducer → `StateFlow<ContextState>`
**And** `EvaluateContextRulesUseCase` evaluates all active rules against entity `StateFlow`, returning highest-priority match (first-created wins on tie — FR40)
**And** when a rule matches, `ContextEngineViewModel` emits a dashboard switch consumed by `DashboardViewModel` — auto-transition fires identical to manual switch: `Motion.dashboardTransition` + `HapticPattern.DashboardSwitch`
**And** context badge pill appears above the Dash nav icon showing active context name (or "No Context Active") — UX-DR6 Growth variant
**And** evaluation runs on entity `StateFlow` updates — no polling

### Story 6.3: Context Rule Builder UI

As a power user,
I want to define context rules using the same if/then entity-state logic I know from HA automations,
So that the context engine has zero learning curve.

**Acceptance Criteria:**

**Given** Story 6.2 complete
**When** the context rule builder is implemented
**Then** rule builder opens as `ModalBottomSheet` from a "New Context" option in the dashboard overflow
**And** builder presents: rule name field, AND/OR logic selector, condition blocks (entity picker → operator dropdown: `=`, `≠`, `>`, `<` → value input)
**And** condition entity picker reuses `EntityPicker` from Story 4.5 — no new picker implementation
**And** user can add multiple conditions; each condition block is removable
**And** saving calls `SaveContextRuleUseCase` and activates the rule immediately
**And** `ContextRuleBuilderScreen.kt` + `ContextRuleBuilderViewModel.kt` follow established screen/ViewModel pattern

### Story 6.4: Pre-Built Rule Gallery

As a power user,
I want to choose from pre-built context rule templates,
So that I can start using the context engine without constructing rules from scratch.

**Acceptance Criteria:**

**Given** Story 6.3 complete
**When** the rule gallery is implemented
**Then** "Browse Templates" in the rule builder opens a gallery of pre-built rules (FR41)
**And** gallery includes at minimum: "Weekday Morning" (time + weekday conditions), "Someone Arrives" (person entity = home), "Movie Time" (media_player = playing)
**And** tapping a template pre-fills the rule builder — user can edit before saving
**And** templates are bundled in `commonMain` (no network fetch) as a sealed class hierarchy or data file

### Story 6.5: App Registers as HA Device Entity

As a power user,
I want HaNative to appear as a controllable device in Home Assistant,
So that my HA automations can trigger dashboard switches just like they control lights and locks.

**Acceptance Criteria:**

**Given** Story 6.2 complete
**When** HA device registration is implemented
**Then** on first launch after connecting, HaNative registers itself via the HA mobile app integration WebSocket API
**And** registration exposes: `sensor.hanative_active_context` and `sensor.hanative_current_dashboard` as HA entities (FR42)
**And** HA automations can trigger a context switch — `ContextEngineViewModel` processes incoming WebSocket event as `ContextAction.ManualContextSwitch`
**And** entity states update in HA within 1 second of a dashboard or context change
**And** registration is idempotent — re-registering on relaunch does not create duplicate HA entities

### Story 6.6: Kiosk Mode

As a power user setting up a wall tablet,
I want HaNative to run fullscreen with the dashboard locked and display always on,
So that the tablet becomes a permanent home control panel anyone can use without navigating.

**Acceptance Criteria:**

**Given** Epic 4 complete
**When** kiosk mode is implemented
**Then** Settings → Kiosk Mode toggle hides OS chrome (status bar, navigation bar) via `WindowManager.LayoutParams.FLAG_FULLSCREEN` (FR43)
**And** `WAKE_LOCK` (`android.permission.WAKE_LOCK`) is acquired when kiosk mode is active — display stays on
**And** dashboard lock toggle prevents `DashboardSwitcher` from opening while locked (FR44)
**And** after configurable idle period (default 5 min), display dims to 20% brightness; any tap wakes to full brightness without unlocking or navigating (FR45)
**And** kiosk settings (enabled, dashboard lock, idle timeout) persist in DataStore across relaunches
**And** kiosk mode is Android-only in V1 — iOS implementation deferred

### Story 6.7: Demo Onboarding

As a prospective user,
I want to see HaNative working with simulated entity data before connecting to my HA,
So that I understand what the app does before committing to setup.

**Acceptance Criteria:**

**Given** Epic 4 complete
**When** demo onboarding is implemented
**Then** the onboarding flow offers "Try a Demo" option before URL entry (FR46)
**And** demo mode loads bundled simulated entities covering all 10 supported domains — no network required
**And** simulated entity state responds to taps (toggles, temperature steps) — updates are in-memory only, never sent to HA
**And** haptic patterns fire normally in demo mode — the physical feel moment is fully demonstrated
**And** a persistent "Demo Mode" banner is visible at all times; tapping it exits demo and returns to HA URL entry
**And** `DemoScreen.kt` + `ConnectHaScreen.kt` implement the two-step flow
**And** auto-generated starter dashboard (FR47): on first real HA connection after demo, the 5 most-recently-active entities are added to a pre-built "My Home" dashboard — user arrives at a working dashboard without the empty state
