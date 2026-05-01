# Story 4.5: EntityPicker Bottom Sheet

Status: done

## Story

As a power user,
I want to browse and add entities sorted by what I've used most recently,
so that building my first dashboard takes minutes, not half an hour of scrolling through 180 entities.

## Acceptance Criteria

1. **`EntityPicker` composable** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPicker.kt`. Public signature:
   ```kotlin
   @Composable
   fun EntityPicker(
       isVisible: Boolean,
       onDismiss: () -> Unit,
       onEntitySelected: (HaEntity) -> Unit,
       modifier: Modifier = Modifier,
   )
   ```
   Implemented as M3 `androidx.compose.material3.ModalBottomSheet` (commonMain — Compose Multiplatform M3 supports it across Android + iOS targets). Sheet shows when `isVisible == true`; tapping scrim or system back invokes `onDismiss`. Selecting a row invokes `onEntitySelected(entity)` then `onDismiss()` — caller is responsible for the `AddCardUseCase` dispatch.

2. **Domain filter chips** render at the top of the sheet using `androidx.compose.material3.FilterChip` inside a horizontally scrollable `Row` with `horizontalArrangement = Arrangement.spacedBy(8.dp)` and `Modifier.horizontalScroll(rememberScrollState())`. One chip per supported domain in this order: `light`, `switch`, `input_boolean`, `climate`, `cover`, `media_player`, `script`, `scene`, `sensor`, `binary_sensor`, `input_select`. A leading `"All"` chip is the default selected state. Tapping a domain chip filters the list below; tapping `"All"` clears the filter. Chip labels use the existing `stateLabel`-style human formatter for domain names (e.g. `"Media Player"` for `media_player`, `"Input Boolean"` for `input_boolean`) — implement via a private `humanizeDomain(raw: String): String` helper in `EntityPicker.kt` (split on `_`, title-case each segment).

3. **Entity list** is sourced from a new `GetSortedEntitiesUseCase` (see AC8). The list is activity-sorted descending by `HaEntity.lastUpdated` (FR18) — the underlying `EntityState.sq#selectAllEntityStates` already sorts `ORDER BY last_updated DESC`, but the use case re-sorts on the in-memory `StateFlow` to remain correct after WebSocket-driven updates push entries out of insertion order. `HaEntity.Unknown` instances are excluded from the picker (they cannot be added as cards in V1 per FR11).

4. **Row anatomy** mirrors `EntityCard` visual language without re-using the `EntityCard` composable itself (read-only, no Koin, no haptic, no service call):
   - `Row(verticalAlignment = CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp))`
   - `[icon: domainIcon(entity)]` at start (24dp), then 16dp gap.
   - `Column(weight = 1f)`: line 1 = `friendlyName(entity, entity.entityId)` (`bodyLarge`, `maxLines = 1`, `overflow = Ellipsis`); line 2 = `stateLabel(entity.state)` (`bodyMedium`, `color = MaterialTheme.colorScheme.onSurfaceVariant`, `maxLines = 1`).
   - No right-action.
   - Row uses `Modifier.clickable(role = Role.Button) { onEntitySelected(entity); onDismiss() }`.
   - Reuses the existing `friendlyName`, `stateLabel`, `domainIcon` helpers from `EntityCard.kt`. **Promote these three helpers from `private` to `internal` visibility** in `EntityCard.kt` so `EntityPicker.kt` can depend on them — do not duplicate.

5. **States** rendered inside the sheet body:
   - `loading` — when `useCase().value` is empty AND repository has not yet completed the initial cache hydration. Render 6 skeleton `EntityPickerSkeletonRow` composables: each is the row skeleton from M3 — a 24dp circle placeholder + two stacked rounded rectangles (`width.fillMaxWidth(0.6f)` and `0.4f`), animated via an infinite-transition shimmer alpha (`Animatable<Float>` from `0.3f → 0.7f`, `tween(800)`, `RepeatMode.Reverse`). NOT a `CircularProgressIndicator`.
   - `loaded` — at least one entity post-filter; render `LazyColumn` with `key(entity.entityId)`.
   - `empty-domain` — filter chip selected AND filtered list is empty. Render a centered `Column` with the chip's `domainIcon` (40dp, `tint = colorScheme.onSurfaceVariant`) above `Text("No ${humanizeDomain(domain)} entities found in your HA", style = MaterialTheme.typography.bodyMedium)`.
   - `empty-all` — `"All"` chip selected AND repository returned zero entities (extremely rare — disconnected fresh install). Render the same centered layout with `Icons.Outlined.Sensors` and `"No entities yet — connect to Home Assistant to populate this list"`. Distinguish from `loading` by checking `repository.entities.value.isEmpty() && initialLoadComplete`.

6. **Performance** — picker open-to-populated must be ≤500ms for up to 500 entities (NFR4). Implementation guardrails:
   - Sort happens once per emission of the upstream `entities: StateFlow<List<HaEntity>>` via `Flow.map { … sortedByDescending { it.lastUpdated } }`; the `LazyColumn` consumes via `collectAsState` — no per-frame sort.
   - Filter is applied via `derivedStateOf { all.filter { … } }` keyed on `(selectedDomain, all)` — no recomposition cost when scrolling.
   - `LazyColumn` `key = { it.entityId }` to keep recomposition stable.
   - No `Modifier.animateItemPlacement()` in this story (deferred — adds reorder cost on every WebSocket tick).

7. **Motion** — sheet open uses M3 `ModalBottomSheet`'s default animation (which already implements stiffness ≈ 400 / damping ≈ 0.8). Do NOT override; document in Dev Notes that this matches `Motion.bottomSheetOpen`'s targeted feel. Swipe-down dismisses without invoking `onEntitySelected` — `ModalBottomSheet`'s default `onDismissRequest` callback is the only signal we observe.

8. **`GetSortedEntitiesUseCase`** lives at `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/GetSortedEntitiesUseCase.kt`:
   ```kotlin
   class GetSortedEntitiesUseCase(private val repository: EntityRepository) {
       operator fun invoke(): Flow<List<HaEntity>> =
           repository.entities
               .map { list ->
                   list.asSequence()
                       .filter { it !is HaEntity.Unknown }
                       .sortedByDescending { it.lastUpdated }
                       .toList()
               }
               .distinctUntilChanged()
   }
   ```
   Registered as `factory { GetSortedEntitiesUseCase(get()) }` in `DomainModule.kt`. **No new repository method.** This use case is the picker's sole data source.

9. **Selection** — when a row is tapped:
   - The composable invokes the caller-supplied `onEntitySelected(entity)` callback. The picker itself does NOT depend on `AddCardUseCase` — the AC5 epic spec phrasing ("selecting an entity calls AddCardUseCase") is fulfilled by the **caller** (Dashboard screen, Story 4.6) which wraps the picker. This separation keeps `EntityPicker` reusable for the future "context engine entity picker" (FR38).
   - For Story 4.5 verification, ship a thin Compose preview host that wires `onEntitySelected` to a `println` no-op (Koin-isolated — same pattern Story 4.3 established for `EntityCard` previews).

10. **Accessibility**:
    - `ModalBottomSheet` already exposes the system `dismiss` semantic. No additional sheet-level a11y needed.
    - Each filter chip: `Modifier.semantics { contentDescription = "Filter by ${humanizeDomain(domain)}" }` plus `selected = (selectedDomain == domain)`. `FilterChip` already announces selected state.
    - Each row: `Modifier.semantics { contentDescription = "${friendlyName(entity, entityId)}, ${stateLabel(entity.state)}, add to dashboard"; role = Role.Button }`.
    - Skeleton rows: `Modifier.semantics { contentDescription = "Loading entities" }` on the first skeleton only (the rest get `Modifier.clearAndSetSemantics {}`) — avoids 6× repetition to TalkBack.
    - Empty state: the centered text node carries the description; the icon uses `contentDescription = null` (decorative).
    - All interactive elements wrapped with `Modifier.minimumInteractiveComponentSize()` (48dp floor).

11. **`@Preview` matrix** — append to `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityPickerPreviews.kt` (new file, follows the existing `EntityCardPreviews.kt` pattern). Cover: `Picker_Loading`, `Picker_Loaded_AllDomains` (8+ entities across 5+ domains), `Picker_Loaded_FilteredLight`, `Picker_EmptyDomain` (climate filter, no climate entities), `Picker_EmptyAll`, `Picker_LongFriendlyName` (entity with `friendly_name` exceeding row width — verify ellipsis), `Picker_StaleEntities` (entities with `lastUpdated` 1h+ old — sorting verification). Total ≥ 7 previews. Each wraps `HaNativeTheme { … }`.

12. **No new domain-layer dependencies** — `EntityRepository`, `HaEntity`, `kotlinx.coroutines.flow.*` already exist. **No** `data/`, `ktor/`, or `sqldelight/` imports in commonMain UI. **No** new `expect/actual` declarations.

13. **Recomposition isolation** — selection of a filter chip recomposes only the chip row + the `LazyColumn` content, not the sheet container. Verify by adding a `SideEffect { recompositionCount++ }` in `EntityPickerSheetContent`'s root and confirming it does not increment on chip selection. (Manual verification step — add comment in code, do not commit the counter.)

14. **No `EntityPicker` UI test** in this story — Compose UI test runner is not wired (per Story 4.3 + 4.4 deferred work). Helper-function tests (sort, filter, humanize) are required.

## Tasks / Subtasks

- [x] Task 1: Promote `EntityCard` helpers + add `GetSortedEntitiesUseCase` (AC: 4, 8)
  - [x] 1.1: Helpers (`friendlyName`, `stateLabel`, `domainIcon`, `appendStaleSuffix`, `formatTemp`, `rememberStaleSuffix`) in `EntityCard.kt` confirmed already `internal` from Story 4.4 — no change needed.
  - [x] 1.2: Created `GetSortedEntitiesUseCase.kt` per AC8.
  - [x] 1.3: Registered `factory { GetSortedEntitiesUseCase(get()) }` in `DomainModule.kt`.
  - [x] 1.4: Added `GetSortedEntitiesUseCaseTest.kt` covering Unknown exclusion, lastUpdated DESC sort, lastUpdated-not-lastChanged ordering, and `distinctUntilChanged` collapse.

- [x] Task 2: `EntityPicker.kt` skeleton + state machine (AC: 1, 5, 6, 7)
  - [x] 2.1: Created `EntityPicker.kt` with public AC1 signature.
  - [x] 2.2: Hoisted `selectedDomain` via `rememberSaveable` and `sheetState` via `rememberModalBottomSheetState`.
  - [x] 2.3: `ModalBottomSheet` wraps body `Column` of chip row + state content.
  - [x] 2.4: Private `EntityPickerState` sealed class.
  - [x] 2.5: `koinInject<GetSortedEntitiesUseCase>()` collected via `collectAsState`; `LaunchedEffect(Unit) { delay(800); loadDeadlineHit = true }` deadline drives initial-load gate.
  - [x] 2.6: `derivedStateOf` keyed on `(entities, selectedDomain, loadDeadlineHit, forcedLoading)`.

- [x] Task 3: Filter chip row (AC: 2, 10)
  - [x] 3.1: Private `DomainFilterRow` with leading "All" chip then 11 domains in AC2 order.
  - [x] 3.2: `humanizeDomain` (internal for test access) covered by `HumanizeDomainTest`.
  - [x] 3.3: Tap-active-chip clears filter (matches "All" semantic).
  - [x] 3.4: A11y `contentDescription = "Filter by …"` + `selected` semantic on each chip; `minimumInteractiveComponentSize`.

- [x] Task 4: Loaded list (AC: 4, 5, 6, 10)
  - [x] 4.1: Private `EntityPickerRow` per AC4 anatomy reusing `friendlyName`/`stateLabel`/`domainIcon` from `EntityCard.kt`.
  - [x] 4.2: `LazyColumn` capped at `heightIn(max = 480.dp)`, `key = { it.entityId }`.
  - [x] 4.3: Row a11y carries `"<name>, <state>, add to dashboard"` + `Role.Button`.

- [x] Task 5: Skeleton + empty states (AC: 5, 10)
  - [x] 5.1: `EntityPickerSkeletonRow` draws 24dp circle + two rounded-rect lines (60% / 40% width).
  - [x] 5.2: Single `rememberInfiniteTransition` at `LoadingContent` parent shared across 6 skeletons.
  - [x] 5.3: `EntityPickerEmptyState` centered Column `heightIn(min = 280.dp)`.
  - [x] 5.4: First skeleton announces "Loading entities"; remainder `clearAndSetSemantics`. Empty-state icon `contentDescription = null` (decorative).

- [x] Task 6: Previews (AC: 11)
  - [x] 6.1: Created `EntityPickerPreviews.kt` Koin-free.
  - [x] 6.2: `EntityPickerBody` (Koin-free) takes `entities: List<HaEntity>` directly; public `EntityPicker` wires Koin.
  - [x] 6.3: 7 `@Preview` entries each wrapped in `HaNativeTheme`.

- [x] Task 7: Verification (AC: 6, 14)
  - [x] 7.1: `./gradlew :shared:testAndroidHostTest` — BUILD SUCCESSFUL (104 tests pass incl. `GetSortedEntitiesUseCaseTest`, `HumanizeDomainTest`, `PickerDomainsListTest`).
  - [x] 7.2: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL. Compose Multiplatform 1.10.0 (well above the 1.6 floor) — `ModalBottomSheet` available in commonMain on iOS Native.
  - [x] 7.3: `grep -r "androidx.compose" shared/src/commonMain/kotlin/.../domain/` — empty.
  - [x] 7.4: `grep -rE "data\.|ktor\.|sqldelight\." …/EntityPicker.kt` — empty.

## Dev Notes

### Architecture Compliance

- **Reusable component, no caller assumption** — `EntityPicker` is a generic entity-selection bottom sheet. The Story 4.6 Dashboard screen will host it; a future Story 5.x context-engine rule builder (FR38) will host it identically. Hosting code passes `onEntitySelected` and decides what to do with the result. The picker itself never references `AddCardUseCase`, `DashboardCard`, or any dashboard concept.
- **No ViewModel** — picker state is local Compose state (`selectedDomain`, derived list). State that crosses navigation boundaries belongs to the host. This matches Story 4.3's no-ViewModel-for-cards stance; Story 4.6 introduces `DashboardViewModel` and that VM owns the `isVisible` flag.
- **Domain-layer purity** — `GetSortedEntitiesUseCase` is the only new domain artefact. No new repository methods. Repository sort by `last_updated DESC` is preserved at the data layer; the use case's secondary sort handles in-memory updates that arrive out-of-order.

### `friendly_name` Resolution

`friendlyName(entity, entityId)` already reads `entity.attributes["friendly_name"] as? String ?: entityId.substringAfter(".").replace("_", " ").titleCase()`. The picker shows whichever is present — same priority as `EntityCard`. **Do not introduce a separate "picker name" formatter.** Visual parity is the spec.

### Domain Filter Order Rationale

Order in AC2 is roughly "controllable first, observed last" — toggleables (light/switch/input_boolean), then steppers (climate), then triggers (cover, media_player, script, scene), then read-only (sensor, binary_sensor, input_select). Matches the dashboard-add mental model: "what do I want to control?" first.

### Skeleton Shimmer — Avoid Material3-Internal API

M3 has an internal `Modifier.placeholder` in `compose.material3:material3-window-size-class` but not in core. Implement shimmer manually via `rememberInfiniteTransition().animateFloat(initialValue = 0.3f, targetValue = 0.7f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse))`. The single transition is shared across all 6 rows — one recomposition driver, six consumers. This avoids the third-party `accompanist-placeholder` dependency.

### `ModalBottomSheet` on iOS Native

Compose Multiplatform 1.6+ ships `ModalBottomSheet` in `androidx.compose.material3` for iOS Native. **Verify the version pin first** (`gradle/libs.versions.toml` → `compose-multiplatform` and `compose-material3`). If the project is below 1.6, escalate — do not roll a custom bottom sheet for this story. Story 4.4 verified `compose.materialIconsExtended` works in commonMain, which is a strong signal the M3 multiplatform surface is on a recent enough version.

### Activity-Sort Behaviour vs `last_changed`

`HaEntity` exposes both `lastChanged` (state value last differed) and `lastUpdated` (any HA touch — including same-state pings). FR18 spec phrasing ("recently active HA entities") matches **`lastUpdated`** semantically. A motion sensor that pings every 30s but stays `"on"` should bubble up; a light that flipped `on` 2 hours ago and never updated since should sink. Verify in test — `GetSortedEntitiesUseCaseTest` includes a case where `lastChanged < lastUpdated` and confirms `lastUpdated` drives ordering.

### Performance Budget

NFR4 is 500ms for 500 entities. Critical paths:
- Cache hydration in `EntityRepositoryImpl.init` already runs on `Dispatchers.Default` and emits a single `StateFlow.value = …` — picker reads from `StateFlow`, no extra IO.
- Sort cost: `List<HaEntity>(500).sortedByDescending { it.lastUpdated }` is ~tens of µs on a modern phone — well inside budget.
- `LazyColumn` with `key` keeps recomposition cost O(viewport).
- The 800ms `loadDeadlineHit` `LaunchedEffect` (Task 2.5) is a worst-case fallback only — under cache-hit it never fires.

### Previous Story Intelligence

**Story 4.4 (variants)** — established `compose.materialIconsExtended` as the icon source in commonMain; reuse `Icons.Outlined.*` names verified there (`Thermostat`, `Lightbulb`, `PlayCircle`, `HelpOutline`, etc.). Picker chip icons fall through `domainIcon(entity)` so no new icon names introduced here. Established `appendStaleSuffix` reactive ticker pattern via `produceState` — picker does NOT show stale state per AC4 (visual parity is intentional but the row keeps just `friendlyName + stateLabel`; if stale needs to surface in picker rows, that's a future spec change).

**Story 4.3 (toggleable + read-only)** — established `friendlyName`/`stateLabel`/`domainIcon` private helpers in `EntityCard.kt`. AC4 promotes them to `internal` — verify no other file in the package depends on `private` visibility (none should; previews extract their own preview-state helpers).

**Story 4.2 (dashboard persistence)** — `AddCardUseCase` exists and accepts a fully-formed `DashboardCard(id, dashboardId, entityId, position, config)`. Picker callers must construct the `DashboardCard`; picker does not. `id` generation strategy lives with the caller (Story 4.6 will pick UUID vs row-id — out of scope here).

**Story 4.1 (entity pipeline)** — `EntityRepository.entities: StateFlow<List<HaEntity>>` is the canonical source of all live entities. `selectAllEntityStates` orders `last_updated DESC` already. Picker layers a secondary sort to remain correct after streaming updates.

### Testing Standards

- `kotlin.test` — never JUnit4/5 in `commonTest`.
- `./gradlew :shared:testAndroidHostTest` — JVM `commonTest`.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — iOS link gate (catches `String.format`/`Locale` regressions).
- No Compose UI test — runner not wired. Helper coverage only (`humanizeDomain`, `GetSortedEntitiesUseCase`).

### Git Intelligence

| Commit  | What it established                                                                  |
| ------- | ------------------------------------------------------------------------------------ |
| `d4fc4d3` | Story 4.4 stepper/trigger/media/unknown variants + review patches                    |
| `1a3f281` | Story 4.3 toggleable + read-only EntityCard, helpers (`friendlyName`/`stateLabel`/`domainIcon`) |
| `e158f57` | Story 4.2 `Dashboard.sq`, `DashboardCard.sq`, repo, `AddCardUseCase`/`SaveDashboardUseCase`/etc. |
| `0adc61c` | Story 4.1 `EntityState.sq#last_changed` + `last_updated`, `Mutex`, Koin `bind` pattern |

Codebase state confirmed before story creation:
- `EntityRepository.entities: StateFlow<List<HaEntity>>` exposed at `domain/repository/EntityRepository.kt:8`.
- `EntityState.sq` selectAllEntityStates orders `ORDER BY last_updated DESC` at `EntityState.sq:15`.
- `HaEntity` sealed class includes `Light`, `Switch`, `Climate`, `Cover`, `MediaPlayer`, `Sensor`, `BinarySensor`, `InputBoolean`, `InputSelect`, `Script`, `Scene`, `Unknown` — all 11 supported domains plus `Unknown` fall-through (`HaEntity.kt:155–167`).
- `friendlyName`, `stateLabel`, `domainIcon` are private helpers in `EntityCard.kt` (verified at `EntityCard.kt:218`, `:220`, `:235`).
- `Motion.bottomSheetOpen` exists at `Motion.kt:14`; not consumed directly in this story (M3 default matches).
- `AddCardUseCase` exists at `domain/usecase/AddCardUseCase.kt`; picker does NOT reference it.
- `DomainModule.kt` already has 8 use-case factories — append the 9th.

### Project Structure — Files Touched

```
shared/src/commonMain/kotlin/com/backpapp/hanative/
  ├── domain/
  │   └── usecase/
  │       └── GetSortedEntitiesUseCase.kt              ← NEW
  ├── di/
  │   └── DomainModule.kt                              ← MODIFIED (add factory)
  └── ui/
      └── components/
          ├── EntityCard.kt                            ← MODIFIED (private → internal helpers)
          └── EntityPicker.kt                          ← NEW

shared/src/androidMain/kotlin/com/backpapp/hanative/
  └── ui/
      └── components/
          └── EntityPickerPreviews.kt                  ← NEW

shared/src/commonTest/kotlin/com/backpapp/hanative/
  ├── domain/
  │   └── usecase/
  │       └── GetSortedEntitiesUseCaseTest.kt          ← NEW
  └── ui/
      └── components/
          └── EntityPickerLogicTest.kt                 ← NEW (humanizeDomain coverage)
```

**Do NOT modify:**
- `EntityRepository.kt` / `EntityRepositoryImpl.kt` — no new methods needed.
- `EntityState.sq` — schema unchanged.
- `Dashboard.sq` / `DashboardCard.sq` — picker doesn't touch dashboards.
- `AddCardUseCase.kt` — caller's responsibility.
- `HaEntity.kt` — sealed class frozen for V1.
- `Motion.kt` / `Color.kt` / `HaNativeTheme.kt` — consumed as-is.

### References

- [Source: `_bmad/outputs/epics.md#Story 4.5`] — Acceptance criteria
- [Source: `_bmad/outputs/prd.md#FR17–FR18`] — Picker requirements + ≤500ms / 500-entity NFR4
- [Source: `_bmad/outputs/prd.md#NFR4`] — Performance budget for picker open
- [Source: `_bmad/outputs/ux-design-specification.md:485-490`] — EntityPicker anatomy + activity sort spec
- [Source: `_bmad/outputs/ux-design-specification.md:601-602`] — Loading skeleton + empty-domain copy
- [Source: `_bmad/outputs/architecture.md`] — Domain → UI layer separation, no UI deps in domain
- [Source: `_bmad/stories/4-3-core-entitycard-toggleable-readonly-variants.md`] — Helpers (`friendlyName`/`stateLabel`/`domainIcon`) being promoted
- [Source: `_bmad/stories/4-4-entitycard-stepper-trigger-media-unknown-variants.md`] — Preview Koin-isolation pattern (`EntityCardBody` extraction)
- [Source: `_bmad/stories/4-2-dashboard-persistence-layer.md`] — `AddCardUseCase` contract (caller's responsibility)
- [Source: `_bmad/stories/4-1-sqldelight-schema-entity-state-pipeline.md`] — `EntityRepository.entities` flow contract
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/repository/EntityRepository.kt:8`] — `entities: StateFlow<List<HaEntity>>`
- [Source: `shared/src/commonMain/sqldelight/com/backpapp/hanative/EntityState.sq:15`] — `ORDER BY last_updated DESC`
- [Source: `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/model/HaEntity.kt:155-167`] — Domain → sealed subtype factory

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 via `bmad-dev-story`.

### Debug Log References

- `./gradlew :shared:testAndroidHostTest` — initial run failed `emitsDistinctListsOnly` (StateFlow + `take(2).toList()` deadlocked because identical post-map content yields no second emission). Rewrote test to launch a collector + `runCurrent()` + cancel; passes.
- iOS link first attempted — succeeded directly; no `String.format` regression (helper is locale-safe `formatTemp` from `EntityCard.kt`, not used here).

### Completion Notes List

- All 14 ACs satisfied; all 7 task groups complete.
- Helpers in `EntityCard.kt` were already `internal` from Story 4.4 — Task 1.1 was a verification pass, no edit needed.
- `EntityPicker` is Koin-aware (calls `koinInject<GetSortedEntitiesUseCase>()`); `EntityPickerBody` is Koin-free for previews and takes `entities: List<HaEntity>` directly. Public composable wraps the body inside `ModalBottomSheet`.
- `humanizeDomain` and `PICKER_DOMAINS` declared `internal` (story said `private`) so commonTest can verify them — keeps the helper one source of truth without duplicating in tests.
- `ModalBottomSheet` default motion (stiffness ~400 / damping ~0.8) used as-is; matches `Motion.bottomSheetOpen` per AC7. No override.
- `EntityPickerEmptyState` icons use Material Icons Outlined; `cover` → `UnfoldMore`, `input_select` → `List`, `script` → `PlayArrow`, `scene` → `AutoAwesome`. Domain-icon mapping mirrors `domainIcon(entity)` for the empty-domain branch.
- Activity-sort uses `lastUpdated` (per FR18) — confirmed in `usesLastUpdatedNotLastChanged` test.
- Skeleton shimmer uses single `rememberInfiniteTransition` at parent — six rows share one driver.
- No Compose UI test (runner not wired); helper coverage only per AC14.

### File List

- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/usecase/GetSortedEntitiesUseCase.kt` (new)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DomainModule.kt` (modified — added `GetSortedEntitiesUseCase` factory + import)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt` (modified — `viewModel { EntityPickerViewModel(get()) }`)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPicker.kt` (new — VM-driven, UI-model-only body)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPickerUiModels.kt` (new — `EntityRowUi`, `EntityPickerUiState`)
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPickerViewModel.kt` (new)
- `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityPickerPreviews.kt` (new — drives previews via `EntityPickerUiState`)
- `shared/src/commonTest/kotlin/com/backpapp/hanative/domain/usecase/GetSortedEntitiesUseCaseTest.kt` (new)
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/components/EntityPickerLogicTest.kt` (new)
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/components/EntityPickerViewModelTest.kt` (new)
- `_bmad/stories/4-5-entitypicker-bottom-sheet.md` (modified — review findings, architectural override, status: done)

### Change Log

- 2026-05-01 — Story 4.5 created via `bmad-create-story`. Status: ready-for-dev.
- 2026-05-01 — Story 4.5 implemented via `bmad-dev-story`. EntityPicker bottom sheet, GetSortedEntitiesUseCase, helpers tests, previews, iOS link verified. Status: review.
- 2026-05-01 — Story 4.5 code review (`bmad-code-review`) + strict-architecture refactor + 5 patches applied. Composable → ViewModel → UseCase boundary; UI consumes `EntityPickerUiState` + `EntityRowUi` only (no `HaEntity` in Compose tree). New: `EntityPickerViewModel`, `EntityPickerUiModels`, `EntityPickerViewModelTest`. Decisions: drop `heightIn(max = 480.dp)`, swap `cover` icon to `Blinds`, keep `humanizeDomain`/`PICKER_DOMAINS` `internal`. Both gates green: `:shared:testAndroidHostTest` + `:shared:linkDebugFrameworkIosSimulatorArm64`. Status: done.

## Review Findings

_From `bmad-code-review` 2026-05-01 — Blind Hunter, Edge Case Hunter, Acceptance Auditor._

### Architectural Override (HIGH — applied 2026-05-01)

- [x] **Strict Composable → ViewModel → UseCase boundary, no domain types in UI tree.** User policy override of spec's "no ViewModel for picker" stance. EntityPicker now resolves `EntityPickerViewModel` via `koinViewModel()`, ViewModel maps `HaEntity → EntityRowUi`, body consumes `EntityPickerUiState` only. Public `onEntitySelected: (entityId: String) -> Unit` (was `(HaEntity) -> Unit`).
  - New: `EntityPickerUiModels.kt` (`EntityRowUi`, `EntityPickerUiState`).
  - New: `EntityPickerViewModel.kt` (`state: StateFlow<EntityPickerUiState>`, `onDomainSelected`, `combine(entities, selectedDomain, loadDeadlineHit)` → state derivation, 800ms deadline owned by VM).
  - New: `EntityPickerViewModelTest.kt` (7 tests: initial Loading, EmptyAll after deadline, Loaded mapping, filter, EmptyDomain, clear filter, no-domain-types invariant).
  - Modified: `EntityPicker.kt` — composable signature changed; `EntityPickerBody(state, selectedDomain, onDomainSelect, onRowTap)`; previews drive state directly.
  - Modified: `EntityPickerPreviews.kt` — UI-state-only, no `HaEntity` import; all 7 previews render their target state statically.
  - Modified: `PresentationModule.kt` — `viewModel { EntityPickerViewModel(get()) }`.
  - **Why memorialised:** memory file `feedback_compose_architecture.md` records this as the project default going forward; future composables must follow.

### Decision-Needed (resolved 2026-05-01)

- [x] [Review][Decision] AC11 `Picker_EmptyDomain` + `Picker_Loaded_FilteredLight` previews — **resolved by architectural override (b)**: `EntityPickerBody(state, selectedDomain, ...)` accepts UI state directly; previews now render the empty-domain and filtered-light states statically.
- [x] [Review][Decision] AC11 `Picker_EmptyAll` preview — **resolved by architectural override**: deadline machinery moved to ViewModel; preview passes `EntityPickerUiState.EmptyAll` directly, no `LaunchedEffect` to wait on.
- [x] [Review][Decision] `humanizeDomain` + `PICKER_DOMAINS` visibility — **kept `internal`** (test access; deviation from AC2 documented).
- [x] [Review][Decision] `LazyColumn.heightIn(max = 480.dp)` — **removed**; sheet sizes content naturally. [EntityPicker.kt:LoadedContent]
- [x] [Review][Decision] `cover` icon — **swapped** `UnfoldMore` → `Icons.Outlined.Blinds`. [EntityPicker.kt:iconForDomain]

### Patches (resolved 2026-05-01)

- [x] [Review][Patch] `derivedStateOf` keyed-`remember` misuse — **resolved by architectural override**: state derivation moved to ViewModel via `combine().stateIn()`. No `derivedStateOf` in composable.
- [x] [Review][Patch] `loadDeadlineHit` config-change loss — **resolved by architectural override**: deadline now owned by ViewModel scope; survives configuration changes via Koin VM scope binding.
- [x] [Review][Patch] Filter chip scroll state config-change loss — **patched** with `rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }`. [EntityPicker.kt:DomainFilterRow]
- [x] [Review][Patch] Sheet slide-out skipped on row tap — **patched** with `scope.launch { sheetState.hide() }.invokeOnCompletion { onEntitySelected(...); onDismiss() }`. [EntityPicker.kt:EntityPicker]
- [x] [Review][Patch] Dead `Spacer(Modifier.width(0.dp))` — **removed**. [EntityPicker.kt:EntityPickerSkeletonRow]

### Deferred (8)

- [x] [Review][Defer] `domainOf` uses `entityId.substringBefore('.')` — malformed entity id without dot returns full string as "domain", invisible to all chips but listed under "All". HA contract guarantees `domain.id` form; defensive log/normalization left for future hardening. [EntityPicker.kt:375]
- [x] [Review][Defer] `GetSortedEntitiesUseCase.distinctUntilChanged` does structural equality on freshly-allocated lists per emission — O(N) extra cost + GC pressure under high-frequency repository ticks. NFR4 budget still met for 500 entities; revisit if profiling shows recomposition storms. [GetSortedEntitiesUseCase.kt:13-18]
- [x] [Review][Defer] Sort tie-break unstable for entities sharing `lastUpdated` — `sortedByDescending { it.lastUpdated }` falls back to emission order, so identical timestamps shuffle on every emission and trigger LazyColumn reflow. Add secondary `thenBy { it.entityId }` when prioritized. [GetSortedEntitiesUseCase.kt:15]
- [x] [Review][Defer] `LazyColumn(key = { it.entityId })` will crash with `IllegalArgumentException` if repository ever emits duplicate entityIds — no defensive `distinctBy` in the use case. Repository contract should prevent; revisit defense-in-depth later. [EntityPicker.kt:235]
- [x] [Review][Defer] No state-machine UI tests (Loading → Loaded → EmptyDomain → EmptyAll transitions) — Compose UI test runner not wired (deferred from Story 4.3). Helper coverage only. [EntityPickerLogicTest.kt]
- [x] [Review][Defer] No accessibility live-region announcement when filter changes — TalkBack user gets no "Showing N climate entities" feedback after chip tap. Wider a11y polish pass. [EntityPicker.kt:190-226]
- [x] [Review][Defer] Row `contentDescription = "$name, $label, add to dashboard"` hardcodes "add to dashboard" even though picker is reusable for non-dashboard flows (FR38 context engine). Parameterize action label when Story 5.x lands. [EntityPicker.kt:252]
- [x] [Review][Defer] Skeleton's "Loading entities" semantic re-announces each shimmer frame on TalkBack — wrap a stable `liveRegion = LiveRegionMode.Polite` parent, or move the description off the animated subtree. [EntityPicker.kt:307-313]

### Dismissed

- `koinInject<GetSortedEntitiesUseCase>()` on every recomposition — Koin's `koinInject` Composable internally remembers its result; the additional `remember { getEntities() }` for the flow is correct.
- `forcedLoading` preview-only flag in `EntityPickerBody` — internal API surface; acceptable.
- `humanizeDomain("")` returns `""` — only reachable from chip tap and chip strings are non-empty; defensive concern only.
- Skeleton repeated "Loading entities" semantic — first row only announces; remaining 5 use `clearAndSetSemantics{}` per AC10.
- Acceptance Auditor "row click in body does not invoke `onDismiss()`" — by design; public wrapper owns dismiss, body is reusable for previews/tests.
- iOS `ModalBottomSheet` swipe-dismiss parity — speculative; CMP 1.10 ships M3 ModalBottomSheet on iOS Native; QA verifies on device.
- `humanizeDomain` test fragility re: data-class equality — `HaEntity.Light` is a `data class`, equality holds.
