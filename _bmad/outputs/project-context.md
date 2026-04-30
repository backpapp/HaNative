---
project_name: 'HaNative'
user_name: 'Jeremy Blanchard'
date: '2026-04-28'
sections_completed: ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'code_quality', 'critical_rules']
status: complete
rule_count: 68
optimized_for_llm: true
---

# Project Context for AI Agents

_Critical rules and patterns AI agents must follow when implementing code. Focus on unobvious details agents might otherwise miss._

---

## Technology Stack & Versions

### Core Platform
- Kotlin 2.3.20 | Compose Multiplatform 1.10.0
- AGP 9.1.1 | Gradle 8.x | JVM target 17
- Android: minSdk 24, compileSdk/targetSdk 36
- iOS targets: iosArm64, iosX64, iosSimulatorArm64
- **Compose compiler is embedded in Kotlin plugin — no separate compose-compiler version entry**

### Key Dependencies (exact versions)
| Library | Version | Notes |
|---------|---------|-------|
| Ktor client-core | 3.1.3 | |
| Ktor client-cio | 3.1.3 | **androidMain ONLY** |
| Ktor client-darwin | 3.1.3 | **iosMain ONLY — never use CIO on iOS** |
| Ktor websockets, content-negotiation, serialization-json | 3.1.3 | commonMain |
| SQLDelight runtime, coroutines, android-driver, native-driver | 2.0.2 | No .sq schema files exist yet — must be created before use |
| DataStore Preferences KMP | 1.1.2 | Use `androidx.datastore:datastore-preferences-core` (KMP artifact) — NOT `datastore-preferences` (Android-only) |
| Koin core, android, compose | 4.0.0 | |
| Compose Navigation 3 (JetBrains) | 1.0.0-alpha06 | `org.jetbrains.androidx.navigation` — **NOT** `androidx.navigation` |
| kotlinx-serialization-json | 1.8.1 | |
| kotlinx-coroutines-core + test | 1.9.0 | |
| kotlinx-datetime | 0.6.1 | |
| lifecycle-viewmodel (KMP) | 2.9.0 | |
| lifecycle-runtime-compose | 2.9.0 | |
| material3-window-size-class | 1.3.1 | |
| security-crypto (**androidMain only**) | 1.1.0-alpha06 | Alpha — do not downgrade to 1.0.0 |
| core-ktx | 1.16.0 | |
| activity-compose | 1.10.1 | |

### Compiler Flags
- `-Xexpect-actual-classes` required (enables strict expect/actual checking)
- iOS framework: `baseName = "shared"`, `isStatic = true`

---

## Language-Specific Rules

### Kotlin / KMP Patterns

**expect/actual**
- Every platform abstraction lives behind `expect interface` or `expect class` in `commonMain`
- `actual` implementations in `androidMain` and `iosMain` — never share platform impl across source sets
- `-Xexpect-actual-classes` active — missing actuals = build failure
- Platform DI modules (`CredentialStoreModule.kt`, `SettingsDataStoreModule.kt`, `HapticEngineModule.kt`) exist in BOTH `androidMain/di/` and `iosMain/di/` — always create both when adding a platform module

**commonMain purity**
- NEVER import `android.*`, `androidx.*`, or `java.*` in `commonMain` source files
- Build passes on Android but fails on iOS — IDE autocomplete will offer these, refuse them
- Use `kotlinx-datetime` instead of `java.time.*`; `kotlin.random.Random` instead of `java.util.UUID`

**Kotlin/Native (iosMain) constraints**
- No `java.*` imports anywhere in iosMain
- Kotlin/Native: no `synchronized {}` — use `Mutex` for shared mutable state
- CF/Security framework: always use `memScoped { }` + `allocArray<UByteVar>(size)` for byte buffers
- NEVER use `ByteArray.usePinned` with `CFDataCreate` — causes receiver type mismatch
- See `IosCredentialStore.kt` as reference implementation for all CF API patterns

**Serialization**
- All DTOs use `@Serializable` (kotlinx.serialization) — never Gson or Moshi
- `MapAnySerializer` in `data/remote/` handles `Map<String, Any?>` — use it, don't rewrite
- DTOs: `{Entity}Dto` suffix; domain models: no suffix

**Coroutines & state**
- `StateFlow` (not `LiveData`) for all observable state in `commonMain`
- `collectAsStateWithLifecycle()` in Composables — NOT `collectAsState()`
- `stateIn()` / `shareIn()` ONLY in ViewModel — never inside a Composable
- `viewModelScope` import: `androidx.lifecycle.viewModelScope` — valid in `commonMain` (KMP lifecycle)
- `Mutex` for shared mutable state — `synchronized {}` unavailable in Kotlin/Native

**Null safety**
- Domain models: all fields `val`, non-nullable unless business rule requires otherwise
- DTOs: nullable fields with `@SerialName` — HA API fields are frequently optional

**OptIn annotations**
- Navigation 3 alpha + some lifecycle/Material3 APIs require `@OptIn` — add annotations, don't change imports to chase red underlines

---

## Framework-Specific Rules

### Clean Architecture Layering
- Domain layer (`domain/`): pure Kotlin only — no Ktor, no SQLDelight, no platform imports
- Repository interfaces in `domain/repository/`; implementations in `data/`
- UseCases in `domain/usecase/` — one public function, no constructor-injected coroutine scope
- ViewModels in `ui/{feature}/` — never in `domain/` or `data/`

### Compose Multiplatform

**Component structure**
- Screen entry points: `{Feature}Screen.kt` — receives ViewModel via `koinViewModel()`
- All shared Composables in `commonMain` — platform-specific only when expect/actual unavoidable
- Previews in `androidApp/preview/` only — never in `shared/`

**Performance**
- Card Composables observe ONLY their own entity's `StateFlow` slice — no full-dashboard recomposition
- `key()` blocks on all card list items
- `remember` / `derivedStateOf` for derived UI state — never recompute in Composable body

**Theme**
- Always wrap in `HaNativeTheme {}` — never call `MaterialTheme` directly
- Theme wrapping at `HaNativeNavHost` level — not per-screen
- Motion constants from `Motion.kt` — never hardcode animation specs
- `LocalWindowSizeClass.current` is the **custom** `CompositionLocal` in `LocalWindowSizeClass.kt` — NOT `androidx.compose.material3.windowsizeclass.LocalWindowSizeClass`

### Compose Navigation 3
- Routes: `@Serializable data object` (no args) or `@Serializable data class` (with args) in `Routes.kt`
- Single `NavHost` in `HaNativeNavHost.kt` — no nested NavHosts
- Deep link handling at platform entry points — not in NavHost
- Import: `org.jetbrains.androidx.navigation.*` — NOT `androidx.navigation.*`

### Koin DI
- Module structure: `DataModule`, `DomainModule`, `PresentationModule` in `commonMain/di/` + platform modules in `androidMain/di/` and `iosMain/di/`
- `startKoin {}` called ONCE at platform entry: `HaNativeApplication.kt` (Android), `KoinHelper.kt` (iOS)
- **When adding a new Koin module: wire in BOTH entry points** — missing one = runtime crash on that platform only
- ViewModels: use `viewModelOf(::MyViewModel)` DSL in module definition (Koin 4.0 KMP)
- `koinViewModel()` import: `org.koin.compose.viewmodel.koinViewModel` — NOT `org.koin.androidx.compose.koinViewModel`
- `single {}` for repositories/clients; `factory {}` for non-ViewModel factories

### ViewModel / State Pattern
- One ViewModel per screen/feature
- Expose: `val uiState: StateFlow<{Feature}UiState>` (read-only)
- `sealed class {Feature}UiState`: `Loading`, `Success(data)`, `Error(message)` variants
- One-shot events via `Channel<{Feature}Effect>` exposed as `Flow` — never put navigation in UiState
- Context engine: MVI `sealed class ContextAction` + pure reducer — no external MVI library

---

## Testing Rules

### Test Framework & Task
- `kotlin.test` for all tests — never JUnit4/5 directly in `commonTest`
- `kotlinx-coroutines-test` for async testing
- Primary test task: `./gradlew :shared:testAndroidHostTest`
- Alias: `testDebugUnitTest` (registered in build.gradle.kts)
- **`testAndroidHostTest` runs on JVM — does NOT exercise Kotlin/Native code paths**
- **iOS `actual` implementations (CF API, Keychain) untested until iosSimulator build**

### Test Organization
```
shared/src/commonTest/kotlin/com/backpapp/hanative/
└── {layer}/{feature}/{Subject}Test.kt
```
- File naming: `{Subject}Test.kt` — not `Tests.kt`, not `Spec.kt`
- Mirror source structure: `data/remote/`, `domain/model/`, `platform/`, `ui/`
- Tests in `commonTest` — not `androidTest` unless testing Android-specific impl

### TDD Pattern (established in Story 3.1)
- Write failing `commonTest` before implementing
- Tests against `expect interface` compile immediately via fake — RED phase valid before `actual` impls exist
- Follow `FakeCredentialStore` pattern for all new platform interface fakes

### What to Test
- Domain models: invariants and constraints
- Serializers: JSON round-trip (see `MapAnySerializerTest.kt`)
- Platform abstractions: contract via `Fake{Interface}.kt` in `commonTest`
- ViewModels: UiState transitions via `runTest {}` + manual collect

### Mocking
- No mocking frameworks — fakes only (`Fake{Interface}.kt` in `commonTest`)
- `FakeCredentialStore` is the established pattern — follow it
- Never mock `EncryptedSharedPreferences` or Keychain types directly

### Coroutine Testing
- `runTest {}` for all coroutine tests
- `StateFlow` with `SharingStarted.WhileSubscribed` won't emit in `runTest` without active collector — use `SharingStarted.Eagerly` in test scope or collect before asserting
- `turbine` library NOT in version catalog — do not use `app.cash.turbine` until added

### Security in Tests
- Test fakes must NOT log credential values — `FakeCredentialStore` stores in memory only, no `println`/`Log` with token contents
- AC5 log-leak audit applies to all storage + network implementations: grep for token/URL values in log calls before committing

---

## Code Quality & Style Rules

### Naming Conventions
- Functions/variables: `camelCase`
- Classes/objects/Composables: `PascalCase`
- Constants: `SCREAMING_SNAKE_CASE`
- Interfaces: no `I` prefix — `HaWebSocketClient` not `IHaWebSocketClient`
- Implementations: `Impl` suffix or descriptive prefix — `KtorHaWebSocketClient`, `AndroidCredentialStore`
- Test files: `{Subject}Test.kt` (singular)
- Package: `com.backpapp.hanative.{layer}.{feature}`

### File Organization
- One top-level class/object/interface per file; filename matches declaration name
- Screen + ViewModel in same feature package: `ui/{feature}/{Feature}Screen.kt` + `{Feature}ViewModel.kt`
- Platform files: identical filename across `commonMain`/`androidMain`/`iosMain` for expect/actual pairs
- No wildcard imports

### Code Style
- No comments unless WHY is non-obvious (hidden constraint, workaround, invariant)
- No multi-line docstrings or block comment headers
- All `val` in domain models and data classes — `var` only where mutation is the business requirement
- Sealed classes for exhaustive state — no open-ended enums for UI state variants
- No unused parameters, no `_` suppression without explanation

### HA Entity Domains
- Entity domain strings are verbatim HA API values — never rename or transform:
  `light`, `switch`, `climate`, `cover`, `media_player`, `sensor`, `binary_sensor`,
  `input_boolean`, `input_select`, `script`, `scene`, `automation`
- Card Composable filenames mirror entity domain: `LightCard.kt`, `SwitchCard.kt`, etc.

### Dependency Rules (layer boundaries)
- `commonMain` may import: `kotlinx.*`, `androidx.lifecycle.*`, `org.jetbrains.androidx.navigation.*`, `io.ktor.*`, `app.cash.sqldelight.*`, `org.koin.*`
- `commonMain` must NOT import: `android.*`, `androidx.*` (except lifecycle/datastore/nav allowlist), `java.*`, `platform.*`
- `androidMain` may import anything; `iosMain` may import `platform.*` (Kotlin/Native interop)

---

## Critical Don't-Miss Rules

### Anti-Patterns

**KMP build traps**
- Adding `android.*` import to `commonMain` → Android build green, iOS build fails silently
- Using `ktor-client-cio` in `iosMain` → iOS link failure (no error until link time)
- Using `androidx.navigation` instead of `org.jetbrains.androidx.navigation` → compile error on iOS
- Calling `startKoin {}` more than once → `KoinAlreadyStartedException` at runtime
- Adding a Koin module in `commonMain/di/` without wiring in both platform entry points → runtime crash on one platform only

**Compose traps**
- `collectAsState()` instead of `collectAsStateWithLifecycle()` → lifecycle leaks
- `stateIn()` inside a Composable → new coroutine on every recomposition
- Hardcoded animation durations/easings instead of `Motion.kt` constants → breaks theme contract
- `MaterialTheme.*` direct access instead of `HaNativeTheme {}` wrapper → wrong tokens

**Serialization traps**
- `Any` type in `@Serializable` class without `MapAnySerializer` → runtime crash on unknown HA field types
- Missing `@SerialName` on DTO fields → breaks if HA API uses snake_case (it does)

### Security Rules
- Never log auth tokens, server URLs, or credential values — production or test code
- `CredentialStore` is the ONLY permitted storage for auth tokens — no `SharedPreferences`, no `UserDefaults`, no hardcoded fallback
- Entity state cache (SQLDelight) encryption is **not yet implemented** — do not assume it's wired
- No biometric gate in MVP — deferred, do not add

### Architecture Boundaries (do not cross)
- Widget surfaces (Glance/WidgetKit) live in `:androidApp`/`:iosApp` — NOT in `:shared`
- CMP does not cover widget surfaces — no Compose Multiplatform for Glance or WidgetKit
- REST API only for initial auth token exchange — all entity control via WebSocket `call_service`
- No per-screen entity fetching — entity state is single source of truth in shared layer, UI observes flows

### Performance Gotchas
- Entity state updates must not trigger full dashboard recomposition — card granularity required
- No blocking network calls on app startup — cached state must be available before WebSocket connects
- Dashboard switch target: ≤200ms — no layout inflation on context switch
- Spring physics on dashboard transitions (FR24b) — use `Motion.kt` specs, not `tween()`

---

## Usage Guidelines

**For AI Agents:** Read this file before implementing any code. Follow ALL rules exactly. When in doubt, prefer the more restrictive option.

**For Humans:** Keep lean. Update when tech stack changes. Remove rules that become obvious over time.

_Last Updated: 2026-04-28_
