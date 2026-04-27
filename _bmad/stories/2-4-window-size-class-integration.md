# Story 2.4: WindowSizeClass Integration

Status: review

## Story

As a developer,
I want `WindowSizeClass` queried at the host level and passed via `CompositionLocal`,
So that every composable adapts to phone vs. tablet layout without querying the window directly.

## Acceptance Criteria

1. `MainActivity.kt` computes `WindowSizeClass` via `androidx.compose.material3.windowsizeclass` and provides it via `LocalWindowSizeClass` composition local
2. `COMPACT` (< 600dp) routes to single-column phone layout with bottom nav; `MEDIUM` falls back to `COMPACT`; `EXPANDED` (> 840dp) has a navigation rail placeholder (Growth hook, not implemented V1)
3. All text uses `sp` units; all layout uses `dp` — no text locked to `dp`
4. A `@Preview` at `COMPACT` and `EXPANDED` sizes confirms the `CompositionLocal` resolves correctly

## Tasks / Subtasks

- [x] Task 1: Add `material3-window-size-class` dependency to version catalog and `androidApp/build.gradle.kts` (AC: 1)
- [x] Task 2: Write failing unit test for `WindowSizeClass` default and enum values (RED phase) (AC: 1, 4)
- [x] Task 3: Update `MainActivity.kt` to use `calculateWindowSizeClass(this)` from `androidx.compose.material3.windowsizeclass`, mapping to custom `WindowSizeClass` enum (AC: 1)
- [x] Task 4: Update `HaNativeNavHost.kt` — COMPACT/MEDIUM: `Scaffold` + `NavigationBar`; EXPANDED: `Row` + `NavigationRail` placeholder (AC: 2, 3)
- [x] Task 5: Create `WindowSizeClassPreview.kt` in androidApp with `@Preview` at COMPACT (390dp) and EXPANDED (1280dp), verifying `LocalWindowSizeClass` resolves (AC: 4)
- [x] Task 6: Build `:shared:testDebugUnitTest :androidApp:assembleDebug` — BUILD SUCCESSFUL (AC: all)

## Dev Notes

### Architecture Context
- `LocalWindowSizeClass.kt` already existed in `shared/src/commonMain` with custom `WindowSizeClass` enum (COMPACT/MEDIUM/EXPANDED) — kept as-is; serves KMP (iOS + Android)
- `MainActivity.kt` had a `LocalConfiguration`-based approach from Story 1.4 — Story 2.4 upgrades to M3's `calculateWindowSizeClass(this)`
- `HaNativeNavHost.kt` previously had empty `Box` placeholders — Story 2.4 adds shell layout (Scaffold + nav chrome)
- Nav items for bottom nav: Dashboard, Rooms, Settings (3 stubs per UX-DR12); icon-free for V1
- Navigation rail for EXPANDED is a Growth hook — rendered as `NavigationRail` with text-only placeholder items

### M3 WindowSizeClass Mapping
```
M3 WindowWidthSizeClass.Compact  → WindowSizeClass.COMPACT  (< 600dp, per M3 spec)
M3 WindowWidthSizeClass.Medium   → WindowSizeClass.MEDIUM   (600–840dp)
M3 WindowWidthSizeClass.Expanded → WindowSizeClass.EXPANDED (> 840dp)
```

### Material3 Window Size Class Artifact
- Artifact: `androidx.compose.material3:material3-window-size-class`
- Version: `1.3.1` (compatible with Compose Multiplatform 1.10.0 / Material3 1.3.x)
- Catalog alias: `material3-window-size` (alias `material3-window-size-class` is reserved by Gradle 9)
- Android-only — added to `androidApp/build.gradle.kts` only

### Dependency Story (Story 2.1)
- `HaNativeTheme` now wraps content in `MainActivity` (added in this story)
- `LocalHapticEngine` already provided by MainActivity (Story 2.3)

### Preview Approach
- `@Preview(widthDp = 390, heightDp = 844)` for COMPACT phone
- `@Preview(widthDp = 1280, heightDp = 800)` for EXPANDED tablet
- Wraps in `CompositionLocalProvider(LocalWindowSizeClass provides ...)` to confirm resolution
- `PreviewHapticEngine` (no-op) defined locally to satisfy `LocalHapticEngine`

### Text/Layout Units (AC 3)
- All `Text` composables use `sp` via M3 `MaterialTheme.typography` tokens and direct string labels
- All layout `Modifier.width/padding` use `.dp` — verified in new NavHost code

## Dev Agent Record

### Debug Log

| # | Issue | Resolution |
|---|-------|------------|
| 1 | Gradle alias `material3-window-size-class` reserved — contains `class` | Renamed alias to `material3-window-size` in catalog and build files |
| 2 | Preview file imports split by edit — syntax error "imports only allowed at beginning of file" | Rewrote file with all imports grouped correctly |

### Completion Notes

Story 2.4 complete. Key deliverables:
- `MainActivity.kt` now uses `calculateWindowSizeClass(this)` from `androidx.compose.material3.windowsizeclass`, mapping M3 `WindowWidthSizeClass` to project `WindowSizeClass` enum, wraps content in `HaNativeTheme`
- `HaNativeNavHost.kt` split into `CompactLayout` (Scaffold + NavigationBar, 3 stub items) and `ExpandedLayout` (Row + NavigationRail placeholder), driven by `LocalWindowSizeClass.current`; MEDIUM falls back to COMPACT path
- `WindowSizeClassPreview.kt` has two `@Preview` functions at 390×844dp (COMPACT) and 1280×800dp (EXPANDED), each providing `LocalWindowSizeClass` via `CompositionLocalProvider`
- `WindowSizeClassTest.kt` (5 unit tests) verifies enum structure and ordering — all pass
- `BUILD SUCCESSFUL` for `:shared:testDebugUnitTest :androidApp:assembleDebug`

## File List

- `gradle/libs.versions.toml` — added `material3-window-size` version + library alias
- `androidApp/build.gradle.kts` — added `libs.material3.window.size` dep
- `androidApp/src/main/java/com/backpapp/hanative/MainActivity.kt` — M3 calculateWindowSizeClass + HaNativeTheme wrapper
- `shared/src/commonMain/kotlin/com/backpapp/hanative/navigation/HaNativeNavHost.kt` — CompactLayout (Scaffold+BottomNav) + ExpandedLayout (Row+NavigationRail)
- `androidApp/src/main/java/com/backpapp/hanative/preview/WindowSizeClassPreview.kt` — new, @Preview at COMPACT and EXPANDED
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/WindowSizeClassTest.kt` — new, 5 unit tests

## Change Log

| Date | Change |
|------|--------|
| 2026-04-27 | Story 2.4 complete — WindowSizeClass integration via M3, responsive nav shell, previews |
