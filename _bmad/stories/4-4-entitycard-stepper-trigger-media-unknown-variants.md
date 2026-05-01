# Story 4.4: EntityCard — Stepper, Trigger, Media & Unknown Variants

Status: done

## Story

As a power user,
I want climate, script, scene, and media player cards to feel as physical and immediate as light cards,
so that every entity domain in my home is controllable with the same level of trust.

## Acceptance Criteria

1. All four new variants are implemented as additional branches inside the existing `EntityCard` composable at `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityCard.kt`. No new public composable types — `EntityCard(entityId, modifier, size, isStale)` continues to be the single entry point. Variant selection happens by `when` on the resolved `HaEntity` sealed subtype (`is HaEntity.Climate`, `is HaEntity.Script`, `is HaEntity.Scene`, `is HaEntity.MediaPlayer`, `is HaEntity.Unknown`).
2. **Stepper variant** (`is HaEntity.Climate`):
   - Anatomy: `[domain-icon] [entity-name + current temp label] [−] [temp value] [+]`.
   - Current temperature renders at `MaterialTheme.typography.titleLarge` overridden to `fontSize = 20.sp` and `fontWeight = FontWeight.W800`. Display value is the optimistic target if pending, else `entity.targetTemperature` (`?: entity.currentTemperature ?: 0.0`), formatted as `"%.1f°"` (locale-default — no `Locale.US` hardcode).
   - `−` and `+` buttons are `IconButton`s wrapped with `Modifier.minimumInteractiveComponentSize()` (48dp floor each). Increment step is `STEPPER_DELTA_C = 0.5` (Celsius half-degree). No unit conversion in this story — HA reports temp in user's configured unit; we trust `entity.targetTemperature` as-is.
   - Tap on `+` fires `HapticPattern.StepperInc`; tap on `−` fires `HapticPattern.StepperDec`. Both haptics fire on **release** (`onClick`), not touch-down — stepper UX differs from toggle (rapid presses tolerated).
   - On tap, optimistic state updates `optimisticTemp` immediately; `CallServiceUseCase("climate", "set_temperature", entity.entityId, mapOf("temperature" to nextTemp))` dispatched in `rememberCoroutineScope().launch { … }`.
   - On `Result.failure` or 5s timeout without WebSocket-confirmed match (`abs(entity.targetTemperature - optimisticTemp) > 0.01`), fire `HapticPattern.ActionRejected`, snap back via the same `Animatable` `translationX(-8.dp → 0)` pattern Story 4.3 established, clear `optimisticTemp`.
   - When entity `state` is `"unavailable"` or `"unknown"`, both buttons are disabled (`enabled = false`) — no haptic, no service call. Same gating Story 4.3 review patches established.
3. **Trigger variant** (`is HaEntity.Script` OR `is HaEntity.Scene`):
   - Anatomy: `[domain-icon] [entity-name] [right-action="Run" Text or Icons.Outlined.PlayArrow]`.
   - Whole row tappable. Tap fires `HapticPattern.ActionTriggered` on **release** (`Modifier.toggleable` is wrong here — use `Modifier.clickable(role = Role.Button)`). `CallServiceUseCase` dispatches `"script.turn_on"` for `Script` and `"scene.turn_on"` for `Scene`, with `entityId = entity.entityId`, no `serviceData`.
   - Brief visual confirmation pulse: trigger an `Animatable<Float>` scale animation `1.0 → 1.04 → 1.0` using `Motion.entityStateChange`'s duration/easing, keyed by an internal `triggerCounter: Int` that increments on each successful tap. No persistent state change in the card itself — `state` will read `"on"`/`"off"` from the script/scene flow but the card does not render a checked indicator.
   - No optimistic state. Fire-and-forget — on failure, fire `HapticPattern.ActionRejected` once and skip snap-back. No background colour change.
   - Disabled when `state == "unavailable"`.
4. **Media variant** (`is HaEntity.MediaPlayer`):
   - Anatomy: `[domain-icon] [Column(media_title || friendly_name, "Playing" / "Paused" / "Idle")] [PlayPause IconButton]`.
   - `media_title` resolves from `entity.mediaTitle` (already exposed on `HaEntity.MediaPlayer`); when null, fall back to `friendlyName(entity, entityId)`.
   - State label: `state == "playing"` → `"Playing"`, `state == "paused"` → `"Paused"`, anything else (`"idle"`, `"off"`, `"on"`, `"standby"`) → humanised via `stateLabel(state)` (existing helper).
   - Right-action: `IconButton(onClick = onPlayPause)` containing `Icons.Outlined.PlayArrow` when `state != "playing"`, else `Icons.Outlined.Pause`. `Modifier.minimumInteractiveComponentSize()` enforced.
   - Tap fires `HapticPattern.ActionTriggered` on release. `CallServiceUseCase("media_player", "media_play_pause", entity.entityId)`. State updates flow back via existing WebSocket subscription — no optimistic `state` rewrite in this story (rationale: HA's `media_play_pause` is asymmetric and a half-second WS round-trip is acceptable for this variant; spec did not mandate optimistic).
   - On `Result.failure`, fire `HapticPattern.ActionRejected`. No snap-back animation (no optimistic state to revert).
   - Disabled when `state == "unavailable"`.
5. **Unknown variant** (`is HaEntity.Unknown`):
   - Read-only. No clickable modifier. No haptic. No service call.
   - Anatomy: `[generic-icon Icons.Outlined.HelpOutline] [Column(entity.entityId, stateLabel(state))]`. No right-action.
   - Renders without crash for any `attributes` shape — `attributes` map is **never** indexed in this branch.
   - Per FR11: this is the catch-all for any HA domain we have not modelled. Display is informational only.
6. **Stale state** behaves identically to Story 4.3 across all four variants — `Modifier.alpha(if (isStale) 0.5f else 1f)` plus appended `, updated {n}m ago` (or `, just now` when delta < 60s) suffix in both the visible text and the `contentDescription`. The stale suffix code path lives in a single helper `appendStaleSuffix(base: String, isStale: Boolean, lastChanged: Instant?): String` used by all variants.
7. **Accessibility** — same rules as Story 4.3:
   - Stepper: row `Modifier.semantics { contentDescription = "${friendlyName}, ${tempLabel}, target ${displayedTemp}°" }`. `−` IconButton: `contentDescription = "Decrease temperature"`. `+`: `"Increase temperature"`. No row-level `Role.Switch` — stepper is two buttons inside an info row.
   - Trigger: `Modifier.semantics { contentDescription = "${friendlyName}, ${stateLabel}"; role = Role.Button }`.
   - Media: row `Modifier.semantics { contentDescription = "${title}, ${stateLabel}" }`. PlayPause IconButton: `contentDescription = if (isPlaying) "Pause" else "Play"`.
   - Unknown: `Modifier.semantics { contentDescription = "${entityId}, ${stateLabel}" }`. No role.
   - All interactive elements wrapped with `Modifier.minimumInteractiveComponentSize()` (48dp floor).
   - Inner controls that delegate to a parent semantics node use `Modifier.clearAndSetSemantics {}` where required to avoid double-announcement (same fix Story 4.3 review applied to the inner `Switch`).
8. **`@Preview` matrix** — append previews to existing `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityCardPreviews.kt`:
   - Stepper: `Stepper_Default`, `Stepper_Active`, `Stepper_Stale`, `Stepper_Optimistic`, `Stepper_Error`
   - Trigger: `Trigger_Default`, `Trigger_Active` (mid-pulse), `Trigger_Stale`, `Trigger_Optimistic` (= `Trigger_Active` per spec — fire-and-forget; document), `Trigger_Error`
   - Media: `Media_Playing`, `Media_Paused`, `Media_Idle`, `Media_Stale`, `Media_Error`
   - Unknown: `Unknown_Default`, `Unknown_Stale`
   - Total new previews ≥ 17. Reuse the existing `EntityCardPreviewBody` extraction so previews stay Koin-free; extend the `PreviewState` enum or add per-variant preview entry points as needed.
9. **Variant dispatch** is internal to `EntityCard.kt`. Caller code (`EntityCard(entityId = "...")`) does not change. Internal private composables `StepperEntityCard`, `TriggerEntityCard`, `MediaEntityCard`, `UnknownEntityCard` — kept as private composables in the same file. The existing `ToggleableEntityCard` and `ReadOnlyEntityCard` are NOT modified beyond what's necessary to extract shared helpers (`appendStaleSuffix`, `friendlyName`, `stateLabel`, `domainIcon`, `Stat snap-back animation builder if reusable`).
10. **Recomposition isolation (NFR5)** — same caller pattern (`key(card.entityId) { EntityCard(entityId) }`). Each card subscribes only to its own entity slice. No new use cases are introduced. Single-card update must not recompose siblings.
11. **No new domain layer code** — `CallServiceUseCase`, `ObserveEntityStateUseCase`, `HapticPattern`, `LocalHapticEngine` already exist. This story is pure UI layer.

## Tasks / Subtasks

- [x] Task 1: Extract shared helpers + variant dispatch scaffold (AC: 1, 6, 9)
  - [x] 1.1: In `EntityCard.kt`, add private helper `appendStaleSuffix(base: String, isStale: Boolean, lastChanged: Instant?): String` consolidating the "just now" / "updated {n}m ago" logic from Story 4.3 patches. Reuse this helper in both visible text and `contentDescription` for all variants.
  - [x] 1.2: Extend the inner `EntityCardBody`/dispatch `when` to add new branches for `is HaEntity.Climate`, `is HaEntity.Script`, `is HaEntity.Scene`, `is HaEntity.MediaPlayer`, `is HaEntity.Unknown`. Keep existing `Light`/`Switch`/`InputBoolean`/`Sensor`/`BinarySensor`/`null` branches untouched.
  - [x] 1.3: Update `domainIcon(entity)` to add `Climate → Icons.Outlined.Thermostat`, `Script → Icons.Outlined.PlayArrow`, `Scene → Icons.Outlined.Lightbulb` (or `Icons.Outlined.AutoAwesome` if available), `MediaPlayer → Icons.Outlined.PlayCircle`, `Unknown → Icons.Outlined.HelpOutline`. Confirm each icon exists in `compose.materialIconsExtended` before committing names — if missing, fallback to `Icons.Outlined.DeviceUnknown`/closest available.

- [x] Task 2: Stepper variant (AC: 2, 6, 7)
  - [x] 2.1: Add private `@Composable fun StepperEntityCard(entity: HaEntity.Climate, isStale: Boolean, size: EntityCardSize, forcedOptimisticTemp: Double? = null, forcedRejected: Boolean = false)`.
  - [x] 2.2: State:
    ```kotlin
    var optimisticTemp: Double? by remember(entity.entityId) { mutableStateOf(forcedOptimisticTemp) }
    var rejectTrigger: Int by remember(entity.entityId) { mutableStateOf(0) }
    ```
  - [x] 2.3: `displayedTemp = optimisticTemp ?: entity.targetTemperature ?: entity.currentTemperature ?: 0.0`. Format as `"%.1f°".format(displayedTemp)` — Kotlin/MP `format` works on JVM; for iOS/Native use `decimalFormat(displayedTemp, 1)` helper or simple string interp `"${(displayedTemp * 10).roundToInt() / 10.0}°"`. Pick whichever compiles on `:linkDebugFrameworkIosSimulatorArm64`.
  - [x] 2.4: Layout — same `Row` skeleton as toggleable; replace right-action with three children: `IconButton(onClick = onDec, enabled = !disabled) { Icon(Icons.Outlined.Remove, "Decrease temperature") }`, `Text(formatted, fontSize = 20.sp, fontWeight = FontWeight.W800)`, `IconButton(onClick = onInc, enabled = !disabled) { Icon(Icons.Outlined.Add, "Increase temperature") }`. `disabled = entity.state in setOf("unavailable", "unknown")`.
  - [x] 2.5: `onInc`/`onDec` lambdas:
    ```kotlin
    val haptic = LocalHapticEngine.current
    val call: CallServiceUseCase = koinInject()
    val scope = rememberCoroutineScope()
    val onInc = {
        val next = (displayedTemp + STEPPER_DELTA_C)
        haptic.fire(HapticPattern.StepperInc)
        optimisticTemp = next
        scope.launch {
            val result = call("climate", "set_temperature", entity.entityId, mapOf("temperature" to next))
            if (result.isFailure) {
                haptic.fire(HapticPattern.ActionRejected)
                rejectTrigger++
                optimisticTemp = null
            }
        }
    }
    ```
    Symmetric for `onDec` with `StepperDec` and `next = displayedTemp - STEPPER_DELTA_C`.
  - [x] 2.6: Optimistic timeout — `LaunchedEffect(optimisticTemp) { if (optimisticTemp != null) { delay(OPTIMISTIC_TIMEOUT_MS); if (optimisticTemp != null && abs((entity.targetTemperature ?: 0.0) - (optimisticTemp ?: 0.0)) > 0.01) { haptic.fire(HapticPattern.ActionRejected); rejectTrigger++; optimisticTemp = null } } }`.
  - [x] 2.7: Reconciliation — `LaunchedEffect(entity.targetTemperature) { val opt = optimisticTemp; if (opt != null && entity.targetTemperature != null && abs(entity.targetTemperature!! - opt) <= 0.01) optimisticTemp = null }`.
  - [x] 2.8: Snap-back — reuse the `Animatable<Float>`/`graphicsLayer.translationX` driver from Story 4.3 (extract to a private `@Composable fun rememberSnapBack(rejectTrigger: Int): Float` helper if not already). Apply `Modifier.graphicsLayer { translationX = nudge }` to the `Row`.
  - [x] 2.9: A11y — row `contentDescription = "${friendlyName(entity, entity.entityId)}, target ${formatted}${appendStaleSuffix("", isStale, entity.lastChanged)}"`. Buttons: per-button `contentDescription` set inside `IconButton`'s `Icon`.
  - [x] 2.10: Define companion `private const val STEPPER_DELTA_C = 0.5`. (`OPTIMISTIC_TIMEOUT_MS` already declared in Story 4.3.)

- [x] Task 3: Trigger variant (AC: 3, 6, 7)
  - [x] 3.1: Add private `@Composable fun TriggerEntityCard(entity: HaEntity, isStale: Boolean, size: EntityCardSize)` — accepts `HaEntity` rather than the sealed subtype because both `Script` and `Scene` flow through here. Branch on `entity is HaEntity.Script` to pick `service = if (entity is HaEntity.Script) "script.turn_on" else "scene.turn_on"` (split as `domain` + `service` to `CallServiceUseCase("script","turn_on",...)` / `("scene","turn_on",...)`).
  - [x] 3.2: State: `var triggerCounter: Int by remember(entity.entityId) { mutableStateOf(0) }`. Use `Animatable<Float>(initialValue = 1f)` keyed on `triggerCounter` to drive a `1.0 → 1.04 → 1.0` scale pulse via `LaunchedEffect(triggerCounter)`. Apply via `Modifier.graphicsLayer { scaleX = scale; scaleY = scale }`.
  - [x] 3.3: Disabled gate: `val disabled = entity.state == "unavailable"`.
  - [x] 3.4: Tap handler:
    ```kotlin
    Modifier.clickable(role = Role.Button, enabled = !disabled) {
        haptic.fire(HapticPattern.ActionTriggered)
        scope.launch {
            val result = call(domain, "turn_on", entity.entityId)
            if (result.isFailure) haptic.fire(HapticPattern.ActionRejected)
            else triggerCounter++
        }
    }
    ```
  - [x] 3.5: Right-action: `Text("Run", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)` wrapped with `Modifier.minimumInteractiveComponentSize()` — since the whole row is the click target, the `"Run"` label is purely visual, so `Modifier.clearAndSetSemantics {}` on it.
  - [x] 3.6: Stale + a11y per AC6, AC7.

- [x] Task 4: Media variant (AC: 4, 6, 7)
  - [x] 4.1: Add private `@Composable fun MediaEntityCard(entity: HaEntity.MediaPlayer, isStale: Boolean, size: EntityCardSize)`.
  - [x] 4.2: Layout: `Row { domainIcon; Column(weight=1f) { Text(title, bodyLarge); Text(stateLabelString, bodyMedium, color = onSurfaceVariant) }; IconButton(onClick = onPlayPause, enabled = !disabled) { Icon(if (isPlaying) Pause else PlayArrow, ...) } }`.
  - [x] 4.3: `title = entity.mediaTitle ?: friendlyName(entity, entity.entityId)`. `isPlaying = entity.state == "playing"`. `disabled = entity.state == "unavailable"`.
  - [x] 4.4: Tap:
    ```kotlin
    val onPlayPause = {
        haptic.fire(HapticPattern.ActionTriggered)
        scope.launch {
            val result = call("media_player", "media_play_pause", entity.entityId)
            if (result.isFailure) haptic.fire(HapticPattern.ActionRejected)
        }
    }
    ```
  - [x] 4.5: No optimistic; no snap-back. WebSocket reconciles state.
  - [x] 4.6: Stale + a11y per AC6, AC7. Wrap inner `IconButton` in `Modifier.clearAndSetSemantics {}` only if its semantic node duplicates the row's — since the row has only `contentDescription` (no role), the IconButton is the canonical button node and should keep its own semantics.

- [x] Task 5: Unknown variant (AC: 5, 6, 7)
  - [x] 5.1: Add private `@Composable fun UnknownEntityCard(entity: HaEntity.Unknown, isStale: Boolean, size: EntityCardSize)`.
  - [x] 5.2: Layout: read-only `Row` (no `clickable`), `Icon(Icons.Outlined.HelpOutline)`, `Column { Text(entity.entityId, bodyLarge); Text(stateLabel(entity.state), bodyMedium, color = onSurfaceVariant) }`. No right-action.
  - [x] 5.3: NEVER index `entity.attributes` in this branch. Verify via code review.
  - [x] 5.4: A11y: `contentDescription = "${entity.entityId}, ${stateLabel(entity.state)}${appendStaleSuffix("", isStale, entity.lastChanged)}"`. No role.

- [x] Task 6: Preview matrix (AC: 8)
  - [x] 6.1: Extend `EntityCardPreviewBody` (or add per-variant `*PreviewBody` siblings) so the new variants render without Koin in `@Preview`. Pass static `HaEntity.Climate`/`Script`/`Scene`/`MediaPlayer`/`Unknown` fixtures.
  - [x] 6.2: Add `@Preview` entries listed in AC8. Wrap each in `HaNativeTheme { ... }`. For `Trigger_Active` use a `LaunchedEffect(Unit) { triggerCounter++ }` shim or pass a forced scale parameter to the preview body.
  - [x] 6.3: For `Stepper_Optimistic`/`Stepper_Error` use `forcedOptimisticTemp` and `forcedRejected` body parameters mirroring the Story 4.3 toggleable preview pattern.

- [x] Task 7: Tests (AC: helper coverage)
  - [x] 7.1: Extend `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/components/EntityCardLogicTest.kt` with cases for: `domainIcon` returns non-null for `Climate`, `Script`, `Scene`, `MediaPlayer`, `Unknown`; `appendStaleSuffix` produces the expected `, just now` / `, updated 3m ago` / empty (when `isStale=false`) outputs; `stateLabel("playing") → "Playing"`, `"paused" → "Paused"`, `"idle" → "Idle"`.
  - [x] 7.2: Defer Compose UI tests (no runner wired). Add `// TODO(Story 4.7-or-later): stepper +/- composition test` comment.
  - [x] 7.3: Run `./gradlew :shared:testAndroidHostTest` — must pass.
  - [x] 7.4: Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — must link.

- [x] Task 8: Domain layer purity audit (AC: project rule)
  - [x] 8.1: `grep -r "androidx.compose" shared/src/commonMain/kotlin/com/backpapp/hanative/domain/` — must return empty (no regression).
  - [x] 8.2: `EntityCard.kt` imports unchanged in scope: `androidx.compose.*`, `domain.usecase.*`, `domain.model.*`, `platform.HapticPattern`, `platform.LocalHapticEngine`, `ui.theme.Motion`. Must NOT import `data.*`/`ktor.*`/`sqldelight.*`.

## Dev Notes

### Architecture Compliance

- **Single composable file** — `EntityCard.kt` continues to be the only entry point per Story 4.3's design. New variants are private composables in the same file. This avoids API surface explosion and keeps preview wiring centralised.
- **No ViewModel** for individual cards — same boundary as Story 4.3. Dashboard-level orchestration arrives in Story 4.6.
- **Fire-and-forget vs optimistic** — stepper has true optimistic state (target temp); media and trigger do not. Stepper uses the same 5s timeout + snap-back framework as toggleable. Media trusts the WebSocket round-trip. Trigger is fire-and-forget with a brief scale pulse on success.
- **Layer purity** — same rules as Story 4.3. No new `data/`, `ktor/`, or `sqldelight/` imports in commonMain UI.

### `HapticPattern` Coverage

`HapticEngineModule` (Story 2.3) exposes `StepperInc`, `StepperDec`, `ActionTriggered`, `ActionRejected` patterns — all four are pre-existing. No new pattern types are introduced by this story.

### Climate Service Contract

HA's `climate.set_temperature` accepts `temperature: Float` (single setpoint) for `heat`/`cool` modes and `target_temp_high`/`target_temp_low` for `heat_cool` mode. **Story 4.4 only handles the single-setpoint case.** Heat-cool dual-setpoint UI is deferred — for `heat_cool` mode entities, the stepper sends `temperature` and HA may interpret it inconsistently. Document in code as `// Heat-cool dual-setpoint deferred to Story 4.4.x or later`.

Reference: `https://www.home-assistant.io/integrations/climate/#service-climateset_temperature` — confirm via context7 `homeassistant` docs if behaviour shifts in the user's HA version.

### Media Player Optimistic Decision

Spec line in epics.md says "state updates via WebSocket subscription" — explicitly NOT optimistic. Rationale: `media_play_pause` is asymmetric (toggles play↔pause), and many media players have multi-second buffering before state flips. An optimistic flip would frequently be wrong. Trust HA's authoritative state.

### Unknown Variant — Why FR11

Per FR11, V1 supports a finite set of domains explicitly. Anything else falls through to `HaEntity.Unknown` (see `HaEntity.kt:138`). The `Unknown` card MUST render without crash regardless of `attributes` shape — common foot-guns are `attributes["unit_of_measurement"]` casts, `attributes["friendly_name"]` casts, etc. Branch must avoid all `attributes` reads to satisfy this.

### Snap-Back Animation Reuse

Story 4.3's review patches established the canonical snap-back: `Animatable<Float>` keyed on `rejectTrigger: Int` counter, sequential `animateTo(-8.dp.toPx())` then `animateTo(0f)`. Pull this into a private helper `@Composable fun rememberSnapBackOffset(rejectTrigger: Int): Float` if not already extracted, then reuse for the stepper variant. Trigger and Media variants do NOT need snap-back (no optimistic state to revert).

### Locale-Safe Number Formatting on KMP

`String.format("%.1f", x)` works on JVM (Android target) but NOT on Kotlin/Native iOS target (no `String.format` in commonMain stdlib). Recommended approaches in commonMain:
1. `((x * 10).roundToInt() / 10.0).toString() + "°"` — simple, no decimal locale separator handling.
2. `kotlin.math.round` + manual fractional concat.
3. Define `expect/actual fun formatTemperature(value: Double, decimals: Int): String` if better locale handling matters.

For Story 4.4, option 1 is sufficient — half-degree increments mean the displayed value is always `.0` or `.5`. Document the choice.

### `compose.materialIconsExtended` Icon Name Verification

Story 4.3's Task 1 added `implementation(compose.materialIconsExtended)` to `commonMain`. Confirm the following names resolve before commit:
- `Icons.Outlined.Thermostat` (climate)
- `Icons.Outlined.PlayArrow`, `Icons.Outlined.Pause`, `Icons.Outlined.PlayCircle` (media)
- `Icons.Outlined.Add`, `Icons.Outlined.Remove` (stepper buttons)
- `Icons.Outlined.HelpOutline` (unknown)
- `Icons.Outlined.AutoAwesome` (scene — fallback to `Lightbulb` if absent)

If any are missing, substitute the closest available — record the substitution in `Change Log`.

### Project Structure — Files Touched

```
shared/src/commonMain/kotlin/com/backpapp/hanative/
  └── ui/
      └── components/
          └── EntityCard.kt                          ← MODIFIED (variants added)

shared/src/androidMain/kotlin/com/backpapp/hanative/
  └── ui/
      └── components/
          └── EntityCardPreviews.kt                  ← MODIFIED (previews appended)

shared/src/commonTest/kotlin/com/backpapp/hanative/
  └── ui/
      └── components/
          └── EntityCardLogicTest.kt                 ← MODIFIED (helper coverage)
```

**Do NOT modify:**
- `HaEntity.kt` — sealed class is frozen for V1 domains. New domains arrive via FR-defined epics.
- `CallServiceUseCase.kt`, `ObserveEntityStateUseCase.kt` — Story 4.1 contracts.
- `HapticFeedback.kt` — Story 2.3 contract; all needed patterns already exist.
- `Motion.kt` / `Color.kt` / `HaNativeTheme.kt` — consumed as-is.
- `DataModule.kt` / `DomainModule.kt` — no DI changes.
- `EntityState.sq`, `Dashboard*.sq`, `EntityRepositoryImpl.kt` — out of scope.

### Previous Story Intelligence

**Story 4.3 (toggleable + read-only)** — established:
- `EntityCardBody` extracted for preview Koin-isolation.
- Snap-back via `Animatable` + sequential `animateTo` keyed on a `rejectTrigger: Int` counter (NOT a `Boolean` — fixes the permanently-stuck-at-(-8) bug from initial implementation).
- `dp → px` conversion via `LocalDensity.current` for the nudge offset (raw float was px, not dp).
- `Modifier.clearAndSetSemantics {}` applied to inner controls that duplicate the row's role/state.
- Stale suffix appended to BOTH visible text and `contentDescription`.
- `appendStaleSuffix` "just now" wording for sub-1-min deltas; comma separator (not bullet `•`) per spec literal.
- `pointerInput(entityId)` keyed only on `entityId`; `displayedOn` snapshotted via `rememberUpdatedState` to avoid mid-press cancellation.
- A11y activation via layered `Modifier.toggleable` PLUS `interactionSource` PressInteraction collector — TalkBack/keyboard/Switch Access dispatch synthetic clicks that go to `toggleable`, while touch path stays touch-down via `interactionSource`. Stepper does NOT need this layering — `IconButton` already routes through `clickable` and assistive activation works natively. Trigger uses `clickable(role=Button)` directly. Media uses `IconButton` directly.
- `unavailable`/`unknown` state gates haptic + service call. Apply same gate to stepper +/- and media play/pause.

**Story 4.2 (dashboard persistence)** — `DashboardCard.entityId` resolves to whatever subtype `HaEntity.kt` factory returns; no schema changes needed for new variants.

**Story 4.1 (entity pipeline)** — `ObserveEntityStateUseCase(entityId)` returns `Flow<HaEntity?>`. All variants must handle `null` (entity not yet loaded). Shared by all variant branches.

### Testing Standards

- `kotlin.test` — never JUnit4/5 in `commonTest`.
- `./gradlew :shared:testAndroidHostTest` — runs commonTest on JVM.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — verifies iOS Native compile path; required because `String.format` and other JVM-only APIs slip in easily.
- Compose UI test runner is NOT wired — defer composition tests, helper-fn tests are sufficient.
- `assertEquals(expected, actual)` — not `assert(x == y)`.

### Git Intelligence

| Commit  | What it established                                                                           |
| ------- | --------------------------------------------------------------------------------------------- |
| `1a3f281` | Story 4.3 toggleable + read-only EntityCard, review patches (snap-back fix, a11y, debounce) |
| `e158f57` | Story 4.2 dashboard persistence (Dashboard.sq, DashboardCard.sq, repo, 6 use cases)         |
| `0adc61c` | Story 4.1 review patches: `Mutex`, `bind` Koin, `last_changed` schema                       |
| Story 2.2 | `Motion.kt`: `snapBackRejection`, `entityStateChange`, `staleIndicatorFade`                |
| Story 2.3 | `HapticEngine`, `HapticPattern` (incl. `StepperInc`/`StepperDec`/`ActionTriggered`)        |

Codebase state confirmed before story creation:
- `HaEntity.kt` exposes `Climate`, `Script`, `Scene`, `MediaPlayer`, `Unknown` sealed subtypes (all needed properties present: `targetTemperature`, `currentTemperature`, `mediaTitle`, etc.).
- `HapticPattern.kt` defines `StepperInc`, `StepperDec`, `ActionTriggered` (verified at `HapticFeedback.kt:8-10`).
- `CallServiceUseCase(domain, service, entityId, serviceData)` returns `Result<Unit>` with optional `serviceData` map (default empty).
- `EntityCard.kt` from Story 4.3 has the toggleable + read-only structure; this story extends.
- `EntityCardPreviews.kt` has 10 existing previews to extend.

### References

- [Source: `_bmad/outputs/epics.md#Story 4.4`] — Acceptance criteria
- [Source: `_bmad/outputs/architecture.md#Process Patterns`] — Optimistic UI control pattern (stepper applies, media intentionally opts out)
- [Source: `_bmad/outputs/architecture.md#FR → Directory Mapping`] — `ui/components/` placement
- [Source: `_bmad/outputs/ux-design-specification.md`] — EntityCard variants, haptic mapping, accessibility
- [Source: `_bmad/stories/4-3-core-entitycard-toggleable-readonly-variants.md`] — Toggleable + read-only baseline this story extends
- [Source: `_bmad/stories/4-1-sqldelight-schema-entity-state-pipeline.md`] — `ObserveEntityStateUseCase` flow contract
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/HaEntity.kt`] — sealed subtypes
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/HapticFeedback.kt:8-10`] — Stepper + ActionTriggered patterns
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/CallServiceUseCase.kt`] — `(domain, service, entityId, serviceData)` signature
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/theme/Motion.kt`] — `snapBackRejection`, `entityStateChange`

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 (bmad-dev-story)

### Debug Log References

- `./gradlew :shared:testAndroidHostTest` → BUILD SUCCESSFUL (incl. new `AppendStaleSuffixTest`, `FormatTempTest`, extended `DomainIconTest` for Climate/Script/Scene/MediaPlayer/Unknown).
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (no `String.format` regression; `formatTemp` uses round + cat).
- Initial test compile failure: `kotlin.time.Clock.System.now()` returns `kotlin.time.Instant`, not `kotlinx.datetime.Instant`. Switched test helpers to `kotlinx.datetime.Clock.System.now()`.

### Completion Notes List

- Variant dispatch added to `EntityCardBody` for `Climate` → Stepper, `Script`/`Scene` → Trigger, `MediaPlayer` → Media, `Unknown` → Unknown. Existing Toggleable + ReadOnly branches untouched.
- Public `EntityCard(entityId, modifier, size, isStale)` API unchanged. `EntityCardBody` gained `call: CallServiceUseCase?`, `forcedOptimisticTemp: Double?`, and `forcedTriggerPulse: Boolean` for preview parameterisation.
- `appendStaleSuffix(base, isStale, lastChanged)` extracted as shared helper (replaces previous private `staleSuffix(isStale, entity)` lambda); reused across all variants for both visible text and `contentDescription`.
- Stepper: optimistic `optimisticTemp`, 5s timeout, snap-back -8dp via `Animatable` keyed on `rejectTrigger`, `STEPPER_DELTA_C = 0.5`, locale-safe `formatTemp` (KMP iOS-Native compatible — no `String.format`).
- Trigger: fire-and-forget `Modifier.clickable(role = Role.Button)`; `1.0 → 1.04 → 1.0` scale pulse via `Animatable<Float>` keyed on `triggerCounter` using `Motion.entityStateChange`; no optimistic / no snap-back.
- Media: `IconButton` for play/pause, no optimistic state (rationale documented in Dev Notes), reconciles via WebSocket round-trip; row-level a11y + IconButton's own `Pause`/`Play` content-description (no `clearAndSetSemantics` needed).
- Unknown: read-only, no `attributes` reads anywhere — verified by code inspection. Preview includes random `attributes` shape to demonstrate non-crash.
- Domain purity audit: `grep -r "androidx.compose" shared/src/commonMain/kotlin/com/backpapp/hanative/domain/` → empty. EntityCard imports limited to `androidx.compose.*`, `domain.*`, `platform.HapticPattern`, `platform.LocalHapticEngine`, `ui.theme.Motion`. No `data.*` / `ktor.*` / `sqldelight.*` regressions.
- All 17+ `@Preview` entries added to `EntityCardPreviews.kt`. `Trigger_Optimistic` is identical to `Trigger_Active` per spec (fire-and-forget) and is documented inline.
- Material icons used: `Thermostat`, `Add`, `Remove`, `Pause`, `PlayArrow`, `PlayCircle`, `AutoAwesome` (scene), `HelpOutline`. All resolved from `compose.materialIconsExtended`. (Compiler emits a deprecation warning suggesting `Icons.AutoMirrored.Outlined.HelpOutline` — non-blocking, tracked for future cleanup.)

### File List

- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityCard.kt` — MODIFIED (added stepper/trigger/media/unknown variants, `appendStaleSuffix`, `formatTemp`, extended `domainIcon`).
- `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityCardPreviews.kt` — MODIFIED (17 new `@Preview` entries).
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/components/EntityCardLogicTest.kt` — MODIFIED (added `AppendStaleSuffixTest`, `FormatTempTest`, extended `DomainIconTest` and `StateLabelTest`).

### Change Log

- 2026-05-01 — Story 4.4 created via `bmad-create-story`. Status: ready-for-dev.
- 2026-05-01 — Story 4.4 implemented via `bmad-dev-story`. Stepper/Trigger/Media/Unknown variants added inside `EntityCard.kt`. Status: review.
- 2026-05-01 — Story 4.4 BMAD code review complete. 3 decisions resolved (clamp, failure shake, reactive stale), 12 patches applied: stepper min/max clamping with HA `target_temp_step`; failure shake on Trigger + Media; `rememberStaleSuffix` ticker; `formatTemp` frac=10 / sign-loss fixes; UNINTERACTABLE_STATES on Trigger/Media; trigger duplicate Role.Button removed; mediaTitle blank check; Media + Unknown title `maxLines = 1, overflow = Ellipsis`; stepper IconButton a11y consolidated; stepper Animatable initial reset; stepper a11y "target" wording when targetTemperature null. 8 deferred (subtle stepper races + `else`-branch fallback). All tests pass; iOS link green. Status: done.

### Review Findings

**Triage summary:** 3 decision-needed, 9 patches, 8 deferred, 22 dismissed (noise/false-positive/by-design). Layers: Blind Hunter ✅, Edge Case Hunter ✅ (re-launched as general-purpose; bmad subagent type missing), Acceptance Auditor ✅ (all 11 ACs substantively satisfied — no AC violations).

#### Decisions (resolved)

- [x] [Review][Decision→Patch] Stepper min/max clamping → **resolved 1a**: clamp using HA-reported `attributes["min_temp"]`/`["max_temp"]`/`["target_temp_step"]`.
- [x] [Review][Decision→Patch] Failure visual on Trigger/Media → **resolved 2b**: add brief shake/pulse animation for failure.
- [x] [Review][Decision→Patch] Stale suffix not reactive → **resolved 3a**: per-card minute ticker via `produceState`.

#### Patches

- [x] [Review][Patch] Stepper clamp `next` to HA `min_temp`/`max_temp` from `entity.attributes` (use `target_temp_step` if present to override `STEPPER_DELTA_C`); when at bound, no-op service call + fire `ActionRejected` haptic [EntityCard.kt StepperEntityCard] — from Decision 1a.
- [x] [Review][Patch] Add brief shake/pulse failure animation on Trigger and Media variants — reuse Stepper snap-back `Animatable` translationX -8dp pattern, keyed on a `rejectTrigger` counter incremented on `result.isFailure` [EntityCard.kt TriggerEntityCard, MediaEntityCard] — from Decision 2b.
- [x] [Review][Patch] Make stale suffix reactive — wrap `appendStaleSuffix` call in `produceState` ticking every 60s while `isStale && lastChanged != null`, so `"3m ago"` advances over time [EntityCard.kt appendStaleSuffix call sites] — from Decision 3a.
- [x] [Review][Patch] `formatTemp` floating-point drift can produce `frac == 10` → output like `"20.10°"` for inputs near integer boundaries [shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityCard.kt:formatTemp] — Add normalization: `if (frac == 10) { whole += 1; frac = 0 }`.
- [x] [Review][Patch] `formatTemp` sign loss for negative-near-zero values — `formatTemp(-0.04)` rounds to `0.0` and outputs `"0.0°"` losing original sign; nondeterministic at half-tie boundary [EntityCard.kt:formatTemp] — Compute sign from input first, format magnitude.
- [x] [Review][Patch] Trigger / Media `disabled` ignores `"unknown"` state — Spec UNINTERACTABLE_STATES is `{unavailable, unknown}` for Toggleable/Stepper but Trigger and Media check only `state == "unavailable"` [EntityCard.kt TriggerEntityCard, MediaEntityCard] — Use `entity.state in UNINTERACTABLE_STATES`.
- [x] [Review][Patch] Trigger applies `Role.Button` twice (in `clickable(role=...)` and in trailing `.semantics { role = ... }`) [EntityCard.kt TriggerEntityCard] — Drop the duplicate from the semantics block; `clickable` already sets it.
- [x] [Review][Patch] Media `mediaTitle` empty string passes through as blank title [EntityCard.kt MediaEntityCard] — `entity.mediaTitle?.takeIf { it.isNotBlank() } ?: friendlyName(entity, entity.entityId)`.
- [x] [Review][Patch] Media + Unknown title `Text` has no `maxLines` / `overflow`; long titles wrap and break grid alignment [EntityCard.kt MediaEntityCard, UnknownEntityCard] — Add `maxLines = 1, overflow = TextOverflow.Ellipsis` to title `Text`.
- [x] [Review][Patch] Stepper IconButtons emit two semantics nodes per +/- (IconButton's Role.Button + child Icon's contentDescription) [EntityCard.kt StepperEntityCard] — Pass `contentDescription = null` on inner `Icon`; set the description on the `IconButton` via `.semantics { contentDescription = ... }`.
- [x] [Review][Patch] `Stepper_Error` preview's `Animatable(initialValue = nudgePx)` is immediately reset by `LaunchedEffect(rejectTrigger).snapTo(0f)`; first frame flashes -8dp then animates [EntityCard.kt StepperEntityCard] — Drop the conditional initial value; rely on `LaunchedEffect` to drive from rest.
- [x] [Review][Patch] Stepper a11y `contentDescription` says `"target X.X°"` even when no target exists (displayedTemp falls to currentTemperature or 0.0) — misleading for `state == "off"` climates [EntityCard.kt StepperEntityCard] — When `entity.targetTemperature == null`, use `"current ${formatted}"` or `"${stateLabel}"` framing instead of `"target …"`.

#### Deferred

- [x] [Review][Defer] Stepper rapid-tap timer reset — every tap re-keys `LaunchedEffect(optimisticTemp)`, restarting the 5s timeout; under sustained interaction the reject path may never fire [EntityCard.kt StepperEntityCard] — deferred, design tension; revisit after Story 4.6 dashboard usage data.
- [x] [Review][Defer] Stepper reconcile race — `LaunchedEffect(entity.targetTemperature)` only re-keys on value change; HA echo with target unchanged (e.g. server-side rejection echoes prior value) leaves optimistic pending until 5s timeout [EntityCard.kt StepperEntityCard] — deferred, requires `lastUpdated` reactive trigger.
- [x] [Review][Defer] Stepper optimistic-timeout LaunchedEffect captures `entity.targetTemperature` at launch, not after delay — successful HA echo arriving mid-delay can be rejected by stale snapshot [EntityCard.kt StepperEntityCard] — deferred, subtle race; same fix family as above.
- [x] [Review][Defer] Stepper reject path clears `optimisticTemp` unconditionally — if the user has tapped twice and the first call fails after the second is in flight, the second optimistic value is wiped [EntityCard.kt StepperEntityCard] — deferred, multi-call coordination work.
- [x] [Review][Defer] `appendStaleSuffix` future-timestamp / clock-skew → negative delta still falls into `"just now"` branch (delta < 60_000 covers negatives) — pre-existing pattern from Story 4.3.
- [x] [Review][Defer] `appendStaleSuffix` very large deltas render as `"…1051200m ago"` (no hour/day bucketing) — pre-existing from Story 4.3, deferred to dashboard polish.
- [x] [Review][Defer] `else` branch in `EntityCardBody` covers `Cover` and `InputSelect` via `ReadOnlyEntityCard` — pre-existing fallback; pure UI strings ("Open"/"Closed") flow through `stateLabel`. Future `Cover` story will add proper variant.
- [x] [Review][Defer] Future `HaEntity` sealed subtypes silently fall to `ReadOnlyEntityCard` via `else` — design pattern; revisit when extending the sealed hierarchy.

#### Dismissed

22 findings dismissed as noise, false-positives, by-design, or covered by other items: `forcedTriggerPulse` initial-frame ergonomics (preview-only); `Media.disabled` flag dead-on-row (row not clickable by design); `stateLabel` test pre-existing; preview `current=null` showing "0.0°" (spec-aligned); `call: CallServiceUseCase?` nullable preview pattern; `clearAndSetSemantics` on "Run" Text (intentional — row carries a11y); Unknown title using `entityId` (correct fallback for unmodelled domains); Stale `else` doc comment minor; pulse counter race (acceptable); play/pause icon for `buffering`/`on` (semantically OK); entityId without dot (extreme edge); delta exactly 60s (off-by-one acceptable); RTL `mediaTitle` (system-handled); `optimisticTemp` keyed on entityId (by design); `Animatable` density not re-keyed (extreme edge); `Trigger_Error` preview ambiguity (preview-only); `Trigger_Optimistic == Trigger_Active` (spec-mandated equivalence); `appendsMinutesForOlderDelta` test using real Clock (acceptable); missing `formatTemp frac=10` test (covered by patch); missing future-timestamp test (low value); `mutableLongStateOf` vs `Int` (functionally equivalent); `B3/E1` stepper "0.0°" timeout false-positive (spec-mandated literal — covered by Decision 1).
