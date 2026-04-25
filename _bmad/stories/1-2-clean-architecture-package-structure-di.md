# Story 1.2: Clean Architecture Package Structure & DI

Status: done

## Story

As a developer,
I want the full Clean Architecture package structure and Koin DI wired up,
so that every subsequent story drops code into the correct layer with zero import rule violations.

## Acceptance Criteria

1. `commonMain` contains exactly: `domain/model/`, `domain/repository/`, `domain/usecase/`, `data/remote/dto/`, `data/local/adapter/`, `data/repository/`, `di/`, `ui/components/`, `ui/navigation/`, `platform/`
2. `DataModule`, `DomainModule`, `PresentationModule` exist (empty, no bindings yet) and are loaded in `HaNativeApplication.kt` (Android) and `iOSApp.swift` (iOS)
3. All core dependencies declared in `libs.versions.toml` compile clean (Ktor CIO + Darwin engines, SQLDelight 2.x, DataStore KMP, `kotlinx.serialization`, `kotlinx.datetime`, `kotlinx.coroutines`, `lifecycle-viewmodel` KMP)
4. A comment block in each empty `domain/` package documents the import enforcement rule: no Ktor/SQLDelight/Android SDK imports allowed
5. Project builds clean on Android with full dependency set resolved: `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest`

## Tasks / Subtasks

- [x] Task 1: Create `commonMain` package directory structure (AC: 1)
  - [x] Create `domain/model/` — add `.gitkeep` or empty `README` to track in git
  - [x] Create `domain/repository/` — same
  - [x] Create `domain/usecase/` — same
  - [x] Create `data/remote/dto/`
  - [x] Create `data/local/adapter/`
  - [x] Create `data/repository/`
  - [x] Create `di/`
  - [x] Create `ui/components/`
  - [x] Create `ui/navigation/`
  - [x] Create `platform/`
  - [x] All under base package `com/backpapp/hanative/` inside `shared/src/commonMain/kotlin/`

- [x] Task 2: Create empty Koin modules (AC: 2)
  - [x] `shared/src/commonMain/.../di/DataModule.kt` — `val dataModule = module { }` with import enforcement comment
  - [x] `shared/src/commonMain/.../di/DomainModule.kt` — same pattern
  - [x] `shared/src/commonMain/.../di/PresentationModule.kt` — same pattern

- [x] Task 3: Wire Koin in platform entry points (AC: 2)
  - [x] Create `androidApp/src/main/java/com/backpapp/hanative/HaNativeApplication.kt` — calls `startKoin { modules(dataModule, domainModule, presentationModule) }`
  - [x] Register `HaNativeApplication` in `AndroidManifest.xml` via `android:name`
  - [x] Update `iosApp/iosApp/iOSApp.swift` — call Koin init before scene body (see pattern below)

- [x] Task 4: Wire all core dependencies into `:shared/build.gradle.kts` (AC: 3)
  - [x] `commonMain` sourceset: `kotlinx-coroutines-core`, `kotlinx-serialization-json`, `kotlinx-datetime`, `ktor-client-core`, `ktor-client-websockets`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `sqldelight-runtime`, `sqldelight-coroutines`, `koin-core`, `datastore-preferences-core`, `lifecycle-viewmodel`, `navigation3-runtime`, `navigation3-ui`
  - [x] `androidMain` sourceset: `ktor-client-cio`, `sqldelight-android-driver`, `koin-android`
  - [x] `iosMain` sourceset: `ktor-client-darwin`, `sqldelight-native-driver`
  - [x] `:androidApp/build.gradle.kts`: add `androidx-core-ktx`, `androidx-activity-compose`, `koin-android`, `koin-compose`
  - [x] Run `./gradlew dependencies` to verify all deps resolve with no conflicts

- [x] Task 5: Add import enforcement comment blocks (AC: 4)
  - [x] In `domain/model/`, `domain/repository/`, `domain/usecase/`: add a `PackageInfo.kt` (or `DomainLayerRules.kt`) with the enforcement comment block
  - [x] Verify Detekt config (if present) enforces layer boundaries — skip if Detekt not yet configured

- [x] Task 6: Verify builds (AC: 5)
  - [x] `./gradlew :androidApp:assembleDebug` — must succeed
  - [x] `./gradlew :shared:testDebugUnitTest` — must succeed
  - [x] No unresolved references or dependency conflicts in build output

## Dev Notes

### CRITICAL: Prerequisite

Story 1.1 must be complete. This story adds structure to the wizard scaffold — it does NOT modify `libs.versions.toml` (all deps must already be declared there from Story 1.1).

### Full Package Tree

All paths are relative to `shared/src/commonMain/kotlin/com/backpapp/hanative/`:

```
domain/
  model/           ← HaEntity, Dashboard, DashboardCard, ContextRule (Story 1.3+)
  repository/      ← interfaces only (Story 1.3+)
  usecase/         ← one class per use case (Epic 3+)
data/
  remote/
    dto/           ← WS message DTOs (Story 1.3)
  local/
    adapter/       ← SQLDelight column adapters (Story 4.1)
  repository/      ← repository implementations (Story 3.3+)
di/
  DataModule.kt
  DomainModule.kt
  PresentationModule.kt
ui/
  components/      ← shared card Composables (Epic 4)
  navigation/      ← HaNativeNavHost.kt, Routes.kt (Story 1.4)
platform/          ← expect interfaces (Story 1.3+)
```

Story 1.2 creates the **empty directory structure + DI skeleton only**. No domain models, no repository interfaces, no use cases — those belong to Stories 1.3+.

### Koin Module Pattern

```kotlin
// di/DataModule.kt
package com.backpapp.hanative.di

import org.koin.dsl.module

val dataModule = module {
    // Bindings added in Story 3.x when data layer is implemented
}
```

Same pattern for `domainModule` and `presentationModule`.

### `HaNativeApplication.kt` Pattern

```kotlin
package com.backpapp.hanative

import android.app.Application
import com.backpapp.hanative.di.dataModule
import com.backpapp.hanative.di.domainModule
import com.backpapp.hanative.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class HaNativeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@HaNativeApplication)
            modules(dataModule, domainModule, presentationModule)
        }
    }
}
```

Register in `AndroidManifest.xml`:
```xml
<application
    android:name=".HaNativeApplication"
    ...>
```

### `iOSApp.swift` Pattern

```swift
import SwiftUI
import shared  // the KMP framework

@main
struct iOSApp: App {
    init() {
        KoinHelperKt.doInitKoin()  // thin expect/actual helper OR call startKoin directly
        // Alternatively: KoinInitializerKt.startKoin(...)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

The iOS Koin init approach depends on the KMP framework bridging. A common pattern is to create a `KoinHelper.kt` in `iosMain`:

```kotlin
// shared/src/iosMain/kotlin/com/backpapp/hanative/KoinHelper.kt
import com.backpapp.hanative.di.dataModule
import com.backpapp.hanative.di.domainModule
import com.backpapp.hanative.di.presentationModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(dataModule, domainModule, presentationModule)
    }
}
```

Then call `KoinHelperKt.doInitKoin()` from `iOSApp.swift` (or simply `HaNativeKt.initKoin()`).

### Domain Layer Import Enforcement Comment

```kotlin
// PackageInfo.kt in domain/model/, domain/repository/, domain/usecase/
package com.backpapp.hanative.domain.model  // (adjust per package)

/*
 * DOMAIN LAYER IMPORT RULES (NON-NEGOTIABLE)
 *
 * This package is pure Kotlin. The following imports are FORBIDDEN:
 *   - io.ktor.*
 *   - app.cash.sqldelight.*
 *   - android.*
 *   - androidx.*
 *   - platform.*  (use interfaces in domain/repository/ instead)
 *
 * Domain models: immutable data classes, all `val`, no platform deps.
 * Repositories: interfaces only, no implementations.
 * Use cases: pure Kotlin, depend only on domain interfaces.
 */
```

### Dependency Wiring Reference

ktor-client-cio (Android) and ktor-client-darwin (iOS) are **engine** implementations — they live in `androidMain`/`iosMain` sourcesets respectively, not `commonMain`. The `ktor-client-core` in `commonMain` provides the `HttpClient` interface.

SQLDelight driver split: `sqldelight-android-driver` in `androidMain`, `sqldelight-native-driver` in `iosMain`. The actual database creation requires a platform expect/actual `DriverFactory` — this is scaffolded in Story 4.1, not here.

DataStore KMP (`androidx.datastore:datastore-preferences-core`) supports KMP since 1.1.x. Uses `commonMain` artifact only — no platform splits needed.

### What NOT to Create in This Story

Do NOT create: `HaEntity.kt`, any repository interfaces, any use case classes, `HaNativeNavHost.kt`, `Routes.kt`, `CredentialStore.kt`, or any SQLDelight `.sq` files. Those belong to Stories 1.3, 1.4, and Epic 3+.

Do NOT add Detekt/ktlint config — that belongs to Story 1.5 CI setup.

### Architecture Enforcement (from `_bmad/outputs/architecture.md`)

- Package root: `com.backpapp.hanative.{layer}.{feature}`
- No `var` in domain model fields (enforced from Story 1.3)
- Koin modules: `dataModule`, `domainModule`, `presentationModule` — one per layer
- `CredentialStore`, `HapticFeedback`, `ServerDiscovery`, `AppLifecycleObserver` are expect/actual — created in Story 1.3+, NOT here

### References

- Package structure: `_bmad/outputs/architecture.md` → "Structure Patterns"
- Koin module design: `_bmad/outputs/architecture.md` → "Communication Patterns"
- Full project tree: `_bmad/outputs/architecture.md` → "Complete Project Tree"
- Story 1.1 prerequisite: `_bmad/stories/1-1-kmp-wizard-project-scaffold.md`

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- navigation3 version `2.9.0-alpha07` not published to Google Maven → fixed to stable `1.0.0`.
- navigation3-runtime/ui have no KMP/iOS variant → moved from `shared/commonMain` to `androidApp/build.gradle.kts` (Android-only library; actual nav scaffolding deferred to Story 1.4).

### Completion Notes List

- All 10 commonMain package dirs created under `shared/src/commonMain/kotlin/com/backpapp/hanative/`. Empty dirs tracked via `.gitkeep`. Domain dirs get `PackageInfo.kt` instead of `.gitkeep`.
- Three empty Koin modules (`dataModule`, `domainModule`, `presentationModule`) created in `di/` — no bindings.
- Android: `HaNativeApplication.kt` created, registered in `AndroidManifest.xml` via `android:name`.
- iOS: `KoinHelper.kt` in `iosMain`, `iOSApp.swift` calls `KoinHelperKt.doInitKoin()` before scene body. (`init` prefix → `do` prefix in ObjC bridge.)
- All V1 deps wired into `shared/build.gradle.kts` (commonMain/androidMain/iosMain) and `androidApp/build.gradle.kts`.
- Import enforcement comment block in `PackageInfo.kt` in each domain sub-package.
- `./gradlew :androidApp:assembleDebug` ✅ `./gradlew :shared:testDebugUnitTest` ✅

### Change Log

- 2026-04-25: Story 1.2 implemented — Clean Architecture package structure + Koin DI skeleton. Fixed navigation3 version to 1.0.0.

### File List

- shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/.gitkeep
- shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/PackageInfo.kt
- shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/.gitkeep
- shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/PackageInfo.kt
- shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/.gitkeep
- shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/PackageInfo.kt
- shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/dto/.gitkeep
- shared/src/commonMain/kotlin/com/backpapp/hanative/data/local/adapter/.gitkeep
- shared/src/commonMain/kotlin/com/backpapp/hanative/data/repository/.gitkeep
- shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt
- shared/src/commonMain/kotlin/com/backpapp/hanative/di/DomainModule.kt
- shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt
- shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/.gitkeep
- shared/src/commonMain/kotlin/com/backpapp/hanative/ui/navigation/.gitkeep
- shared/src/commonMain/kotlin/com/backpapp/hanative/platform/.gitkeep
- shared/src/iosMain/kotlin/com/backpapp/hanative/KoinHelper.kt
- shared/build.gradle.kts
- androidApp/build.gradle.kts
- androidApp/src/main/java/com/backpapp/hanative/HaNativeApplication.kt
- androidApp/src/main/AndroidManifest.xml
- iosApp/iosApp/iOSApp.swift
- gradle/libs.versions.toml

### Review Findings

- [x] [Review][Patch] navigation3-runtime/ui in shared commonMain — confirmed already moved to androidApp/build.gradle.kts by dev agent; shared commonMain clean. [shared/build.gradle.kts, androidApp/build.gradle.kts]
- [x] [Review][Patch] startKoin double-init guard missing — added GlobalContext.getOrNull() guard in HaNativeApplication.kt and KoinHelper.kt. [HaNativeApplication.kt, KoinHelper.kt]
- [x] [Review][Patch] .gitkeep present in domain/ dirs alongside PackageInfo.kt — deleted domain/model/.gitkeep, domain/repository/.gitkeep, domain/usecase/.gitkeep. [shared/src/commonMain/kotlin/com/backpapp/hanative/domain/]
- [x] [Review][Defer] koin.android declared in both androidApp and shared:androidMain — spec-compliant, Gradle deduplicates; no action needed now — deferred, pre-existing
- [x] [Review][Defer] iOS Koin init in App.init() before UIApplication ready — safe with empty modules now; latent risk when modules bind platform APIs — deferred, pre-existing
- [x] [Review][Defer] DataStore iOS storage path wiring missing — no platform-specific OkioStorage config; safe now (not instantiated); required when DataStore is used in a later story — deferred, pre-existing
- [x] [Review][Defer] koin-compose not in shared module — needed only when shared composables use DI injection; not yet required — deferred, pre-existing
- [x] [Review][Defer] SQLDelight plugin not applied in shared/build.gradle.kts — intentional scaffold; .sq files and plugin wiring come in a later story — deferred, pre-existing
- [x] [Review][Defer] lifecycle-viewmodel:2.9.0 iOS/native KMP artifact unverified — iOS build not run in this story; verify when iOS build pipeline is added — deferred, pre-existing
- [x] [Review][Defer] ktor-client-cio TLS gaps on Android API < 29 — latent WebSocket risk; evaluate and switch to OkHttp engine when networking implemented in a later story — deferred, pre-existing
- [x] [Review][Defer] iOS Koin init on main thread — deadlock risk if future module factories use runBlocking or I/O; safe with empty modules now — deferred, pre-existing
