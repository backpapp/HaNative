# Story 2.3: HapticEngine expect/actual

Status: review

## Story

As a developer,
I want a platform-bridged `HapticEngine` with 7 semantic patterns,
So that every entity control interaction fires entity-typed haptic feedback through the theme contract without any composable touching platform APIs directly.

## Acceptance Criteria

1. `platform/HapticFeedback.kt` (commonMain) declares `interface HapticEngine` with `fun fire(pattern: HapticPattern)`
2. `HapticPattern` is a sealed class with 7 variants: `ToggleOn`, `ToggleOff`, `StepperInc`, `StepperDec`, `ActionTriggered`, `ActionRejected`, `DashboardSwitch`
3. `AndroidHapticFeedback.kt` (androidMain) implements each pattern using `VibrationEffect` with distinct waveform + amplitude per pattern
4. `IosHapticFeedback.kt` (iosMain) implements via `UIImpactFeedbackGenerator` / `UINotificationFeedbackGenerator` per pattern type
5. `HapticEngine` is registered in `DataModule` (Koin) and exposed via `LocalHapticEngine` composition local — composables call `LocalHapticEngine.current.fire(pattern)`, never platform API directly

## Tasks / Subtasks

- [x] Task 1: Create `platform/HapticFeedback.kt` in commonMain — `HapticPattern` sealed class (7 variants), `HapticEngine` interface, `LocalHapticEngine` CompositionLocal (AC: 1, 2, 5)
- [x] Task 2: Create `AndroidHapticFeedback.kt` in androidMain — `AndroidHapticEngine` with `VibrationEffect` per pattern (AC: 3)
- [x] Task 3: Create `IosHapticFeedback.kt` in iosMain — `IosHapticEngine` with UIKit feedback generators per pattern (AC: 4)
- [x] Task 4: Wire `HapticEngine` into Koin `DataModule` via expect/actual platform modules (AC: 5)
- [x] Task 5: Provide `LocalHapticEngine` at composable tree root (MainActivity + MainViewController) (AC: 5)
- [x] Task 6: Add VIBRATE permission to `AndroidManifest.xml` (AC: 3)
- [x] Task 7: Build `:shared:testDebugUnitTest :androidApp:assembleDebug` — BUILD SUCCESSFUL (AC: all)

## Dev Notes

### Package
- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/HapticFeedback.kt`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AndroidHapticFeedback.kt`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/IosHapticFeedback.kt`

### Pattern Mapping
| Pattern | Android (VibrationEffect) | iOS |
|---|---|---|
| ToggleOn | oneShot(20ms, amp=180) | UIImpactFeedbackStyleMedium |
| ToggleOff | oneShot(15ms, amp=100) | UIImpactFeedbackStyleLight |
| StepperInc | oneShot(10ms, amp=200) | UIImpactFeedbackStyleLight |
| StepperDec | oneShot(10ms, amp=120) | UIImpactFeedbackStyleLight |
| ActionTriggered | oneShot(30ms, amp=255) | UINotificationFeedbackTypeSuccess |
| ActionRejected | waveform([0,20,60,20], [0,200,0,150], no-repeat) | UINotificationFeedbackTypeError |
| DashboardSwitch | oneShot(8ms, amp=80) | UIImpactFeedbackStyleRigid |

### Android API Level
- `VibrationEffect` requires API 26+; minSdk is 24 — fire is a no-op below API 26.
- `VibratorManager` (API 31+) used for vibrator service acquisition on API 31+; deprecated `Vibrator` used for 26–30.

### Koin Wiring
- `expect fun hapticEngineModule(): Module` in commonMain `DataModule.kt`
- `actual fun` implementations in androidMain and iosMain `di/DataModule.kt`
- Android: `single<HapticEngine> { AndroidHapticEngine(androidContext()) }`
- iOS: `single<HapticEngine> { IosHapticEngine() }`

### LocalHapticEngine Provision
- Provided via `CompositionLocalProvider` in `MainActivity` and `MainViewController`
- Retrieved from Koin via `remember { GlobalContext.get().get<HapticEngine>() }`

## Dev Agent Record

### Debug Log

### Completion Notes

- `expect fun hapticEngineModule()` in commonMain `DataModule.kt`; `actual fun` lives in separate `HapticEngineModule.kt` files (not `DataModule.kt`) to avoid duplicate JVM class name `DataModuleKt` — KMP includes both commonMain and platform sourceSets during Android compilation
- `VibrationEffect` is API 26+; `fire()` is a no-op on API 24–25 (minSdk = 24)
- `VibratorManager` acquired on API 31+; deprecated `Vibrator` service used for API 26–30
- `HapticEngine` retrieved in composable scope via `remember { GlobalContext.get().get<HapticEngine>() }` on Android; iOS uses `appKoin.get<HapticEngine>()` via `KoinHelper.appKoin` — `GlobalContext` is JVM-only in koin-core 4.x
- In KN 2.3.x, `UIImpactFeedbackStyle` and `UINotificationFeedbackType` are enum classes — values accessed as `UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium`, NOT importable as top-level symbols (`import platform.UIKit.UIImpactFeedbackStyleMedium` fails)

## File List

- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/HapticFeedback.kt` (new)
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AndroidHapticFeedback.kt` (new)
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/IosHapticFeedback.kt` (new)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt` (modified)
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/HapticEngineModule.kt` (new)
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/HapticEngineModule.kt` (new)
- `shared/src/commonTest/kotlin/com/backpapp/hanative/platform/HapticPatternTest.kt` (new)
- `androidApp/src/main/java/com/backpapp/hanative/MainActivity.kt` (modified)
- `shared/src/iosMain/kotlin/com/backpapp/hanative/MainViewController.kt` (modified)
- `shared/src/iosMain/kotlin/com/backpapp/hanative/KoinHelper.kt` (modified)
- `androidApp/src/main/AndroidManifest.xml` (modified)

## Change Log

- 2026-04-27: Story 2.3 implemented — HapticEngine expect/actual complete, BUILD SUCCESSFUL
