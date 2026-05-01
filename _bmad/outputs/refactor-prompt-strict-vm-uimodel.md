# Refactor Prompt — Strict Composable → ViewModel → UseCase + UIModel Boundary

Hand this entire file to a fresh agent. It has zero context from the originating conversation. Everything needed to act is below.

---

## Project

`/Users/jeremyblanchard/AndroidStudioProjects/HaNative` — Kotlin Multiplatform (Android + iOS) Compose Multiplatform app for Home Assistant. Modules: `shared/` (commonMain + androidMain + iosMain + commonTest), `androidApp/`, `iosApp/`. DI via Koin. Clean Architecture: `data → domain → ui` (UI may depend on domain; never the reverse).

## Architectural Rules to Enforce (project-wide)

These rules are now **mandatory for every screen and component** in `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/`:

1. **Composable → ViewModel → UseCase only.** No `org.koin.compose.koinInject<SomeUseCase>()` inside `@Composable`. No use case calls from UI. The composable consumes a `ViewModel` (via `org.koin.compose.viewmodel.koinViewModel()`) or accepts a pre-mapped UIModel + lambdas.
2. **No domain classes in the Compose tree.** Composables never import from `com.backpapp.hanative.domain.model.*` (`HaEntity`, `Dashboard`, `DashboardCard`, etc.) and never take parameters typed as those classes. The ViewModel maps domain → a UI-layer `*Ui` data class living next to the screen/component in `ui/`. Pass primitives (`String`, `Int`, `ImageVector`, `kotlin.time.Instant`) and UIModels.
3. **State shape:** `ViewModel.state: StateFlow<SomeUiState>` (sealed class for screen states; data class for simple value-bag states). Compose collects via `androidx.lifecycle.compose.collectAsStateWithLifecycle()`.
4. **Reusable leaf composables** that need to be host-agnostic still take UIModel + callbacks — never domain types.
5. **Previews** (`shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/*Previews.kt`) drive bodies via UIModel/UiState directly. Preview files must not import `com.backpapp.hanative.domain.*`.

The reference implementation already in the repo is **EntityPicker (Story 4.5)** — study it before touching anything else. See:

- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPicker.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPickerUiModels.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPickerViewModel.kt`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityPickerPreviews.kt`
- `shared/src/commonTest/kotlin/com/backpapp/hanative/ui/components/EntityPickerViewModelTest.kt`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/PresentationModule.kt` (`viewModel { EntityPickerViewModel(get()) }`)

Mirror that structure. Same package, same file naming pattern (`*UiModels.kt`, `*ViewModel.kt`, `*ViewModelTest.kt`).

## Known Offenders (audit + refactor each)

The following files violate the rules today. Confirm by inspection before refactoring — codebase may have moved.

### Major — must refactor

1. **`shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityCard.kt`** (~885 lines)
   - Public `EntityCard(entityId: String, ...)` injects two use cases via `koinInject` at lines ~110–111: `ObserveEntityStateUseCase`, `CallServiceUseCase`.
   - Internal `EntityCardBody(entity: HaEntity?, ...)` and every variant (`ToggleableEntityCard`, `StepperEntityCard`, `TriggerEntityCard`, `MediaEntityCard`, `UnknownEntityCard`, `ReadOnlyEntityCard`) takes `HaEntity` (or a sealed subtype) as parameter.
   - Branches on `is HaEntity.Light`, `is HaEntity.Climate`, etc. inside composables.

   **Refactor target:**
   - New `EntityCardViewModel(entityId: String, observe: ObserveEntityStateUseCase, call: CallServiceUseCase) : ViewModel()` — use case factory injection, exposes `state: StateFlow<EntityCardUiState>` and intent fns (`onToggle()`, `onStepUp()`, `onStepDown()`, `onTrigger()`, `onPlayPause()`, etc.) that internally call `CallServiceUseCase`.
   - New `EntityCardUiModels.kt` defining a sealed `EntityCardUiState` covering each variant: `Toggle(name, stateLabel, icon, isOn, isStale, lastChangedSuffix, ...)`, `Stepper(...)`, `Trigger(...)`, `Media(...)`, `Unknown(...)`, `ReadOnly(...)`, `Loading`, `Error`. Map every domain branching decision (`is HaEntity.Light`, sensor unit lookup, climate temperature) into the VM mapping function.
   - Leaf composables become `internal fun EntityCardBody(state: EntityCardUiState, onIntent: (EntityCardIntent) -> Unit, modifier: Modifier = Modifier)` and dispatch on the sealed UI state, not the domain type.
   - Helpers `friendlyName(HaEntity?, fallbackId: String): String`, `stateLabel(state: String): String`, `domainIcon(HaEntity?): ImageVector` currently in `EntityCard.kt`: move the `HaEntity`-touching parts into the ViewModel mapping function. The composable-side `domainIcon` should accept a `domain: String` (matches `EntityPicker`'s `iconForDomain`).
   - `appendStaleSuffix`, `rememberStaleSuffix`, `formatTemp` are pure-string helpers — keep them but they should consume primitives, not `HaEntity`. `rememberStaleSuffix` already takes a `String + Boolean + Instant?`; verify and keep.
   - Register: `viewModel { (entityId: String) -> EntityCardViewModel(entityId, get(), get()) }` in `PresentationModule.kt` (parameterized factory — Koin supports `koinViewModel { parametersOf(entityId) }`).
   - Update **every** call site of `EntityCard(entityId = ...)` if any exist outside previews.
   - Rewrite `shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityCardPreviews.kt` to drive `EntityCardBody` with `EntityCardUiState` directly. Drop all `HaEntity.*` imports and constructors.

2. **`shared/src/androidMain/kotlin/com/backpapp/hanative/ui/components/EntityCardPreviews.kt`** — drop `import com.backpapp.hanative.domain.model.HaEntity` and rebuild every preview from `EntityCardUiState` (after step 1 lands). Currently constructs `HaEntity.Light`, `HaEntity.Sensor`, `HaEntity.Climate`, `HaEntity.Script`, `HaEntity.MediaPlayer`, `HaEntity.Unknown`, `HaEntity.BinarySensor` literals.

### Minor — verify, then patch

3. **`shared/src/commonMain/kotlin/com/backpapp/hanative/ui/components/EntityPickerUiModels.kt`** + `EntityPickerViewModel.kt` + `EntityPicker.kt` + previews — already compliant; do not touch unless audit reveals a regression.

4. **Screen-level Composables already use `koinViewModel()`** — `OnboardingScreen.kt`, `AuthScreen.kt`, `SettingsScreen.kt`, `HaNativeNavHost.kt` (`StartupViewModel`). Audit them anyway: each screen's `*ViewModel.uiState` is the boundary; verify nothing leaks domain types into Compose params. If any expose a domain class via state, map to UIModel.

5. **Search globally for residual `koinInject` in commonMain UI:**
   ```bash
   grep -rn "koinInject<" shared/src/commonMain/kotlin/com/backpapp/hanative/ui/
   ```
   Each remaining hit must either become a `koinViewModel()` call (if a use case) or be deleted in favor of VM-mediated state.

6. **Search globally for `HaEntity` / `Dashboard` / `DashboardCard` imports inside `ui/` and `androidMain/.../ui/`:**
   ```bash
   grep -rn "import com.backpapp.hanative.domain" \
     shared/src/commonMain/kotlin/com/backpapp/hanative/ui/ \
     shared/src/androidMain/kotlin/com/backpapp/hanative/ui/
   ```
   Composable files (anything with `@Composable`) and preview files must not appear in the results. `*ViewModel.kt`, `*UiModels.kt`, and `*Mapper.kt` may import domain freely.

## Out of Scope — Do Not Touch

- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/domain/`
- `shared/src/commonMain/sqldelight/`
- `shared/src/iosMain/`, `shared/src/androidMain/.../platform/`
- `_bmad/` (story specs, outputs) — read-only context.
- `androidApp/`, `iosApp/` unless a refactor breaks a call site there. If it does, fix only the breakage.

## Verification — both must pass before declaring done

```bash
./gradlew :shared:testAndroidHostTest
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Add new `*ViewModelTest.kt` for every new VM. Cover at minimum: initial state, success path mapping, intent → state transition, error/empty states. Use the `EntityPickerViewModelTest.kt` pattern (StandardTestDispatcher + setMain + `backgroundScope.launch { vm.state.collect {} }` to keep `stateIn` flow hot).

Lint check after each step:

```bash
grep -rn "koinInject<\|import com.backpapp.hanative.domain" \
  shared/src/commonMain/kotlin/com/backpapp/hanative/ui/ \
  shared/src/androidMain/kotlin/com/backpapp/hanative/ui/ \
  | grep -v "ViewModel.kt\|UiModels.kt\|Mapper.kt"
```
Must return zero hits in composable/preview files when refactor is complete.

## Working Style

- Make one offender's refactor per commit. Commit message format: `<area>: refactor <thing> to ViewModel + UIModel boundary`. No squashing across offenders.
- Do not skip pre-commit hooks. Do not amend. Each failed hook → fix → new commit.
- Update each touched story file in `_bmad/stories/` with a Change Log entry: `2026-MM-DD — <short summary> per strict-architecture refactor.`
- After all offenders are clean, append the architectural rule to `_bmad/outputs/architecture.md` under a "## Compose UI Boundary" section so it survives in source-of-truth docs.

## When You Are Done

Reply with:
- Bullet list of every file added, modified, deleted.
- Output of both verification commands' final lines.
- Output of the lint grep showing zero hits.
- One-paragraph reflection on any judgment calls (e.g., "merged X and Y UIModels because they shared 80% of fields").

If a refactor would meaningfully change runtime behavior (e.g., adding a network call, changing a dispatch path), STOP and report — this is a structural refactor, not a behavior change. Same goes for any rename that crosses module boundaries or changes a public API outside `ui/`.
