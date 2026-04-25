---
stepsCompleted: ['step-01-init', 'step-02-context', 'step-03-starter', 'step-04-decisions', 'step-05-patterns', 'step-06-structure', 'step-07-validation', 'step-08-complete']
status: 'complete'
completedAt: '2026-04-21'
inputDocuments:
  - '_bmad/outputs/prd.md'
workflowType: 'architecture'
project_name: 'HaNative'
user_name: 'Jeremy Blanchard'
date: '2026-04-21'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

---

## Project Context Analysis

### System Concerns (from 47 FRs)

| Concern | FRs | Core Requirement |
|---------|-----|-----------------|
| **HA Connectivity** | FR1–FR8 | WebSocket local-first, Nabu Casa cloud fallback, ≤5s reconnect, session persistence across network switches |
| **Entity State Management** | FR9–FR16 | Real-time state from WebSocket subscription, 10 entity domains, optimistic UI updates, stale-state cache for offline glanceability |
| **Dashboard & Card System** | FR17–FR30 | Create/edit dashboards, manual context switch (MVP), auto-switch via entity rules (Growth), dashboard→widget→context rule one definition (SCAMPER C1), card add/remove/reorder |
| **Theme & Physical Design** | FR31–FR38 | One built-in theme (V1 reference impl), Compose component contract (public API for V2 SDK), spring physics on dashboard transitions (FR24b), per-entity haptics/micro-animations authored by theme |
| **Widget Surface** | FR39–FR47 | Context-aware home screen widget (Growth), Glance (Android) / WidgetKit (iOS) separate native integration, widget reflects active context's top entities |

### NFR Architectural Impacts

| NFR | Target | Architectural Impact |
|-----|--------|---------------------|
| Entity control roundtrip | ≤500ms | Optimistic UI updates mandatory; WebSocket command/response correlation |
| App launch to dashboard | ≤1s cold | Cached entity state must survive process death; no blocking network calls on startup |
| Dashboard switch | ≤200ms | Context transition must be pre-computed or asset-preloaded; no layout inflation on switch |
| Animation | 60fps | Compose render thread discipline; no blocking on main thread during spring physics |
| Credential storage | Keystore/Keychain | Platform expect/actual pattern; no shared KMP impl |
| Network reconnect | ≤5s | WebSocket reconnect with exponential backoff; entity state re-subscription on reconnect |

### Technical Constraints

- **HA WebSocket API**: Community-maintained, unofficial, no SLA. Must absorb breaking changes without app store release cycle. Abstraction layer mandatory.
- **KMP**: Shared business logic (connectivity, entity model, context engine, dashboard state). Platform expect/actual for Keystore/Keychain, haptics, Glance/WidgetKit.
- **Compose Multiplatform (CMP)**: Shared UI layer for phone screens. Widget surfaces are NOT CMP — Glance (Android) and WidgetKit (iOS) require separate native implementations.
- **Nabu Casa**: Cloud relay only. Same WebSocket protocol, different auth. No Nabu Casa-specific business logic.
- **V2 SDK backward compatibility**: V1 component contracts are public API. Internal theme = reference implementation. Breaking changes in V2 must be versioned.

### Cross-Cutting Concerns

1. **Entity state distribution**: Single source of truth in shared KMP layer. UI observes state flows. No per-screen entity fetching.
2. **Platform expect/actual bridge**: Keystore/Keychain, haptics, widget surfaces, biometric auth all require platform implementations behind KMP interfaces.
3. **Offline / stale state**: Entity state cached to disk (encrypted). App launches into stale state, overlays "last updated" indicator, refreshes on WebSocket connect.
4. **Theme component contract**: Every card type must implement the full component contract (all entity domains × all size buckets × all state variants). V1 internal theme proves the contract before V2 SDK publishes it.
5. **Session persistence**: WebSocket auth token + server URL persisted to Keystore/Keychain. Auto-reconnect on app foreground. No re-login on network switch.

---

## Starter Template Evaluation

### Primary Technology Domain

KMP + Compose Multiplatform mobile app — Android-first, iOS via CMP shared UI layer.

### Starter Options Considered

| Option | Approach | Trade-off |
|--------|----------|-----------|
| **KMP Wizard scaffold** ✅ | Generate fresh multi-module KMP+CMP project at kmp.jetbrains.com | Correct structure from day one; zero real code to preserve |
| Add KMP module to existing `:app` | AS Meerkat "KMP Shared Module" template | Keeps Android project; `:app` stays Android-only — awkward for CMP UI sharing |
| Manual restructure | Hand-craft `build.gradle.kts` | Full control; high Gradle expertise required |

### Selected Starter: KMP Wizard (kmp.jetbrains.com) — Fresh Scaffold

**Rationale:** Zero real source code in current project. Wizard generates correct three-module structure with CMP "Share UI" enabled from the start. Existing project is bare Android boilerplate — nothing to preserve.

**Setup path:**
1. Generate at kmp.jetbrains.com — targets: Android + iOS, "Share UI" enabled
2. Port `applicationId = "com.backpapp.hanative"`, `rootProject.name = "HaNative"`
3. Discard generated stub code; keep Gradle structure

**Architectural Decisions Provided by Starter:**

| Category | Decision |
|----------|----------|
| Language | Kotlin 2.3.20, targeting JVM (Android) + Native (iOS) |
| UI | Compose Multiplatform 1.10.x — shared `commonMain` Composables |
| Module structure | `:shared` (KMP logic + CMP UI), `:androidApp` (Android host + Glance), `:iosApp` (iOS host + WidgetKit) |
| Build tooling | Gradle 8.x + AGP 8.8+, `kotlin.multiplatform` plugin |
| iOS integration | CocoaPods or direct XCFramework via `embedAndSignAppleFrameworkForXcode` |
| Testing | `kotlin.test` in `:shared`, Espresso in `:androidApp`, XCTest in `:iosApp` |
| State management | Not prescribed — decided in Step 4 |

**HaNative-specific module layout:**

```
:shared
  ├── commonMain/   ← HA WebSocket client, entity model, context engine, dashboard state, CMP UI
  ├── androidMain/  ← Android Keystore impl, platform expects
  └── iosMain/      ← iOS Keychain impl, platform expects
:androidApp         ← Compose host Activity, Glance widget, notifications
:iosApp             ← SwiftUI host entry, WidgetKit, notifications
```

> Widget surfaces (Glance / WidgetKit) are platform-specific — they live in `:androidApp` / `:iosApp`, not `:shared`. CMP does not cover widget surfaces.

**Current versions:**
- Kotlin: `2.3.20`
- Compose Multiplatform: `1.10.x`
- AGP: `8.8.0+`
- iOS CMP: stable (since May 2025)

---

## Core Architectural Decisions

### Decision Priority Analysis

**Critical (block implementation):**
- Clean Architecture layering + ViewModel + StateFlow for all state
- Ktor WebSocket for HA connectivity
- SQLDelight for entity state cache + dashboard config
- Koin for dependency injection
- Compose Navigation 3 for in-app routing

**Important (shape architecture):**
- Platform expect/actual `CredentialStore` (Keystore / Keychain)
- DataStore Preferences for auth tokens + user settings
- MVI-style reducer for context engine state machine specifically

**Deferred (post-MVP):**
- Widget communication channel between `:androidApp` and `:shared` state
- iOS WidgetKit architecture (Growth — no widgets in MVP)

---

### Data Architecture

**Pattern:** Clean Architecture layers in `:shared/commonMain`

```
UI Layer (Compose)         → ViewModels + UiState sealed class
Domain Layer               → UseCases / Interactors (pure Kotlin)
Data Layer                 → Repositories (interface in domain, impl in data)
                               ├── Remote: Ktor WebSocket (HaWebSocketClient)
                               └── Local:  SQLDelight (EntityCache, DashboardStore)
```

**Entity state cache:** SQLDelight. Tables: `entity_state`, `dashboard`, `dashboard_card`, `context_rule`. Query by entity domain for card rendering. Encrypted at rest via platform expect/actual.

**Auth / settings:** DataStore Preferences (KMP) — lightweight, no SQL overhead for KV config.

**Migration approach:** SQLDelight migrations via `.sqm` files. Versioned, compile-time verified.

---

### Authentication & Security

| Concern | Decision |
|---------|---------|
| HA auth token storage | `expect interface CredentialStore` / `actual` per platform |
| Android impl | `EncryptedSharedPreferences` (Keystore-backed) |
| iOS impl | `Security.framework` Keychain via `iosMain` actual |
| WebSocket auth | Bearer token in WebSocket handshake header |
| No biometric gate in MVP | Deferred to Growth |

HA auth flow: OAuth2 device authorization (HA supports) or direct long-lived token. Token fetched once, stored in `CredentialStore`, refreshed only on 401.

---

### API & Communication

**HA WebSocket protocol:**
- Ktor WebSocket client in `commonMain` with CIO engine (Android) + Darwin engine (iOS)
- JSON serialization: `kotlinx.serialization`
- Message types: `auth`, `subscribe_events`, `call_service`, `get_states`
- Abstraction: `HaWebSocketClient` interface in domain layer; Ktor impl in data layer
- Reconnect: exponential backoff (1s → 2s → 4s → max 30s), ≤5s first reconnect

**No REST API for entity control** — all commands via WebSocket `call_service`. REST used only for initial auth token exchange.

---

### Frontend Architecture (Compose Multiplatform)

**State management:** ViewModel (KMP `lifecycle-viewmodel`) + `StateFlow<UiState>` + `collectAsStateWithLifecycle()`. One ViewModel per screen/feature.

**Context engine state:** MVI-style in `ContextEngineViewModel` — `sealed class ContextAction` + pure reducer function → `StateFlow<ContextState>`. No external MVI library.

**Component architecture:** Feature-based packages in `commonMain/ui/`. Shared Composables in `commonMain/ui/components/`. Platform-specific overrides via `expect`/`actual` Composables only when unavoidable.

**Navigation:** Compose Navigation 3 (JetBrains). Type-safe routes via `@Serializable` data classes. Single `NavHost` in `:shared`. Deep links handled in platform entry points.

**Performance:** Entity state updates must not trigger full dashboard recomposition. Card Composables observe only their own entity's `StateFlow` slice. `key()` blocks on card lists.

---

### Infrastructure & Deployment

| Area | Decision |
|------|---------|
| CI | GitHub Actions |
| Android distribution | Play Store internal → beta → production |
| iOS distribution | TestFlight → App Store |
| Static analysis | Detekt + ktlint |
| Version catalog | `libs.versions.toml` (Gradle 8.x standard) |
| Build variants | `debug` + `release` only in MVP |

---

### Decision Impact Analysis

**Implementation sequence:**
1. KMP Wizard scaffold → three-module structure
2. Koin modules (data + domain + presentation layers)
3. `HaWebSocketClient` (Ktor) + auth flow + `CredentialStore` expect/actual
4. SQLDelight schema + `EntityCache` + `DashboardStore`
5. Entity state distribution (`StateFlow` from repository → ViewModels)
6. Card Composables (10 entity domains × component contract)
7. Dashboard create/edit UI + Compose Navigation 3
8. Context engine (manual switch MVP; auto-switch Growth)

**Cross-component dependencies:**
- `EntityCache` (SQLDelight) feeds initial state on cold launch → ViewModel reads cache before WebSocket reconnects
- `HaWebSocketClient` pushes state events → `EntityRepository` writes to `EntityCache` + emits to `StateFlow`
- `ContextEngine` reads entity `StateFlow` → emits active context → `DashboardViewModel` selects cards
- `CredentialStore` gates `HaWebSocketClient` init — no token = onboarding flow

---

## Implementation Patterns & Consistency Rules

### Naming Patterns

**Kotlin (all modules):** `camelCase` functions/variables, `PascalCase` classes/objects/Composables, `SCREAMING_SNAKE_CASE` constants.

**SQLDelight:** `snake_case` table and column names. Foreign keys = `{table}_id`. Index prefix = `idx_`.

**Packages:** `com.backpapp.hanative.{layer}.{feature}` — e.g. `com.backpapp.hanative.data.websocket`, `com.backpapp.hanative.domain.entity`, `com.backpapp.hanative.ui.dashboard`.

**HA entity domains:** Mirror HA domain strings exactly — `light`, `switch`, `climate`, `cover`, `media_player`, `sensor`, `binary_sensor`, `input_boolean`, `input_select`, `script`, `scene`, `automation`. No renaming.

---

### Structure Patterns

**Clean Architecture layer enforcement:**

```
domain/          ← pure Kotlin only — no Android/Ktor/SQLDelight imports
  model/         ← HaEntity, Dashboard, ContextRule, DashboardCard (data classes, all val)
  repository/    ← interfaces only
  usecase/       ← one class per use case
data/
  remote/        ← Ktor WebSocket impl + serialization DTOs (data/remote/dto/)
  local/         ← SQLDelight generated queries + adapters
  repository/    ← repository implementations
ui/
  {feature}/
    {Feature}Screen.kt       ← Composable entry point
    {Feature}ViewModel.kt    ← ViewModel + UiState sealed class
    components/              ← feature-local Composables
  components/                ← shared card Composables (all entity domains)
```

**Tests:** Unit tests in `src/{sourceSet}Test/`. Instrumented tests in `src/androidInstrumentedTest/`. No `__tests__` directories.

**Expect/actual:** `expect` in `commonMain`, matching `actual` in `androidMain` and `iosMain`. File names identical across all three.

---

### Format Patterns

**UI state shape — always:**
```kotlin
sealed class {Feature}UiState {
    object Loading : {Feature}UiState()
    data class Success(val data: T, val isStale: Boolean = false) : {Feature}UiState()
    data class Error(val message: String) : {Feature}UiState()
}
```
Never raw booleans for loading.

**Domain models:** Immutable data classes, all `val`. Copy-on-write via `.copy()`. No mutable fields.

**HA WebSocket messages:** Deserialize to domain models at data layer boundary. No `JsonObject` above `data/remote/`. DTOs live in `data/remote/dto/`.

**Date/time:** `kotlinx.datetime.Instant` in `commonMain`. Format for display at UI layer only.

---

### Communication Patterns

**ViewModel state:** `val uiState: StateFlow<{Feature}UiState>` — one per ViewModel. One-shot events via `Channel<{Feature}Event>` exposed as `Flow`.

**WebSocket → state pipeline:**
```
HaWebSocketClient (Ktor) → Flow<HaEvent>
  → EntityRepository (collect, map to domain, write SQLDelight, emit StateFlow)
  → ViewModels (collect StateFlow slice)
```
No direct WebSocket access above repository.

**Koin modules:** `dataModule`, `domainModule`, `presentationModule` — one per layer. Loaded in platform entry points.

**Context engine actions:**
```kotlin
sealed class ContextAction {
    data class EntityStateChanged(val entity: HaEntity) : ContextAction()
    data class ManualContextSwitch(val contextId: String) : ContextAction()
    object Refresh : ContextAction()
}
```

---

### Process Patterns

**Error handling:** Repository catches exceptions → `Result<T>`. UseCase propagates `Result`. ViewModel maps `Result.Failure` → `UiState.Error`. No raw exceptions above data layer.

**Entity control (optimistic UI):** Emit `UiState.Success` with pending state immediately → revert on WebSocket error. No loading spinner for entity toggle.

**Reconnect:** Transparent to UI. `UiState.Success(data, isStale = true)` during reconnect window. No loading state.

**Cold launch:** Read SQLDelight cache first → emit `UiState.Success(isStale = true)` → overlay "last updated" badge → refresh when WebSocket connects. Empty cache = `UiState.Loading`.

---

### Enforcement

**All agents MUST:**
- Never import Ktor / SQLDelight / Android SDK in `domain/` package
- Never use `var` in domain model fields
- Never access WebSocket events above repository layer
- Always use `{Feature}UiState` sealed class — no ad-hoc `isLoading: Boolean`
- Always use HA entity domain strings verbatim in code

---

## Project Structure & Boundaries

### FR → Directory Mapping

| FR Group | Lives in |
|----------|---------|
| FR1–8 (HA connectivity) | `:shared/commonMain/data/remote/` |
| FR9–16 (entity state) | `:shared/commonMain/domain/` + `data/local/` |
| FR17–30 (dashboard/cards) | `:shared/commonMain/ui/dashboard/` + `ui/components/` |
| FR31–38 (theme/physical design) | `:shared/commonMain/ui/theme/` + `ui/components/` |
| FR39–47 (widgets) | `:androidApp/.../widget/` (Growth) |

### Complete Project Tree

```
HaNative/
├── .github/
│   └── workflows/
│       ├── android-ci.yml
│       └── ios-ci.yml
├── gradle/
│   └── libs.versions.toml          ← version catalog
├── build.gradle.kts                ← root build (plugins only)
├── settings.gradle.kts             ← include :shared, :androidApp, :iosApp
├── gradle.properties
│
├── shared/
│   ├── build.gradle.kts            ← kotlin("multiplatform"), compose.multiplatform
│   └── src/
│       ├── commonMain/kotlin/com/backpapp/hanative/
│       │   ├── domain/
│       │   │   ├── model/
│       │   │   │   ├── HaEntity.kt              ← sealed class + 10 subtypes
│       │   │   │   ├── EntityState.kt
│       │   │   │   ├── Dashboard.kt
│       │   │   │   ├── DashboardCard.kt
│       │   │   │   └── ContextRule.kt
│       │   │   ├── repository/
│       │   │   │   ├── EntityRepository.kt      ← interface
│       │   │   │   ├── DashboardRepository.kt   ← interface
│       │   │   │   └── ContextRepository.kt     ← interface
│       │   │   └── usecase/
│       │   │       ├── ObserveEntityStateUseCase.kt
│       │   │       ├── CallServiceUseCase.kt
│       │   │       ├── GetDashboardUseCase.kt
│       │   │       ├── SaveDashboardUseCase.kt
│       │   │       └── EvaluateContextRulesUseCase.kt
│       │   ├── data/
│       │   │   ├── remote/
│       │   │   │   ├── HaWebSocketClient.kt     ← interface
│       │   │   │   ├── KtorHaWebSocketClient.kt ← Ktor impl
│       │   │   │   ├── HaReconnectManager.kt    ← exponential backoff
│       │   │   │   └── dto/
│       │   │   │       ├── HaMessageDto.kt
│       │   │   │       ├── EntityStateDto.kt
│       │   │   │       └── ServiceCallDto.kt
│       │   │   ├── local/
│       │   │   │   ├── HaNativeDatabase.kt      ← SQLDelight generated
│       │   │   │   └── adapter/
│       │   │   │       └── EntityDomainAdapter.kt
│       │   │   └── repository/
│       │   │       ├── EntityRepositoryImpl.kt
│       │   │       ├── DashboardRepositoryImpl.kt
│       │   │       └── ContextRepositoryImpl.kt
│       │   ├── di/
│       │   │   ├── DataModule.kt
│       │   │   ├── DomainModule.kt
│       │   │   └── PresentationModule.kt
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   │   ├── HaNativeTheme.kt
│       │   │   │   ├── Color.kt
│       │   │   │   ├── Typography.kt
│       │   │   │   ├── Shape.kt
│       │   │   │   └── Motion.kt                ← spring physics constants
│       │   │   ├── components/
│       │   │   │   ├── LightCard.kt
│       │   │   │   ├── SwitchCard.kt
│       │   │   │   ├── ClimateCard.kt
│       │   │   │   ├── CoverCard.kt
│       │   │   │   ├── MediaPlayerCard.kt
│       │   │   │   ├── SensorCard.kt
│       │   │   │   ├── InputBooleanCard.kt
│       │   │   │   ├── InputSelectCard.kt
│       │   │   │   ├── ScriptCard.kt
│       │   │   │   ├── SceneCard.kt
│       │   │   │   └── AutomationCard.kt
│       │   │   ├── onboarding/
│       │   │   │   ├── OnboardingScreen.kt
│       │   │   │   ├── OnboardingViewModel.kt
│       │   │   │   ├── DemoScreen.kt
│       │   │   │   └── ConnectHaScreen.kt
│       │   │   ├── dashboard/
│       │   │   │   ├── DashboardScreen.kt
│       │   │   │   ├── DashboardViewModel.kt
│       │   │   │   ├── DashboardEditorScreen.kt
│       │   │   │   └── DashboardEditorViewModel.kt
│       │   │   ├── context/                     ← Growth
│       │   │   │   ├── ContextRuleBuilderScreen.kt
│       │   │   │   └── ContextEngineViewModel.kt
│       │   │   └── navigation/
│       │   │       ├── HaNativeNavHost.kt
│       │   │       └── Routes.kt                ← @Serializable route classes
│       │   └── platform/
│       │       ├── CredentialStore.kt           ← expect interface
│       │       ├── HapticFeedback.kt            ← expect interface
│       │       ├── ServerDiscovery.kt           ← expect interface (mDNS)
│       │       └── AppLifecycleObserver.kt      ← expect class (foreground reconnect)
│       ├── commonMain/sqldelight/com/backpapp/hanative/
│       │   ├── EntityState.sq
│       │   ├── Dashboard.sq
│       │   ├── DashboardCard.sq
│       │   └── ContextRule.sq
│       ├── androidMain/kotlin/com/backpapp/hanative/platform/
│       │   ├── AndroidCredentialStore.kt        ← actual
│       │   ├── AndroidHapticFeedback.kt         ← actual
│       │   ├── AndroidServerDiscovery.kt        ← actual (NsdManager)
│       │   └── AndroidAppLifecycleObserver.kt   ← actual (ProcessLifecycleOwner)
│       ├── iosMain/kotlin/com/backpapp/hanative/platform/
│       │   ├── IosCredentialStore.kt            ← actual
│       │   ├── IosHapticFeedback.kt             ← actual
│       │   ├── IosServerDiscovery.kt            ← actual (NetServiceBrowser)
│       │   └── IosAppLifecycleObserver.kt       ← actual (didBecomeActiveNotification)
│       ├── commonTest/kotlin/
│       │   ├── domain/usecase/
│       │   └── data/repository/
│       └── androidInstrumentedTest/kotlin/
│           └── data/local/
│
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/backpapp/hanative/
│       │   ├── HaNativeApplication.kt           ← startKoin
│       │   ├── MainActivity.kt
│       │   └── widget/                          ← Growth: Glance
│       │       └── HaNativeWidget.kt
│       └── res/
│
├── ha-upstream-refs/                           ← HA source snapshots for diff watcher
│   ├── android/
│   │   ├── Entity.kt
│   │   ├── WebSocketCoreImpl.kt
│   │   ├── AuthenticationRepositoryImpl.kt
│   │   ├── MapAnySerializer.kt
│   │   └── entities/                           ← WS message type snapshots
│   └── ios/
│       ├── Bonjour.swift
│       ├── TokenManager.swift
│       └── WebSocketMessage.swift
│
└── iosApp/
    ├── iosApp.xcodeproj/
    ├── iosApp/
    │   ├── Info.plist                           ← NSLocalNetworkUsageDescription + NSBonjourServices
    │   ├── iOSApp.swift                         ← init Koin + scene lifecycle
    │   ├── ContentView.swift                    ← ComposeUIViewController host
    │   └── HaNativeWidgetExtension/             ← Growth: WidgetKit + HAKit SPM dep
    └── Package.swift                            ← HAKit dep (Growth)
```

### Architectural Boundaries

**Data flow (unidirectional):**
```
WebSocket events
  → KtorHaWebSocketClient → Flow<HaEvent>
  → EntityRepositoryImpl  → EntityState table + StateFlow<List<HaEntity>>
  → ViewModels            → UiState
  → Composables           → render
```

**Entity control (reverse path):**
```
Composable tap → ViewModel → CallServiceUseCase
  → EntityRepository → KtorHaWebSocketClient.callService()
  → optimistic UiState immediately
  → WebSocket confirms → state reconciled
```

**Cold launch sequence:**
```
App start → DashboardViewModel reads SQLDelight cache
  → UiState.Success(isStale=true) → dashboard visible immediately
  → WebSocket connects → live events replace stale data
```

**Cross-module rule:** `:androidApp` and `:iosApp` import `:shared` only. No inter-platform imports.

### FR → File Mapping

| Requirement | File |
|-------------|------|
| FR1–3 WebSocket connect/auth | `KtorHaWebSocketClient.kt`, `HaReconnectManager.kt` |
| FR4–5 Nabu Casa fallback | `KtorHaWebSocketClient.kt` (server URL swap) |
| FR6–8 Session persistence | `AndroidCredentialStore.kt` / `IosCredentialStore.kt` |
| FR9–11 Entity state subscription | `EntityRepositoryImpl.kt`, `EntityState.sq` |
| FR12–14 Optimistic UI | `DashboardViewModel.kt`, `CallServiceUseCase.kt` |
| FR15–16 Stale state cache | `EntityRepositoryImpl.kt`, `DashboardViewModel.kt` |
| FR17–20 Dashboard CRUD | `DashboardScreen.kt`, `DashboardEditorScreen.kt`, `Dashboard.sq` |
| FR21–23 Card add/remove/reorder | `DashboardEditorScreen.kt`, `DashboardCard.sq` |
| FR24b Dashboard transitions | `HaNativeNavHost.kt`, `Motion.kt` |
| FR25–30 10 entity card domains | `ui/components/*.kt` (11 files) |
| FR31–34 Theme system | `ui/theme/` |
| FR35–38 Haptics/micro-animations | `HapticFeedback.kt` expect/actual |
| FR39–47 Widgets | `androidApp/widget/` (Growth) |
| Onboarding demo-first | `DemoScreen.kt`, `ConnectHaScreen.kt` |
| Context engine (Growth) | `ui/context/`, `ContextRepositoryImpl.kt`, `ContextRule.sq` |
| mDNS discovery (onboarding) | `ServerDiscovery.kt` expect/actual |
| Foreground reconnect | `AppLifecycleObserver.kt` expect/actual |
| iOS local network permission | `iosApp/Info.plist` |
| Growth WidgetKit data | HAKit REST from widget extension process |

---

## HA Upstream Compatibility Strategy

### Approach: Port Once (KMP-idiomatic) + GitHub Actions Diff Watcher

**License:** Both HA Android and iOS repos are Apache 2.0. Commercial use permitted. Attribution required — add `NOTICE` file at project root.

### Ported components (not verbatim copies — KMP-idiomatic translations)

| Source | KMP target | Key adaptation |
|--------|-----------|---------------|
| `Entity.kt` (Android) | `commonMain/domain/model/Entity.kt` | `kotlinx.datetime.Instant` replaces Java time |
| `WebSocketCoreImpl.kt` (Android) | `commonMain/data/remote/WebSocketRepository.kt` | OkHttp WebSocket → Ktor `DefaultClientWebSocketSession` |
| `AuthenticationRepositoryImpl.kt` (Android) | `commonMain/data/remote/AuthenticationRepositoryImpl.kt` | Retrofit → Ktor `HttpClient`; add Mutex for refresh dedup |
| `AuthenticationService.kt` (Android) | `commonMain/data/remote/AuthenticationService.kt` | Retrofit interface → Ktor typed request |
| `MapAnySerializer.kt` (Android) | `commonMain/data/remote/MapAnySerializer.kt` | Verbatim — pure `kotlinx.serialization`, zero platform deps |
| `entities/` message types (Android) | `commonMain/data/remote/entities/` | Verbatim — pure Kotlin, zero platform deps |
| `ServerManager.kt` (Android) | `commonMain/data/remote/ServerManager.kt` | OkHttp → Ktor; add `AppLifecycleObserver` integration |
| `TokenManager.swift` refresh pattern (iOS) | `AuthenticationRepositoryImpl.kt` Mutex pattern | Kotlin `Mutex` + `Deferred` deduplication |
| `Bonjour.swift` (iOS) | `iosMain/platform/IosServerDiscovery.kt` | Swift `NetServiceBrowser` → Kotlin `NSNetServiceBrowser` interop |

### GitHub Actions diff watcher

```yaml
# .github/workflows/ha-upstream-watch.yml
# Runs weekly (Sunday 00:00 UTC)
# Fetches watched files from HA Android + iOS repos
# Diffs against ha-upstream-refs/ snapshots
# Opens GitHub issue with diff if any file changed
# Label: "ha-upstream-change" — requires human review before porting
```

**Watched files:**

| Repo | File | Why watch |
|------|------|-----------|
| Android | `common/.../integration/Entity.kt` | Entity model + extension functions |
| Android | `common/.../websocket/impl/WebSocketCoreImpl.kt` | WS protocol impl |
| Android | `common/.../authentication/impl/AuthenticationRepositoryImpl.kt` | Auth flow |
| Android | `common/.../websocket/impl/entities/` (dir) | WS message types |
| Android | `common/.../data/integration/impl/IntegrationRepositoryImpl.kt` | Mobile app registration |
| iOS | `Sources/Shared/API/Authentication/TokenManager.swift` | Refresh dedup pattern |
| iOS | `Sources/App/Onboarding/API/Bonjour.swift` | mDNS discovery pattern |
| iOS | `Sources/Shared/API/Models/WebSocketMessage.swift` | Protocol message shapes |

**Update process:** Issue opened → developer reviews diff → determines if change affects KMP port → ports relevant changes → updates snapshot in `ha-upstream-refs/`.

### Token refresh deduplication (ported from iOS TokenManager pattern)

```kotlin
// AuthenticationRepositoryImpl.kt
private val refreshMutex = Mutex()
private var refreshJob: Deferred<TokenInfo>? = null

suspend fun getValidToken(): String = refreshMutex.withLock {
    if (tokenInfo.isExpired()) {
        refreshJob?.await() ?: coroutineScope {
            async { doRefresh() }.also { refreshJob = it }
        }.await().also { refreshJob = null }
    }
    tokenInfo.accessToken
}
```

### iOS-specific requirements

**Info.plist (mandatory for LAN WebSocket):**
```xml
<key>NSLocalNetworkUsageDescription</key>
<string>Connect directly to your Home Assistant instance on your local network.</string>
<key>NSBonjourServices</key>
<array>
  <string>_home-assistant._tcp</string>
  <string>_hass-mobile-app._tcp</string>
</array>
```

**Growth — WidgetKit data source:**
WidgetKit extension runs in separate process; cannot access main app's Kotlin WebSocket state. Use HAKit (Swift) via REST for widget timeline refreshes.
```swift
// Package.swift — iosApp Growth dependency
.package(url: "https://github.com/home-assistant/HAKit", from: "0.4.14")
```

---

## Architecture Validation Results

### Coherence Validation ✅

**Decision compatibility:** Kotlin 2.3.20 + CMP 1.10.x + Ktor 3.x + SQLDelight 2.x + Koin 4.x + Compose Navigation 3 — all KMP-compatible, no version conflicts. `kotlinx.serialization` shared across Ktor, Navigation 3 type-safe routes, and ported HA message types — no duplication. `lifecycle-viewmodel` KMP artifact available in CMP 1.10.x.

**HA source compatibility:** Ported components use same `kotlinx.serialization` as our stack. `MapAnySerializer` ports verbatim. `Entity.kt` has zero platform imports — direct KMP adoption. Auth flow maps cleanly Retrofit → Ktor. OkHttp WebSocket → Ktor `DefaultClientWebSocketSession` is a compatible API swap.

**Pattern alignment:** Clean Architecture import rules enforced by Koin module boundaries. `UiState` sealed class pattern works directly with `StateFlow`. Expect/actual for 4 platform concerns (CredentialStore, HapticFeedback, ServerDiscovery, AppLifecycleObserver) aligns with three-module wizard scaffold.

**One implementation note:** SQLDelight 2.x requires `kotlinx.datetime` adapter for `Instant` columns. Must be wired explicitly in `EntityDomainAdapter.kt`. Flag in first data-layer story.

### Requirements Coverage Validation ✅

| FR Group | Coverage | Source |
|----------|---------|--------|
| FR1–8 HA connectivity | ✅ | Ported `WebSocketRepository` + `ServerManager` + `CredentialStore` |
| FR9–16 Entity state | ✅ | Ported `Entity.kt` + SQLDelight cache + `StateFlow` pipeline |
| FR17–30 Dashboard/cards | ✅ | 11 card Composables + `DashboardEditor` + `Dashboard.sq` |
| FR31–38 Theme/physical design | ✅ | `ui/theme/` + `HapticFeedback` expect/actual + `Motion.kt` |
| FR39–47 Widgets | ✅ Growth | Glance (Android) + HAKit REST (iOS WidgetKit) |
| Onboarding demo-first | ✅ | `DemoScreen.kt` + `ConnectHaScreen.kt` + `ServerDiscovery` |
| Context engine | ✅ Growth | `ui/context/` + `ContextRule.sq` scaffolded |

| NFR | Coverage |
|-----|---------|
| ≤500ms entity control | Optimistic UI — emit before WebSocket round-trip |
| ≤1s cold launch | SQLDelight cache read on start; no blocking network call |
| ≤200ms dashboard switch | Compose Navigation 3 + pre-loaded `StateFlow` |
| 60fps | Compose render thread; spring physics in `Motion.kt` |
| Keystore/Keychain | Expect/actual `CredentialStore` |
| ≤5s reconnect | `ServerManager` exponential backoff + `AppLifecycleObserver` foreground trigger |

### Gap Analysis Results

**Critical gaps:** None.

**Important — 2 items:**
1. SQLDelight 2.x `kotlinx.datetime` adapter must be explicitly wired in `EntityDomainAdapter.kt` — first data-layer story must call this out
2. `ContextRule.sq` schema undefined — Growth story must design schema before context engine story is assigned

**Minor:**
- `HaNativeDatabase.kt` is SQLDelight-generated — agents must not hand-author it
- `ha-upstream-refs/` snapshots must be committed at project init with the initial ported file versions

### Architecture Completeness Checklist

- [x] Project context analyzed — 47 FRs, 6 NFRs, 5 cross-cutting concerns
- [x] Starter: KMP Wizard, three-module, Kotlin 2.3.20 + CMP 1.10.x
- [x] All critical decisions documented
- [x] Clean Architecture layers enforced via import rules + Koin boundaries
- [x] HA source ported (not copied) — Entity.kt, WebSocket types, Auth, ServerManager
- [x] HA upstream diff watcher defined (GitHub Actions, weekly)
- [x] Apache 2.0 attribution required — NOTICE file
- [x] Token refresh dedup pattern (Mutex) ported from iOS TokenManager
- [x] iOS local network permission (Info.plist) specified
- [x] 4 expect/actual platform interfaces defined
- [x] Growth widget architecture decided (HAKit REST from WidgetKit process)
- [x] mDNS discovery (ServerDiscovery expect/actual) added
- [x] Foreground reconnect (AppLifecycleObserver expect/actual) added
- [x] Complete project tree with 55+ named files
- [x] Every FR mapped to specific file(s)
- [x] Growth features scaffolded, out of MVP scope

### Architecture Readiness Assessment

**Overall status:** READY FOR IMPLEMENTATION

**Confidence:** High

**Key strengths:**
- `HaWebSocketClient` interface + ported `WebSocketRepository` gives production-grade WS impl on day one without building from scratch — and the diff watcher keeps it current without manual repo watching
- Ported `Entity.kt` (1,283 lines of extension functions) covers all 10 entity domains — no entity modeling work in MVP
- SQLDelight cache enables ≤1s cold launch AND offline glanceability in one solution
- 4 expect/actual pairs cleanly isolate all platform divergence from `commonMain`
- V1 theme component contract is simultaneously the V1 product and V2 SDK reference implementation

**Areas for future work:**
- `ContextRule.sq` schema design (before Growth context engine story)
- V2 SDK versioning strategy (when component contract becomes public API)
- Growth widget data architecture revisit (app group SQLite vs HAKit REST) if widget staleness is user issue

### Implementation Handoff

**First story:** KMP Wizard scaffold → port `com.backpapp.hanative` + `HaNative` → commit Gradle structure → commit initial `ha-upstream-refs/` snapshots → add `NOTICE` file (Apache 2.0 attribution).

**Agent rules (non-negotiable):**
- No Ktor / SQLDelight / Android SDK imports in `domain/`
- No `var` in domain model fields
- No WebSocket event access above repository layer
- Always `{Feature}UiState` sealed class — no `isLoading: Boolean`
- HA entity domain strings verbatim (`light`, `switch`, etc.)
- Never hand-author `HaNativeDatabase.kt` — SQLDelight generates it from `.sq` files
- Wire `kotlinx.datetime` adapter in `EntityDomainAdapter.kt` explicitly
