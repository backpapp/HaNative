# Story 2.2: Motion Constants

Status: review

## Story

As a developer,
I want all spring physics and animation constants defined in a single `Motion.kt` file,
So that every animation in the app is authored from theme contract values, not per-screen magic numbers.

## Acceptance Criteria

1. `Motion.kt` created in `ui/theme/`
2. Defines all 6 named animation specs: `dashboardTransition` (`tween(200ms, EaseInOut)` cross-fade), `bottomSheetOpen`/`bottomSheetDismiss` (spring `stiffness=400, dampingRatio=0.8`), `cardPress` (spring `stiffness=600, dampingRatio=0.7`), `entityStateChange` (`tween(200ms, EaseInOut)`), `snapBackRejection` (spring `stiffness=500, dampingRatio=0.6`), `staleIndicatorFade` (`tween(300ms)`)
3. Each constant has a one-line comment stating its semantic justification
4. No composable outside `ui/theme/` hardcodes animation durations, stiffness, or damping values — all reference `Motion.*`

## Tasks / Subtasks

- [x] Task 1: Create `Motion.kt` in `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/` with all 6 specs and semantic comments (AC: 1–3)
- [x] Task 2: Build `:shared:testDebugUnitTest :androidApp:assembleDebug` — BUILD SUCCESSFUL (AC: all)

## Dev Notes

### Package
`shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Motion.kt`

### Animation Types
- `tween` specs typed as `TweenSpec<Float>` — used for alpha/offset cross-fades
- `spring` specs typed as `SpringSpec<Float>` — used for physics-based gesture response
- `FastOutSlowInEasing` used for all tween easing (M3 standard EaseInOut equivalent in Compose)

### AC 4 Verification
No composables exist outside `ui/theme/` yet that hardcode animation values — constraint is forward-looking for all future card/screen composables.

## Dev Agent Record

### Completion Notes

- `FastOutSlowInEasing` is the Compose equivalent of `EaseInOut` — imported from `androidx.compose.animation.core`
- `SpringSpec<Float>` and `TweenSpec<Float>` explicit return types ensure `Motion.*` constants are callable without reified type inference at call sites
- All 6 specs defined as `val` in `object Motion` — zero runtime allocation overhead vs. `fun` factories

## File List

- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Motion.kt` (new)

## Change Log

- 2026-04-27: Story 2.2 implemented — Motion constants complete, BUILD SUCCESSFUL
