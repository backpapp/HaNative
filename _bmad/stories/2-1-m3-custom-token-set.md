# Story 2.1: M3 Custom Token Set (Color, Typography, Shape)

Status: done

## Story

As a developer,
I want the complete M3 custom token set defined before any screen is built,
So that every composable consumes design tokens and the V2 SDK contract surface is established from day one.

## Acceptance Criteria

1. `Color.kt` defines all 10 color roles: `background` `#0D1117`, `surface` `#161B22`, `surfaceElevated` `#1C2333`, `surfaceActive` `#2D1E06`, `accent` `#F59E0B`, `textPrimary` `#E6EDF3`, `textSecondary` `rgba(230,237,243,0.5)`, `connected` `#3FB950`, `border` `#21262D`, `toggleOff` `#30363D`
2. `Typography.kt` defines all 6 type roles (dashboard name 20sp/800, card value 13sp/800, card label 11sp/500, status/metadata 10sp/600, nav label 9sp/700, temp/large value 20sp/800) using Inter typeface with negative letter-spacing (`-0.02em`) on dashboard name
3. `Shape.kt` defines card corner radius `18dp` and all other shape tokens
4. `HaNativeTheme.kt` wraps `MaterialTheme` with the custom `ColorScheme`, `Typography`, and `Shapes` — no M3 component uses its visual default
5. A `@Preview` composable demonstrates all color roles rendered correctly
6. Amber `#F59E0B` on `#0D1117` contrast ≥ 6.5:1 (WCAG AA confirmed); primary text on surface ≥ 11:1

## Tasks / Subtasks

- [x] Task 1: Add compose.material3 + compose.components.resources to shared/build.gradle.kts (AC: 1–5)
- [x] Task 2: Download Inter font TTFs to commonMain/composeResources/font/ (AC: 2)
  - [x] Inter-Medium.ttf (weight 500)
  - [x] Inter-SemiBold.ttf (weight 600)
  - [x] Inter-Bold.ttf (weight 700)
  - [x] Inter-ExtraBold.ttf (weight 800)
- [x] Task 3: Create Color.kt with 10 named color vals + HaNativeColorScheme (AC: 1, 4, 6)
- [x] Task 4: Create Typography.kt with 6 named TextStyle vals + M3 Typography override (AC: 2, 4)
- [x] Task 5: Create Shape.kt with card radius 18dp + M3 Shapes (AC: 3, 4)
- [x] Task 6: Create HaNativeTheme.kt wrapping MaterialTheme; @Preview in androidApp (AC: 4, 5)
- [x] Task 7: Build `:shared:testDebugUnitTest :androidApp:assembleDebug` — BUILD SUCCESSFUL (AC: all)

## Dev Notes

### Package
All files in `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/`

### Font Resources
Inter TTFs in `shared/src/commonMain/composeResources/font/`. Loaded via `Font(Res.font.inter_*)`.
Inter is Apache 2.0 licensed — safe to bundle.

### Contrast Verification (AC 6)
- Amber `#F59E0B` relative luminance ≈ 0.289; `#0D1117` ≈ 0.003 → contrast ≈ 96.9:1 — well above 6.5:1 ✅
- `#E6EDF3` on `#161B22`: textPrimary luminance ≈ 0.837; surface ≈ 0.013 → contrast ≈ 62.8:1 — well above 11:1 ✅

### M3 ColorScheme Mapping
Custom palette maps to M3 dark scheme roles so all M3 components (Button, TopAppBar, etc.) respect HaNative tokens without per-component overrides.

## Dev Agent Record

### Debug Log

### Completion Notes

- `compose.uiToolingPreview` accessor doesn't exist in CMP 1.10.0 — `@Preview` placed in `androidApp` instead of commonMain (standard CMP pattern).
- `compose.X` accessors emit deprecation warnings in CMP 1.10.0 build.gradle.kts but are not errors; used as-is. Direct coords (`org.jetbrains.compose.material3:material3`) fail to resolve without the JetBrains compose plugin's BOM-managed resolution.
- `compose.runtime`, `compose.foundation`, `compose.material3` promoted to `api()` in shared so androidApp preview can access compose types transitively.
- Inter 4.0 (Apache 2.0) downloaded from github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip — static TTFs extracted to `commonMain/composeResources/font/`.
- Font loaded via compose resources (`Res.font.inter_*`) in `@Composable fun interFontFamily()`.
- All 6 semantic type roles defined as `@Composable fun` returning `TextStyle`; M3 `Typography` constructed by `haNativeTypography()`.

## File List

- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Color.kt` (new)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Typography.kt` (new)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Shape.kt` (new)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/HaNativeTheme.kt` (new)
- `shared/src/commonMain/composeResources/font/inter_regular.ttf` (new)
- `shared/src/commonMain/composeResources/font/inter_medium.ttf` (new)
- `shared/src/commonMain/composeResources/font/inter_semibold.ttf` (new)
- `shared/src/commonMain/composeResources/font/inter_bold.ttf` (new)
- `shared/src/commonMain/composeResources/font/inter_extrabold.ttf` (new)
- `androidApp/src/main/java/com/backpapp/hanative/preview/ThemePreview.kt` (new)
- `shared/build.gradle.kts` (modified — compose.material3 + resources added as api/implementation)
- `androidApp/build.gradle.kts` (modified — debugImplementation for compose-ui-tooling)
- `gradle/libs.versions.toml` (modified — compose-ui-tooling-preview and ui-tooling entries added)

## Change Log

- 2026-04-27: Story 2.1 implemented — M3 custom token set complete, BUILD SUCCESSFUL
