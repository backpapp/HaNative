// DashboardScreen — root composable for the active dashboard tab.
//
// Architecture: strict Composable → ViewModel → UseCase. UI consumes only
// `DashboardUiState` + `DashboardIntent` (UI-layer models). Domain types
// (`Dashboard`, `DashboardCard`, `HaEntity`) live behind the ViewModel boundary.

package com.backpapp.hanative.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backpapp.hanative.platform.HapticPattern
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.components.EntityCard
import com.backpapp.hanative.ui.components.EntityPicker
import com.backpapp.hanative.ui.theme.Motion
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val viewModel: DashboardViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticEngine.current
    LaunchedEffect(viewModel) {
        viewModel.haptics.collect { haptic.fire(it) }
    }
    DashboardBody(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
internal fun DashboardBody(
    state: DashboardUiState,
    onIntent: (DashboardIntent) -> Unit,
    modifier: Modifier = Modifier,
    cardSlot: @Composable (DashboardCardUi, Boolean, Modifier) -> Unit = { card, isStale, m ->
        EntityCard(entityId = card.entityId, isStale = isStale, modifier = m)
    },
    pickerSlot: @Composable (Boolean, () -> Unit, (String) -> Unit) -> Unit = { isVisible, onDismiss, onEntitySelected ->
        EntityPicker(
            isVisible = isVisible,
            onDismiss = onDismiss,
            onEntitySelected = onEntitySelected,
        )
    },
    switcherSlot: @Composable (DashboardSwitcherUi, (DashboardIntent) -> Unit) -> Unit = { switcher, onIntent2 ->
        DashboardSwitcherSheet(state = switcher, onIntent = onIntent2)
    },
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            DashboardUiState.Loading -> LoadingContent()
            is DashboardUiState.Empty -> EmptyDashboardState(
                // P16: surface the active dashboard name above the empty state when one is selected
                // (e.g. user just deleted all cards) so the header doesn't drop.
                dashboardName = state.switcher.dashboards
                    .firstOrNull { it.id == state.switcher.activeDashboardId }
                    ?.name,
                indicator = state.indicator,
                onTapToAdd = { onIntent(DashboardIntent.OpenPicker) },
            )
            is DashboardUiState.Success -> Crossfade(
                // P9: key on activeDashboardId so the cross-fade fires on dashboard switch
                // only, not on every card / picker toggle. Lambda receives the keyed id;
                // we render the current Success snapshot — same outgoing-frame freeze the
                // Crossfade gives us automatically since the previous lane keeps its
                // composition until the fade completes.
                targetState = state.activeDashboardId,
                animationSpec = Motion.dashboardTransition,
                label = "dashboardSwitch",
            ) { keyedId ->
                if (keyedId == state.activeDashboardId) {
                    SuccessContent(
                        state = state,
                        onIntent = onIntent,
                        cardSlot = cardSlot,
                    )
                }
            }
        }

        val pickerVisible = when (state) {
            is DashboardUiState.Empty -> state.pickerVisible
            is DashboardUiState.Success -> state.pickerVisible
            DashboardUiState.Loading -> false
        }
        pickerSlot(
            pickerVisible,
            { onIntent(DashboardIntent.DismissPicker) },
            { entityId -> onIntent(DashboardIntent.AddCard(entityId)) },
        )

        val switcher = when (state) {
            is DashboardUiState.Empty -> state.switcher
            is DashboardUiState.Success -> state.switcher
            DashboardUiState.Loading -> null
        }
        if (switcher != null) {
            switcherSlot(switcher, onIntent)
            DeleteConfirmDialog(switcher = switcher, onIntent = onIntent)
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    switcher: DashboardSwitcherUi,
    onIntent: (DashboardIntent) -> Unit,
) {
    val pendingId = switcher.pendingDeleteId ?: return
    val target = switcher.dashboards.firstOrNull { it.id == pendingId }
    // P5: target dashboard vanished (e.g. cascade delete or out-of-band removal) — auto-dismiss
    // instead of showing "Delete ?".
    if (target == null) {
        LaunchedEffect(pendingId) { onIntent(DashboardIntent.CancelDeleteDashboard) }
        return
    }
    val name = target.name
    AlertDialog(
        onDismissRequest = { onIntent(DashboardIntent.CancelDeleteDashboard) },
        title = { Text("Delete $name?") },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = { onIntent(DashboardIntent.ConfirmDeleteDashboard(pendingId)) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(DashboardIntent.CancelDeleteDashboard) }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyDashboardState(
    onTapToAdd: () -> Unit,
    modifier: Modifier = Modifier,
    dashboardName: String? = null,
    indicator: StaleIndicatorUi = StaleIndicatorUi(StaleIndicatorKind.Connected),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onTapToAdd)
            .semantics(mergeDescendants = true) {
                contentDescription = "No cards yet. Tap to add your first card."
                role = Role.Button
            }
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (dashboardName != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dashboardName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                StaleStateIndicator(state = indicator)
            }
            Spacer(Modifier.size(16.dp))
        } else if (indicator.kind != StaleIndicatorKind.Connected) {
            // No dashboard name but still need to surface staleness on cold launch.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                StaleStateIndicator(state = indicator)
            }
            Spacer(Modifier.size(16.dp))
        }
        Text(
            "Add your first card",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "Your most-used entities appear first",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(56.dp)
                .minimumInteractiveComponentSize(),
        )
    }
}

@Composable
private fun SuccessContent(
    state: DashboardUiState.Success,
    onIntent: (DashboardIntent) -> Unit,
    cardSlot: @Composable (DashboardCardUi, Boolean, Modifier) -> Unit,
) {
    val haptic = LocalHapticEngine.current
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val ids = state.cards.map { it.cardId }.toMutableList()
        if (from.index in ids.indices && to.index in ids.indices) {
            ids.add(to.index, ids.removeAt(from.index))
            onIntent(DashboardIntent.Reorder(ids))
        } else {
            haptic.fire(HapticPattern.ActionRejected)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DashboardHeader(name = state.dashboardName, indicator = state.indicator)
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.cards, key = { it.cardId }) { cardUi ->
                ReorderableItem(reorderState, key = cardUi.cardId) {
                    val rowModifier = Modifier
                        .fillMaxWidth()
                        .longPressDraggableHandle()
                        .semantics {
                            contentDescription = "Reorder ${cardUi.entityId}"
                        }
                    cardSlot(cardUi, state.isStale, rowModifier)
                }
            }
            item(key = "__add_card__") {
                AddCardAffordanceRow(onClick = { onIntent(DashboardIntent.OpenPicker) })
            }
        }
    }
}

@Composable
private fun DashboardHeader(name: String, indicator: StaleIndicatorUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        StaleStateIndicator(state = indicator)
    }
}

@Composable
private fun AddCardAffordanceRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .minimumInteractiveComponentSize()
            .semantics {
                contentDescription = "Add card"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
