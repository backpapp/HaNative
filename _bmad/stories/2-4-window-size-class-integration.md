# Story 2.4: WindowSizeClass Integration

Status: done

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

## Review Findings

_Reviewed 2026-04-27. 3-layer adversarial review (Blind Hunter, Edge Case Hunter, Acceptance Auditor). 2 decisions, 3 patches, 8 deferred, 6 dismissed._

### AC Verdict

| AC | Result | Notes |
|----|--------|-------|
| AC1 | PASS | `calculateWindowSizeClass(this)` → `LocalWindowSizeClass` via `CompositionLocalProvider` ✓ |
| AC2 | PASS† | COMPACT/MEDIUM → `CompactLayout`; EXPANDED → `ExpandedLayout` with rail placeholder. †Rail uses `Box+Text` (spec says "placeholder, not implemented V1") |
| AC3 | PASS | No `dp`-locked text; all `Text` inherits M3 typography (`sp`) |
| AC4 | PASS | COMPACT (390dp) + EXPANDED (1280dp) previews present, `LocalWindowSizeClass` provided |

### [Review][Decision] D1 — `configChanges` in AndroidManifest

No `android:configChanges` declared on `MainActivity`. On standard phones, Activity recreates on rotation and `calculateWindowSizeClass` recomputes correctly. On foldables (API 30+), fold/unfold may not recreate the Activity — meaning `calculateWindowSizeClass` won't update reactively without `configChanges` + manual handling.

**Options:**
1. Add `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"` to `MainActivity` now and verify `calculateWindowSizeClass` still recomputes on rotation
2. Defer to a foldable-specific story; document known gap

### [Review][Decision] D2 — Nav tabs disconnected from `backStack`

`CompactLayout.selectedIndex` updates visual tab selection but never pushes any route onto the `NavDisplay` back stack. Tapping Rooms or Settings does nothing to navigation. Spec says "3 stubs per UX-DR12, icon-free for V1" — this may be intentional.

**Options:**
1. Confirm as intentional V1 scaffolding — defer nav wiring to next nav story
2. Wire `selectedIndex` changes to `backStack` push now (requires `RoomsRoute` + `SettingsRoute` to exist)

### [Review][Patch] P1 — Dead test: `windowSizeClass_localDefaultIsCompact`

`WindowSizeClassTest.kt:27-33` — body is `assertNotNull(WindowSizeClass.COMPACT)`. Enum constants are non-null by definition; this assertion can never fail. Test name claims to verify the `CompositionLocal` default but does not access it.

**Fix:** Replace with an assertion that validates the enum value used in the `CompositionLocal` default declaration, or remove and document the limitation.

### [Review][Patch] P2 — `windowSizeClass` mapping not `remember`-ed

`MainActivity.kt:~30` — the `when (m3SizeClass.widthSizeClass)` mapping runs on every recomposition, not just when `m3SizeClass` changes.

**Fix:**
```kotlin
val windowSizeClass = remember(m3SizeClass.widthSizeClass) {
    when (m3SizeClass.widthSizeClass) { ... }
}
```

### [Review][Patch] P3 — Preview crash risk with `rememberNavBackStack`

`WindowSizeClassPreview.kt` renders full `HaNativeNavHost()` which calls `rememberNavBackStack(navConfig, OnboardingRoute)`. Navigation3's back stack implementation requires a `SavedStateRegistryOwner` not present in the Compose Preview environment. Previews may crash silently at render time in Android Studio.

**Fix:** Verify previews actually render in Android Studio. If they crash, wrap `NavDisplay` in a preview-safe stub or restructure previews to render `CompactLayout`/`ExpandedLayout` directly with a fake content lambda.

### [Review][Deferred]

| ID | Finding | Reason deferred |
|----|---------|-----------------|
| DEF1 | `selectedIndex` ↔ `backStack` disconnected | Intentional V1 stub nav per spec (see D2) |
| DEF2 | `ExpandedLayout` uses `Box+Text` not `NavigationRailItem` | Spec: "Growth hook, text-only placeholder, not implemented V1" |
| DEF3 | `selectedIndex` lost on COMPACT↔EXPANDED switch | Moot until nav is wired (DEF1) |
| DEF4 | No `Scaffold` in `ExpandedLayout` — insets not consumed | Deferred to full expanded layout story |
| DEF5 | `GlobalContext.get().get<HapticEngine>()` in `remember` | Existing pattern; Koin init is synchronous at callsite |
| DEF6 | `navContent` lambda not stable — forced recomposition | V1 scaffold; perf acceptable |
| DEF7 | `material3-window-size = "1.3.1"` pinned separately from BOM | Version matches current M3; monitor on upgrades |
| DEF8 | No MEDIUM-width preview | AC4 only required COMPACT + EXPANDED |

## Change Log

| Date | Change |
|------|--------|
| 2026-04-27 | Story 2.4 complete — WindowSizeClass integration via M3, responsive nav shell, previews |
| 2026-04-27 | Code review patches applied — D1: configChanges added to Manifest; D2: stub nav confirmed intentional; P1: dead test replaced; P2: windowSizeClass wrapped in remember; P3: accepted risk (AS preview env provides SavedStateRegistryOwner) |
