# Story 1.1: KMP Wizard Project Scaffold

Status: review

## Story

As a developer,
I want a correctly structured three-module KMP + CMP project,
so that all subsequent epics build on a valid scaffold with the right Gradle configuration from day one.

## Acceptance Criteria

1. Project has three modules: `:shared`, `:androidApp`, `:iosApp`
2. `applicationId = "com.backpapp.hanative"` and `rootProject.name = "HaNative"` are set
3. Generated stub code (Hello World screens) replaced with empty placeholder composables
4. `libs.versions.toml` version catalog created with all V1 dependency versions (Kotlin 2.3.20, CMP 1.10.x, AGP 8.8+, Ktor 3.x, SQLDelight 2.x, Koin 4.x, Navigation 3)
5. Project builds successfully: `./gradlew :androidApp:assembleDebug :shared:testDebugUnitTest` passes with no errors
6. iOS scheme builds via `xcodebuild` with no errors

## Tasks / Subtasks

- [x] Task 1: Generate KMP Wizard project structure (AC: 1, 2)
  - [x] Generate project at kmp.jetbrains.com — targets: Android + iOS, "Share UI" enabled — OR manually create three-module Gradle structure matching the wizard output
  - [x] Port `applicationId = "com.backpapp.hanative"` into `:androidApp/build.gradle.kts`
  - [x] Confirm `rootProject.name = "HaNative"` in `settings.gradle.kts`
  - [x] `settings.gradle.kts` includes `:shared`, `:androidApp`, `:iosApp` (remove bare `:app`)
  - [x] Root `build.gradle.kts` is plugins-only (no allprojects repositories)
  - [x] `gradle.properties` sets `kotlin.code.style=official` and `android.useAndroidX=true`

- [x] Task 2: Replace stub code with empty placeholder composables (AC: 3)
  - [x] In `:shared/commonMain`, replace generated Hello World Composable with `HaNativePlaceholderScreen` — an empty `Box(Modifier.fillMaxSize())` with a `TODO("placeholder")` comment
  - [x] In `:androidApp/src/main`, `MainActivity.kt` hosts `setContent { HaNativePlaceholderScreen() }` — no stub content
  - [x] In `:iosApp`, `ContentView.swift` hosts `ComposeUIViewController { HaNativePlaceholderScreen() }` — no stub content
  - [x] Delete all generated stub resource files that reference Hello World content

- [x] Task 3: Create complete `libs.versions.toml` version catalog (AC: 4)
  - [x] Add all versions, libraries, and plugins entries per the spec below
  - [x] Verify no duplicate keys, valid TOML syntax

- [x] Task 4: Verify builds pass (AC: 5, 6)
  - [x] Run `./gradlew :androidApp:assembleDebug` — must succeed
  - [x] Run `./gradlew :shared:testDebugUnitTest` — must succeed (no tests yet, just must not fail)
  - [x] Run iOS build: `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug build` — must succeed

## Dev Notes

### CRITICAL: Current Project State

The current repo is a **bare Android-only project** — single `:app` module, no KMP, no iOS. The `settings.gradle.kts` currently includes `:app` only. `libs.versions.toml` has Android-only deps.

**This story replaces the entire project structure.** The `:app` module is discarded. The wizard output (`:shared`, `:androidApp`, `:iosApp`) is the new structure.

Two implementation paths:
1. **Preferred:** Generate fresh at kmp.jetbrains.com (Android + iOS, "Share UI" = CMP enabled), then port `applicationId` and `rootProject.name`. Discard Hello World stub content.
2. **Manual fallback:** Hand-craft the three-module structure. High Gradle expertise required. See architecture.md "Starter Evaluation" section for exact module layout.

### `libs.versions.toml` Spec

```toml
[versions]
agp = "9.1.1"
kotlin = "2.3.20"
compose-multiplatform = "1.10.0"
ktor = "3.1.3"
sqldelight = "2.0.2"
koin = "4.0.0"
navigation3 = "2.9.0-alpha07"
coroutines = "1.9.0"
serialization = "1.8.1"
datetime = "0.6.1"
datastore = "1.1.2"
lifecycle-viewmodel = "2.9.0"
coreKtx = "1.16.0"
activityCompose = "1.10.1"
```

> **Developer note:** The architecture doc specifies Kotlin 2.3.20 and CMP 1.10.x as target versions. Verify the latest stable patch releases at the time of implementation — use exact versions, not range constraints. The versions above are the minimum targets; newer stable patch releases are acceptable.

```toml
[libraries]
# Kotlin
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }

# Ktor (KMP)
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }

# SQLDelight (KMP)
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }

# Koin (KMP)
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }

# DataStore (KMP)
datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }

# Compose Navigation 3
navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }

# Lifecycle ViewModel (KMP)
lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle-viewmodel" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle-viewmodel" }

# Android platform
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
```

```toml
[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

### Module Build Files

**`:shared/build.gradle.kts`** must apply:
- `kotlin("multiplatform")`
- `org.jetbrains.compose`
- `org.jetbrains.kotlin.plugin.compose`
- `org.jetbrains.kotlin.plugin.serialization`

Kotlin targets: `androidTarget()` and `iosX64()` + `iosArm64()` + `iosSimulatorArm64()`.

**`:androidApp/build.gradle.kts`** must apply:
- `com.android.application`
- `kotlin("android")`

**`:iosApp`** is a standard Xcode project — no Gradle build file.

### Placeholder Composable Pattern

```kotlin
// shared/src/commonMain/kotlin/com/backpapp/hanative/HaNativePlaceholderScreen.kt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HaNativePlaceholderScreen() {
    Box(modifier = Modifier.fillMaxSize())
}
```

### Architecture Enforcement (Non-Negotiable)

From `_bmad/outputs/architecture.md`:
- Three modules only: `:shared`, `:androidApp`, `:iosApp`
- `applicationId = "com.backpapp.hanative"`
- Package root: `com.backpapp.hanative`
- No Ktor/SQLDelight/Android SDK imports in `domain/` (enforced from Story 1.2 onward)
- Never hand-author `HaNativeDatabase.kt` — SQLDelight generates it from `.sq` files (relevant from Story 4.1)

### What NOT to Create in This Story

Do NOT create any of: domain models, Koin modules, repository interfaces, SQLDelight schemas, navigation routes, or platform expect/actual files. Those belong to Stories 1.2–1.4. Only scaffold structure here.

### References

- Architecture starter evaluation: `_bmad/outputs/architecture.md` → "Starter Template Evaluation"
- Version targets: `_bmad/outputs/architecture.md` → "Architecture Validation Results"
- Module layout: `_bmad/outputs/architecture.md` → "Complete Project Tree"
- Epic goals: `_bmad/outputs/epics.md` → "Epic 1: Project Foundation & CI"

## Dev Agent Record

### Agent Model Used

GPT-5

### Debug Log References

- `env GRADLE_USER_HOME=/tmp/hanative-gradle-home ./gradlew :androidApp:assembleDebug`
- `env GRADLE_USER_HOME=/tmp/hanative-gradle-home ./gradlew :shared:testDebugUnitTest`
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug build`

### Completion Notes List

- Replaced the Android-only starter with the three-module `:shared`, `:androidApp`, `:iosApp` scaffold and removed the bare `:app` module from Gradle settings.
- Added shared Compose placeholder entry points for Android and iOS, including a Swift host app and Xcode project wiring for the generated `shared.framework`.
- Updated the version catalog to the Story 1.1 dependency baseline and aligned the shared module with the current Android KMP plugin model while keeping the story's exact validation commands working.
- Added `CADisableMinimumFrameDurationOnPhone` to `Info.plist`, moved the framework build phase ahead of Swift sources, and raised the iOS deployment target to `17.2` so the iOS app builds and launches cleanly.
- Added a compatibility `testDebugUnitTest` task alias that delegates to `testAndroidHostTest` so the story's exact verification command passes under the current Android-KMP plugin layout.

### File List

- settings.gradle.kts
- build.gradle.kts
- gradle.properties
- gradle/libs.versions.toml
- androidApp/build.gradle.kts
- androidApp/src/main/AndroidManifest.xml
- androidApp/src/main/java/com/backpapp/hanative/MainActivity.kt
- shared/build.gradle.kts
- shared/src/androidMain/AndroidManifest.xml
- shared/src/commonMain/kotlin/com/backpapp/hanative/HaNativePlaceholderScreen.kt
- shared/src/commonTest/kotlin/com/backpapp/hanative/PlaceholderSmokeTest.kt
- shared/src/iosMain/kotlin/com/backpapp/hanative/MainViewController.kt
- iosApp/iosApp.xcodeproj/project.pbxproj
- iosApp/iosApp/ContentView.swift
- iosApp/iosApp/Info.plist
- iosApp/iosApp/iOSApp.swift
- iosApp/build.gradle.kts (deleted)

### Change Log

- 2026-04-25: Replaced the Android-only scaffold with the KMP/CMP three-module project, added platform placeholder hosts, aligned iOS build settings, and verified Story 1.1 build commands.
