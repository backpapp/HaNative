# Story 4.3: Core EntityCard — Toggleable & Read-Only Variants

Status: done

## Story

As a power user,
I want to tap entity cards and have my home respond with haptic confirmation before I lift my thumb,
so that every control interaction builds trust through speed and physical feedback.

## Acceptance Criteria

1. `EntityCard` is a row-based composable in `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/`. Anatomy: `[domain-icon] [entity-name + state-label] [right-action]`. Two sizes — `EntityCardSize.Standard` (72dp row min height) and `EntityCardSize.Compact` (56dp row min height) — selected via parameter.
2. **Toggleable variant** (light, switch, input_boolean): full card row tappable. Press registers `HapticPattern.ToggleOn` (when entity off→on) or `HapticPattern.ToggleOff` (when on→off) on **touch-down** (not release). `CallServiceUseCase` invokes `homeassistant.toggle` with `entityId`. Card immediately renders optimistic new state with `MaterialTheme.colorScheme.primaryContainer` (the `surfaceActive` token) background. On WebSocket-confirmed match within `OPTIMISTIC_TIMEOUT_MS = 5000`, optimistic clears. On rejection (timeout OR `Result.failure`), card snaps back via `Motion.snapBackRejection` and `HapticPattern.ActionRejected` fires.
3. **Read-only variant** (sensor, binary_sensor): displays state value and `unit_of_measurement` attribute when present. No toggle widget. No clickable modifier. No haptic registered. No `CallServiceUseCase` wiring.
4. **Stale state**: when caller passes `isStale = true`, card content dims to 50% opacity (`Modifier.alpha(0.5f)`) and an inline trailing label `"updated {n}m ago"` (relative to `entity.lastChanged`) renders below the state-label. No separate full-card overlay. Staleness flag is passed in by parent (Dashboard ViewModel) — `EntityCard` does not compute it.
5. Each `EntityCard` instance internally subscribes only to its own entity's flow via `ObserveEntityStateUseCase` (Koin `koinInject()`), keyed by `entityId`. Callers must use `key(card.entityId) { EntityCard(...) }` inside `LazyColumn` lists. Single entity update must NOT recompose sibling cards (NFR5) — verified via `Snapshot.withMutableSnapshot` test or by confirming each card has its own `produceState`/`collectAsState` boundary.
6. **Accessibility:**
   - Toggleable: `Modifier.semantics { contentDescription = "{friendly_name}, {stateLabel}"; role = Role.Switch; stateDescription = state }`. Example: `"Living Room light, on"`.
   - Read-only: `Modifier.semantics { contentDescription = "{friendly_name}, {stateLabel} {unit ?: ""}".trim() }`. Role omitted.
   - All interactive elements wrapped with `Modifier.minimumInteractiveComponentSize()` (48dp floor).
7. `friendly_name` resolves from `entity.attributes["friendly_name"]` when present; otherwise falls back to a humanised `entityId` (drop domain prefix, replace `_` with space, title-case). State label resolves `entity.state` to a humanised string (`"on"` → `"On"`, `"unavailable"` → `"Unavailable"`). Helpers live in same file as private functions.
8. Domain-icon resolution: `domainIcon(entity: HaEntity): ImageVector` private helper maps each supported domain to a Material icon — `light` → `Icons.Outlined.Lightbulb`, `switch` → `Icons.Outlined.ToggleOn`, `input_boolean` → `Icons.Outlined.RadioButtonChecked`, `sensor` → `Icons.Outlined.Sensors`, `binary_sensor` → `Icons.Outlined.Notifications`. Uses only `androidx.compose.material.icons.outlined.*` (multiplatform-safe).
9. `@Preview` previews live alongside `EntityCard.kt` covering all 5 states (`default`, `active`, `stale`, `optimistic`, `error`) for **both** variants — 10 previews total. `@Preview` is `androidx.compose.ui.tooling.preview.Preview` (Compose-MP-supported in androidMain only — preview file lives in `shared/src/androidMain`).
10. No business state inside the composable — optimistic state held in `remember { mutableStateOf(...) }` scoped to a single entityId; resets when entityId changes via `LaunchedEffect(entityId)`. The card has zero references to ViewModel or repository; only `koinInject<ObserveEntityStateUseCase>()` and `koinInject<CallServiceUseCase>()`.

## Tasks / Subtasks

- [x] Task 1: Add Compose Material Icons Extended dependency for domain icons (AC: 8)
  - [x] 1.1: Added `implementation(compose.materialIconsExtended)` to commonMain in `shared/build.gradle.kts`.
  - [x] 1.2: All five icons resolved; iOS + Android compiles confirm.

- [x] Task 2: Create `EntityCard.kt` skeleton + types (AC: 1, 7, 8)
  - [x] 2.1: Create `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityCard.kt`.
  - [x] 2.2: Define public API:
    ```kotlin
    enum class EntityCardSize { Standard, Compact }

    @Composable
    fun EntityCard(
        entityId: String,
        modifier: Modifier = Modifier,
        size: EntityCardSize = EntityCardSize.Standard,
        isStale: Boolean = false,
    )
    ```
  - [x] 2.3: Inside, resolve dependencies via Koin: `val observe: ObserveEntityStateUseCase = koinInject()` and `val call: CallServiceUseCase = koinInject()`. Use `import org.koin.compose.koinInject`.
  - [x] 2.4: Collect entity flow keyed by entityId:
    ```kotlin
    val entity by remember(entityId) { observe(entityId) }
        .collectAsState(initial = null)
    ```
  - [x] 2.5: Add private helpers `friendlyName(entity: HaEntity?, fallbackId: String): String`, `stateLabel(state: String): String`, `domainIcon(entity: HaEntity?): ImageVector`. Domain dispatch on the sealed subtype (`is HaEntity.Light`, etc.) — never branch on raw entityId prefix.

- [x] Task 3: Implement read-only variant rendering (AC: 3, 4, 6)
  - [x] 3.1: Branch by entity subtype. For `is HaEntity.Sensor` and `is HaEntity.BinarySensor` (and `null`), render a non-clickable `Row` — no `Modifier.clickable`, no haptic.
  - [x] 3.2: Layout — `Row(Modifier.fillMaxWidth().heightIn(min = if (size==Standard) 72.dp else 56.dp).padding(horizontal=16.dp, vertical=8.dp), verticalAlignment=CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp))`.
  - [x] 3.3: Children — `Icon(domainIcon(entity), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)`, `Column(Modifier.weight(1f)) { Text(friendlyName, MaterialTheme.typography.bodyLarge); Text(stateLabel + unit, MaterialTheme.typography.bodyMedium, color=onSurfaceVariant) }`. Right-action slot empty.
  - [x] 3.4: For `BinarySensor`, also dispatch state label so `"on"` → `"Detected"` / `"off"` → `"Clear"` is acceptable but not required for MVP — humanised `"On"`/`"Off"` is fine. Document the deferral in a code comment.
  - [x] 3.5: Apply `Modifier.semantics { contentDescription = ... }` at `Row` level. No `role`. No `stateDescription`. Per AC6.
  - [x] 3.6: Apply `Modifier.alpha(if (isStale) 0.5f else 1f)` to the `Row` and append `" • updated {n}m ago"` to the state-label `Text` when `isStale && entity != null`. Compute `n` from `(Clock.System.now() - entity.lastChanged).inWholeMinutes`.

- [x] Task 4: Implement toggleable variant — touch-down haptic + optimistic + reject (AC: 2, 4, 5, 6, 10)
  - [x] 4.1: Branch for `is HaEntity.Light`, `is HaEntity.Switch`, `is HaEntity.InputBoolean`.
  - [x] 4.2: Optimistic state:
    ```kotlin
    var optimisticOn: Boolean? by remember(entityId) { mutableStateOf(null) }
    var rejected: Boolean by remember(entityId) { mutableStateOf(false) }
    LaunchedEffect(entityId) { optimisticOn = null; rejected = false }
    ```
  - [x] 4.3: Resolve `displayedOn` priority: `optimisticOn ?: (entity?.state == "on")`.
  - [x] 4.4: Touch-down detection — use `Modifier.pointerInput(entityId) { detectTapGestures(onPress = { offset -> /* fire haptic + dispatch on press, await release for visual */ awaitRelease() }) }`. Per AC2, haptic + service call must fire on `onPress` (touch-down), not release. Pseudo:
    ```kotlin
    val haptic = LocalHapticEngine.current
    val scope = rememberCoroutineScope()
    Modifier.pointerInput(entityId, displayedOn) {
        detectTapGestures(onPress = {
            val nextOn = !displayedOn
            haptic.fire(if (nextOn) HapticPattern.ToggleOn else HapticPattern.ToggleOff)
            optimisticOn = nextOn
            scope.launch {
                val result = call("homeassistant", "toggle", entityId)
                if (result.isFailure) {
                    haptic.fire(HapticPattern.ActionRejected)
                    rejected = true
                    optimisticOn = null
                }
            }
            awaitRelease()
        })
    }
    ```
  - [x] 4.5: Optimistic timeout — companion `private const val OPTIMISTIC_TIMEOUT_MS = 5_000L`. After `optimisticOn` is set, start a `LaunchedEffect(optimisticOn)` that delays then, if WebSocket has not yet reflected the change (`entity?.state` mismatch with `optimisticOn`), treat as rejection (fire `HapticPattern.ActionRejected`, clear optimistic, set rejected=true).
  - [x] 4.6: Reconciliation — second `LaunchedEffect(entity?.state)` clears `optimisticOn = null` once `entity.state` matches `optimisticOn`'s "on"/"off" value.
  - [x] 4.7: Snap-back animation — when `rejected` flips true, animate the `Row`'s `translationX` via `animateFloatAsState(targetValue = 0f, animationSpec = Motion.snapBackRejection)` after first nudging it (e.g., -8dp → 0dp). After animation completes, set `rejected = false`.
  - [x] 4.8: Active background — `val bg by animateColorAsState(if (displayedOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, animationSpec = Motion.entityStateChange)`. Apply `Modifier.background(bg, MaterialTheme.shapes.medium)` to the row.
  - [x] 4.9: Right-action — `Switch(checked = displayedOn, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize())`. The whole-row tap drives the toggle; the Switch is purely visual. `onCheckedChange = null` makes it inert (passes M3 a11y as it inherits the row's `Role.Switch`).
  - [x] 4.10: Apply `Modifier.semantics { contentDescription = "${friendlyName}, ${stateLabel(if (displayedOn) "on" else "off")}"; role = Role.Switch; stateDescription = if (displayedOn) "on" else "off" }` at the row.
  - [x] 4.11: Apply `Modifier.alpha(if (isStale) 0.5f else 1f)` and stale-timestamp suffix per Task 3.6.

- [x] Task 5: Verify single-card recomposition isolation (AC: 5)
  - [x] 5.1: In `EntityCard.kt`, ensure the `Flow` collection happens *inside* `EntityCard` (not in caller). Each call to `EntityCard(entityId=...)` produces an independent `collectAsState`. Confirm by code review — no list-level subscription.
  - [x] 5.2: Document for future Story 4.6 (Dashboard Screen) usage:
    ```kotlin
    // CALLER PATTERN (Story 4.6)
    LazyColumn {
        items(cards, key = { it.entityId }) { card ->
            key(card.entityId) { EntityCard(entityId = card.entityId) }
        }
    }
    ```
    Add this as a code comment block at the top of `EntityCard.kt`.

- [x] Task 6: Add `@Preview` matrix (AC: 9)
  - [x] 6.1: Create `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityCardPreviews.kt` (androidMain because `@Preview` tooling is Android-only on Compose-MP today).
  - [x] 6.2: Build static `HaEntity` fixtures for each state — `default`, `active` (state="on"), `stale` (paired with `isStale=true`), `optimistic` (forced via wrapper preview composable that sets `optimisticOn` via parameter), `error`.
  - [x] 6.3: For optimistic and error previews, extract a `private fun EntityCardPreviewBody(state: PreviewState, entity: HaEntity, size: EntityCardSize)` so previews don't need Koin. Production `EntityCard` delegates to this body after resolving its own dependencies. This keeps previews dependency-free.
  - [x] 6.4: Wrap each preview in `HaNativeTheme { ... }`.
  - [x] 6.5: 10 previews total — `Toggleable_Default`, `Toggleable_Active`, `Toggleable_Stale`, `Toggleable_Optimistic`, `Toggleable_Error`, `ReadOnly_Default`, `ReadOnly_Active`, `ReadOnly_Stale`, `ReadOnly_Optimistic` (renders same as default for read-only — document), `ReadOnly_Error` (same — document). Acceptable to collapse Optimistic/Error for read-only down to one preview each that renders identically with a comment "no-op for read-only variant".

- [x] Task 7: Tests (AC: 2, 3, 5)
  - [x] 7.1: Create `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/components/EntityCardLogicTest.kt`. Test the pure helper functions: `friendlyName`, `stateLabel`, `domainIcon` (assert non-null vector for each supported subtype).
  - [x] 7.2: Defer composition tests — Compose UI tests require `compose.uiTest` runner not yet wired in this project. Add a `// TODO(Story 4.7-or-later)` note in the test file.
  - [x] 7.3: Run `./gradlew :shared:testAndroidHostTest` — must pass.
  - [x] 7.4: Run `./gradlew :shared:compileKotlinAndroid` — must compile.
  - [x] 7.5: Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — must link (ensures iOS Native compile path).

- [x] Task 8: Domain layer purity audit (AC: project rule)
  - [x] 8.1: `grep -r "androidx.compose\|androidx.compose.material" shared/src/commonMain/kotlin/com/backpapp/hanative/domain/` — must return empty.
  - [x] 8.2: `EntityCard.kt` may import `androidx.compose.*`, `domain.usecase.*`, `domain.model.*`, `platform.HapticPattern`, `platform.LocalHapticEngine`, `ui.theme.Motion`. Must NOT import `data.*` or `ktor.*` or `sqldelight.*`.

### Review Findings

_BMAD code review run 2026-04-30. Three reviewer layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor. After dedup/triage: 3 decisions, 8 patches, 5 deferred, ~10 dismissed._

- [x] [Review][Decision] Accessibility activation broken via `pointerInput`/`detectTapGestures` — TalkBack double-tap, keyboard Enter, and Switch Access dispatch synthetic clicks that go to `Modifier.clickable`/`toggleable`, NOT `pointerInput`. Spec AC2 mandates touch-down haptic (which only `pointerInput` can deliver). Conflict: assistive-tech users currently cannot activate the card. Options: (a) layer `Modifier.toggleable(role = Role.Switch, onValueChange = ...)` alongside `pointerInput` so a11y path runs the same optimistic+service flow on release while touch path stays touch-down; (b) accept on-release for all (simplifies code, drops touch-down feel); (c) explicit semantics action `onClick`. [EntityCard.kt:266-283]
- [x] [Review][Decision] No debounce / in-flight guard on rapid re-tap — second press while optimistic is pending fires a second `homeassistant.toggle` and a second haptic; under network reorder, terminal state can mismatch UI. Also: scroll/drag starting on the card still toggles it because `detectTapGestures.onPress` has no cancel-on-drag. Spec is silent. Options: (a) ignore presses while `optimisticOn != null`; (b) cancel optimistic on drag (move haptic+call to `onTap`, dropping touch-down); (c) accept and document. [EntityCard.kt:267-283]
- [x] [Review][Decision] Toggling `unavailable`/`unknown` entity sends a doomed `homeassistant.toggle`, then triggers a 5s rejection snap-back — bad UX. Spec did not address. Options: (a) make toggleable card non-interactive when `state in {"unavailable","unknown"}`; (b) gate haptic/service call only; (c) accept current behaviour. [EntityCard.kt:209-283]

- [x] [Review][Patch] Snap-back animation is permanently stuck at -8 — `targetValue` is `-8f` while rejected, `finishedListener` only resets when `value == 0f`, which never happens. Card translates -8 once, never returns. Use `Animatable` w/ sequential `animateTo(-nudgeDp.toPx()) → animateTo(0f)` keyed on a `LaunchedEffect(rejected)`, OR clear `rejected` via timer not finishedListener. [EntityCard.kt:241-247]
- [x] [Review][Patch] Snap-back nudge uses raw float as pixels, not dp — spec says "-8dp → 0dp" but `translationX = nudge` where `nudge = -8f` translates 8 px. Convert via `with(LocalDensity.current) { (-8).dp.toPx() }` or use `Modifier.offset { IntOffset(nudge.dp.roundToPx(), 0) }`. [EntityCard.kt:243, 259]
- [x] [Review][Patch] Inner `Switch(onCheckedChange = null)` registers a second `Role.Switch` a11y node alongside the parent row's `Role.Switch` — TalkBack risk of double-announcing. Wrap Switch in `Modifier.clearAndSetSemantics {}` (or `semantics(mergeDescendants = false) { invisibleToUser() }`). [EntityCard.kt:300-304]
- [x] [Review][Patch] Stale suffix missing from `contentDescription` on both variants — visible label includes "updated 5m ago" but TalkBack only reads the non-stale label, so a11y users never hear staleness. Append `staleSuffix` to `contentDesc` for both `ToggleableEntityCard` and `ReadOnlyEntityCard`. [EntityCard.kt:159, 250]
- [x] [Review][Patch] `minutesSince` returns 0 when delta < 60s, rendering literal "updated 0m ago" — confusing. Render "Just now" (or "<1m ago") when delta < 60_000ms. [EntityCard.kt:333-338]
- [x] [Review][Patch] Stale suffix prefix `" • "` deviates from spec literal `"updated {n}m ago"` (AC4). Drop bullet, use `, ` or simple space. [EntityCard.kt:156, 233]
- [x] [Review][Patch] Redundant `LaunchedEffect(entityId) { ... }` reset block — `remember(entityId) { mutableStateOf(forcedOptimisticOn) }` already re-initializes on entityId change. The effect is dead in production (`forced*` always defaults) and confusing. Remove. [EntityCard.kt:204-207]
- [x] [Review][Patch] `pointerInput(entityId, displayedOn)` re-keys the gesture detector on every state flip, cancelling `awaitRelease()` mid-press. Replace `displayedOn` key with `rememberUpdatedState(displayedOn)` and key `pointerInput` only on `entityId`. [EntityCard.kt:266]

- [x] [Review][Defer] `friendlyName` returns empty string when `entityId` is empty or has no `object_id` (e.g. `"light."`). Edge case unlikely from real HA. [EntityCard.kt:308-317] — pre-existing/edge.
- [x] [Review][Defer] Future timestamps from clock skew silently render as "0m ago" via `coerceAtLeast(0L)`. [EntityCard.kt:333-338] — masks real bugs but cosmetic.
- [x] [Review][Defer] Unsupported subtypes (Climate/Cover/etc.) fall back to `Icons.Outlined.Sensors` — same icon as a real sensor; no visual distinction. [EntityCard.kt:324-331] — Story 4.4+ addresses.
- [x] [Review][Defer] Recomposition isolation (NFR5) verified by code review only; no `Snapshot.withMutableSnapshot` test exists. Spec AC5 accepts code review. — deferred per spec.
- [x] [Review][Defer] `Motion.entityStateChange` (TweenSpec<Float>) cannot type-match `animateColorAsState` directly; equivalent `tween(200, FastOutSlowInEasing)` re-declared. Token expansion to expose a Color-typed variant deferred. [EntityCard.kt:235-239] — Motion module enhancement.

## Dev Notes

### Architecture Compliance

- **Compose composable lives in `:shared/commonMain/ui/components/`** per `architecture.md#FR → Directory Mapping` (FR17–30, FR31–38 covers components).
- **No ViewModel for individual cards** — Story 4.3 deliberately keeps the card self-subscribing to a per-entity slice via `ObserveEntityStateUseCase`. Dashboard-level orchestration (selecting which entityIds to render, computing connection-level `isStale`) lives in Story 4.6's `DashboardViewModel`. This story does not create a ViewModel.
- **Optimistic UI pattern** prescribed in `architecture.md#Process Patterns:322` — "Emit `UiState.Success` with pending state immediately → revert on WebSocket error. No loading spinner for entity toggle." Story 4.3 implements the per-card analogue using local `remember` state instead of ViewModel `UiState`.
- **No `var` in domain models** — already enforced; `EntityCard` only reads `HaEntity` immutables.
- **`bind` Koin syntax** is used in DataModule already; no DI changes needed for Story 4.3 — `ObserveEntityStateUseCase` and `CallServiceUseCase` already registered (Story 4.1 review patches confirmed).

### `HapticEngine` Wiring

`LocalHapticEngine` is a `staticCompositionLocalOf` declared in `platform/HapticFeedback.kt`. Confirmed providers exist in androidMain `HapticEngineModule.kt` and iosMain `HapticEngineModule.kt`. The composable just calls `LocalHapticEngine.current.fire(...)` — no DI lookup required for haptics.

### Touch-Down Haptic Critical Detail

`Modifier.clickable` fires onClick on **release** — wrong for AC2. Use `detectTapGestures(onPress = { ... })` instead — the lambda runs at the moment the finger touches the screen. This is the same pattern Material's `InteractionSource` ripple uses for press detection.

### Optimistic State Lifetime

The optimistic `Boolean?` lives in `remember(entityId)` — keying on `entityId` ensures recycling within a `LazyColumn` does not bleed state between rows. `LaunchedEffect(entityId)` resets state when the same composable slot is reused for a different entity. This matches Compose `key()` block semantics in the caller pattern (see Task 5.2).

### `OPTIMISTIC_TIMEOUT_MS = 5000`

Picked from UX spec: optimistic is meant to feel instant, but if HA never echoes back, treat as rejected to avoid leaving the card in a permanently-wrong-but-orange state. 5s is generous for a healthy local network; Story 4.8 (Offline Resilience) may tune this.

### `surfaceActive` vs `MaterialTheme.colorScheme.primaryContainer`

The token `surfaceActive` is wired to `primaryContainer` in `HaNativeColorScheme` (Color.kt:20). Composables MUST use `MaterialTheme.colorScheme.primaryContainer` — never the raw `surfaceActive` const — so dynamic theming and previews work. Same rule for the inactive bg → `MaterialTheme.colorScheme.surface`.

### Domain Icons — Why Material Icons Extended

`compose.material3` ships only the small core icon set (~20 icons). Domain icons require `material-icons-extended` (or `compose.materialIconsExtended` in CMP DSL). If unavailable in the version catalog, the fallback is a hand-rolled `DomainIcon.kt` with `Icons.Default.*` substitutes — uglier but unblocking. Confirm in Task 1.1 before committing the icon names.

### `@Preview` on Compose Multiplatform

`androidx.compose.ui.tooling.preview.Preview` only renders inside Android Studio's Android target. iOS preview support in Compose Multiplatform is still experimental. Place all `@Preview` composables under `shared/src/androidMain/...` to keep `commonMain` clean of `compose.ui.tooling.preview` imports. The card itself (`EntityCard.kt`) lives in commonMain.

### Stale Computation Boundary

This story does NOT compute connection-level `isStale`. The repository-level `isStale` flag on `UiState.Success` (Story 4.1 AC) belongs to the dashboard's overall connection state; per-card stale flag is plumbed down by the parent. For Story 4.3, treat `isStale` as a parameter and trust the caller. Story 4.8 will introduce per-entity staleness rules.

### Project Structure — New Files

```
shared/src/commonMain/kotlin/com/backpapp/hanative/
  └── ui/
      └── components/
          └── EntityCard.kt                          ← NEW

shared/src/androidMain/kotlin/com/backpapp/hanative/
  └── ui/
      └── components/
          └── EntityCardPreviews.kt                  ← NEW

shared/src/commonTest/kotlin/com/backpapp/hanative/
  └── ui/
      └── components/
          └── EntityCardLogicTest.kt                 ← NEW
```

Modified files (potentially):
- `shared/build.gradle.kts` — add `compose.materialIconsExtended` if absent (Task 1.1).

**Do NOT modify:**
- `EntityRepositoryImpl.kt`, `EntityState.sq`, `Dashboard*.sq` — out of scope
- `DataModule.kt` / `DomainModule.kt` — use cases already registered in Story 4.1
- `HapticFeedback.kt` — Story 2.3 frozen contract
- `Motion.kt` / `Color.kt` / `HaNativeTheme.kt` — tokens consumed as-is

### Previous Story Intelligence

**Story 4.1 (entity pipeline)** — `ObserveEntityStateUseCase(entityId)` returns `Flow<HaEntity?>` (nullable). Card MUST handle the null case (entity not yet loaded) — render a skeleton row or the read-only fallback. Story 4.1 also established that flows are cold and per-entity slices come from the same `StateFlow<List<HaEntity>>` upstream, so subscribing 20 cards to 20 flows is cheap (no extra WebSocket subscriptions).

**Story 4.2 (dashboard persistence)** — domain models `Dashboard`, `DashboardCard` exist; `DashboardCard.entityId` is the contract this story plugs into. `DashboardCard.config` is opaque JSON for now — this story does not parse it.

**Story 2.2 (motion)** — `Motion.snapBackRejection: SpringSpec<Float>` and `Motion.entityStateChange: TweenSpec<Float>` are confirmed present (Motion.kt:23, 26). Use them by reference, do not re-tune.

**Story 2.3 (haptic)** — `HapticPattern` and `LocalHapticEngine` are frozen; `ToggleOn`, `ToggleOff`, `ActionRejected`, `ActionTriggered` are the four patterns this story uses. Do not add new patterns — Story 4.4 will use `StepperInc/Dec`/`ActionTriggered` for stepper/trigger variants.

### Testing Standards

- Test framework: `kotlin.test` — never JUnit4/5 in `commonTest`.
- Test task: `./gradlew :shared:testAndroidHostTest`.
- Compose UI test runner is NOT wired — defer touch-down/optimistic integration tests to a later story; pure-helper unit tests are sufficient for Story 4.3.
- `assertEquals(expected, actual)` — not `assert(x == y)`.
- Backtick test names are JVM-only — fine for `:testAndroidHostTest`. Do not run these tests on Native.

### Git Intelligence

| Commit | What it established |
|--------|-------------------|
| `e158f57` | Story 4.2 dashboard persistence (Dashboard.sq, DashboardCard.sq, repo, 6 use cases, DI) |
| `0adc61c` | Story 4.1 review patches: `Mutex`, `withContext(Dispatchers.Default)`, `bind` Koin syntax, `runCatchingCancellable`, `last_changed` schema |
| `99f4ea3` | Story 4.1: `EntityState.sq`, `EntityRepositoryImpl`, `ObserveEntityStateUseCase`, `CallServiceUseCase` |
| Story 2.2 | `Motion.kt` with `snapBackRejection`, `entityStateChange`, `staleIndicatorFade` |
| Story 2.3 | `HapticEngine`, `HapticPattern`, `LocalHapticEngine` |
| Story 2.1 | `HaNativeColorScheme` with `surfaceActive` → `primaryContainer` mapping |

Relevant codebase state confirmed before story creation:
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/` exists but is empty.
- `Motion.kt:23,26,29` exposes the three motion specs needed.
- `Color.kt:9,20` confirms `surfaceActive` → `primaryContainer`.
- `HapticFeedback.kt` exposes `LocalHapticEngine` as a `staticCompositionLocalOf`.
- `HaEntity.kt` is a sealed class with `Light`, `Switch`, `InputBoolean`, `Sensor`, `BinarySensor` subtypes — the five domains this story handles directly.
- `ObserveEntityStateUseCase(entityId)` returns `Flow<HaEntity?>` (nullable).
- `CallServiceUseCase(domain, service, entityId, serviceData)` returns `Result<Unit>`.

### References

- [Source: `_bmad/outputs/epics.md#Story 4.3`] — Full acceptance criteria
- [Source: `_bmad/outputs/architecture.md#Process Patterns:322`] — Optimistic UI control pattern
- [Source: `_bmad/outputs/architecture.md#Enforcement:330`] — Layer purity rules
- [Source: `_bmad/outputs/architecture.md#FR → Directory Mapping`] — `ui/components/` placement
- [Source: `_bmad/outputs/ux-design-specification.md:475-484`] — EntityCard anatomy, states, stale spec
- [Source: `_bmad/outputs/ux-design-specification.md:512-519`] — HapticEngine contract
- [Source: `_bmad/outputs/ux-design-specification.md:668,709`] — Accessibility (`contentDescription`, `role`, `stateDescription`)
- [Source: `_bmad/stories/4-1-sqldelight-schema-entity-state-pipeline.md`] — `ObserveEntityStateUseCase` returns `Flow<HaEntity?>`; per-card subscription pattern
- [Source: `_bmad/stories/4-2-dashboard-persistence-layer.md`] — `DashboardCard.entityId` is the contract Story 4.3 consumes
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/HapticFeedback.kt`] — `HapticPattern`, `LocalHapticEngine`
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Motion.kt`] — `snapBackRejection`, `entityStateChange`, `staleIndicatorFade`
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Color.kt`] — `surfaceActive` token, `HaNativeColorScheme.primaryContainer` mapping
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/HaEntity.kt`] — sealed subtypes for domain dispatch
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/ObserveEntityStateUseCase.kt`, `CallServiceUseCase.kt`] — input contracts

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- iOS link initially failed: `kotlin.time.Clock` shadowed `kotlinx.datetime.Clock` because `kotlinx-datetime` 0.7.1 deprecated its own Clock typealias. Switched to `kotlin.time.Clock.System.now()` with `@OptIn(kotlin.time.ExperimentalTime::class)`.
- `animateColorAsState` rejected `Motion.entityStateChange` (typed `TweenSpec<Float>`). Re-declared an equivalent `tween(200, FastOutSlowInEasing)` for the Color spec to keep the same easing/duration without retyping the shared token.
- `:shared:compileKotlinAndroid` task does not exist in this KMP module (Android only exposes `compileAndroidMain` + `testAndroidHostTest`). Used `:shared:testAndroidHostTest` for Android verification.

### Completion Notes List

- `EntityCard.kt` (commonMain) renders both toggleable (light/switch/input_boolean) and read-only (sensor/binary_sensor + null fallback) variants. Touch-down haptic via `detectTapGestures(onPress = …)`. Optimistic state with 5s timeout reconciliation against the WebSocket-confirmed `state == "on"`. Snap-back animation on rejection via `Motion.snapBackRejection` driving a `graphicsLayer.translationX` nudge.
- Internal `EntityCardBody` extracted so `@Preview` composables can render without Koin wiring.
- 10 `@Preview` entries in `EntityCardPreviews.kt` (androidMain). Read-only Optimistic/Error previews are documented as no-ops.
- `EntityCardLogicTest` covers `friendlyName`, `stateLabel`, `domainIcon` (12 cases). Compose UI tests deferred per Task 7.2.
- Domain layer purity audit clean: `grep androidx.compose shared/src/commonMain/.../domain/` returns empty.
- Builds verified: `:shared:testAndroidHostTest` BUILD SUCCESSFUL; `:shared:linkDebugFrameworkIosSimulatorArm64` BUILD SUCCESSFUL.

### File List

- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityCard.kt` (NEW)
- `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityCardPreviews.kt` (NEW)
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/components/EntityCardLogicTest.kt` (NEW)
- `shared/build.gradle.kts` (MODIFIED — added `compose.materialIconsExtended` to commonMain, `compose.ui.tooling.preview` to androidMain)

### Change Log

- 2026-04-30 — Story 4.3 implemented: `EntityCard` toggleable + read-only variants, touch-down haptic, optimistic UI w/ 5s timeout + snap-back rejection, 10 previews, helper-fn unit tests.
- 2026-05-01 — BMAD code review: 3 decisions resolved + 8 patches applied + 5 deferred. Notable fixes: a11y activation via layered `Modifier.toggleable` + `interactionSource` PressInteraction collector for touch-down haptic; in-flight debounce blocks rapid-retap toggle storm; `unavailable`/`unknown` states gate interaction; snap-back rewritten via `Animatable` sequential `animateTo(-8.dp.toPx()) → animateTo(0f)` driven by `rejectTrigger` counter (fixes permanently-stuck-at-(-8) bug); nudge converted from raw px to dp via `LocalDensity`; inner `Switch.clearAndSetSemantics{}` eliminates double `Role.Switch` a11y node; stale suffix appended to `contentDescription` so TalkBack announces staleness; "just now" wording for sub-1-min deltas; bullet "•" replaced with comma to match spec literal; redundant `LaunchedEffect(entityId)` reset removed; `pointerInput` churn eliminated by `interactionSource`-based design. Android `:shared:testAndroidHostTest` and iOS `:shared:linkDebugFrameworkIosSimulatorArm64` both green. Status → done.
