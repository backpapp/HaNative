# Story 1.2: Clean Architecture Package Structure & DI

Status: ready-for-dev

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

- [ ] Task 1: Create `commonMain` package directory structure (AC: 1)
  - [ ] Create `domain/model/` — add `.gitkeep` or empty `README` to track in git
  - [ ] Create `domain/repository/` — same
  - [ ] Create `domain/usecase/` — same
  - [ ] Create `data/remote/dto/`
  - [ ] Create `data/local/adapter/`
  - [ ] Create `data/repository/`
  - [ ] Create `di/`
  - [ ] Create `ui/components/`
  - [ ] Create `ui/navigation/`
  - [ ] Create `platform/`
  - [ ] All under base package `com/backpapp/hanative/` inside `shared/src/commonMain/kotlin/`

- [ ] Task 2: Create empty Koin modules (AC: 2)
  - [ ] `shared/src/commonMain/.../di/DataModule.kt` — `val dataModule = module { }` with import enforcement comment
  - [ ] `shared/src/commonMain/.../di/DomainModule.kt` — same pattern
  - [ ] `shared/src/commonMain/.../di/PresentationModule.kt` — same pattern

- [ ] Task 3: Wire Koin in platform entry points (AC: 2)
  - [ ] Create `androidApp/src/main/java/com/backpapp/hanative/HaNativeApplication.kt` — calls `startKoin { modules(dataModule, domainModule, presentationModule) }`
  - [ ] Register `HaNativeApplication` in `AndroidManifest.xml` via `android:name`
  - [ ] Update `iosApp/iosApp/iOSApp.swift` — call Koin init before scene body (see pattern below)

- [ ] Task 4: Wire all core dependencies into `:shared/build.gradle.kts` (AC: 3)
  - [ ] `commonMain` sourceset: `kotlinx-coroutines-core`, `kotlinx-serialization-json`, `kotlinx-datetime`, `ktor-client-core`, `ktor-client-websockets`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `sqldelight-runtime`, `sqldelight-coroutines`, `koin-core`, `datastore-preferences-core`, `lifecycle-viewmodel`, `navigation3-runtime`, `navigation3-ui`
  - [ ] `androidMain` sourceset: `ktor-client-cio`, `sqldelight-android-driver`, `koin-android`
  - [ ] `iosMain` sourceset: `ktor-client-darwin`, `sqldelight-native-driver`
  - [ ] `:androidApp/build.gradle.kts`: add `androidx-core-ktx`, `androidx-activity-compose`, `koin-android`, `koin-compose`
  - [ ] Run `./gradlew dependencies` to verify all deps resolve with no conflicts

- [ ] Task 5: Add import enforcement comment blocks (AC: 4)
  - [ ] In `domain/model/`, `domain/repository/`, `domain/usecase/`: add a `PackageInfo.kt` (or `DomainLayerRules.kt`) with the enforcement comment block
  - [ ] Verify Detekt config (if present) enforces layer boundaries — skip if Detekt not yet configured

- [ ] Task 6: Verify builds (AC: 5)
  - [ ] `./gradlew :androidApp:assembleDebug` — must succeed
  - [ ] `./gradlew :shared:testDebugUnitTest` — must succeed
  - [ ] No unresolved references or dependency conflicts in build output

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

### Completion Notes List

### File List
