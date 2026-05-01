package com.backpapp.hanative.ui

/*
 * COMPOSE UI BOUNDARY — NON-NEGOTIABLE
 *
 * Applies to every file under `ui/` containing a `@Composable` function or a
 * `@Preview` function (commonMain + androidMain).
 *
 * FORBIDDEN in `@Composable` / `@Preview` files:
 *   - import org.koin.compose.koinInject     // composables resolve ViewModels, not use cases
 *   - import com.backpapp.hanative.domain.*  // no domain types in the Compose tree
 *
 * PRESCRIBED:
 *   - Composables consume a ViewModel via `org.koin.compose.viewmodel.koinViewModel()`,
 *     or accept a pre-mapped UIModel (`*UiState`) + intent lambdas.
 *   - ViewModel exposes `state: StateFlow<{Feature}UiState>` collected via
 *     `androidx.lifecycle.compose.collectAsStateWithLifecycle()`.
 *   - ViewModel maps domain → UIModel inside a `derive(...)` (or equivalent) function.
 *     Domain branching (`is HaEntity.Light`, etc.) lives in the VM, never in Composables.
 *   - Per-instance VMs use Koin parameterized factory:
 *       viewModel { (id: String) -> SomeViewModel(id, get(), get()) }
 *     and call sites use:
 *       koinViewModel(key = id) { parametersOf(id) }
 *
 * FILE-NAMING CONVENTION:
 *   {Feature}.kt              — Composable
 *   {Feature}UiModels.kt      — sealed `*UiState` + `*Intent` (may import domain — for mapping ref)
 *   {Feature}ViewModel.kt     — ViewModel (may import domain freely)
 *   {Feature}Mapper.kt        — optional standalone mapper (may import domain freely)
 *   {Feature}Previews.kt      — androidMain previews driven by UIModel (NO domain imports)
 *   {Feature}ViewModelTest.kt — commonTest VM unit tests
 *
 * REFERENCE IMPLEMENTATIONS:
 *   - EntityPicker (Story 4.5)
 *   - EntityCard   (refactored 2026-05-01)
 *
 * LINT CHECK (must return zero hits):
 *   grep -rn "koinInject<\|import com.backpapp.hanative.domain" \
 *     shared/src/commonMain/kotlin/com/backpapp/hanative/ui/ \
 *     shared/src/androidMain/kotlin/com/backpapp/hanative/ui/ \
 *     | grep -v "ViewModel.kt\|UiModels.kt\|Mapper.kt"
 *
 * Full rule + rationale: _bmad/outputs/architecture.md § Compose UI Boundary.
 */
