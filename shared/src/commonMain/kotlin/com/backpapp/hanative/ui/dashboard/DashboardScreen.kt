// DashboardScreen — root composable for the active dashboard tab.
//
// Architecture: strict Composable → ViewModel → UseCase. UI consumes only
// `DashboardUiState` + `DashboardIntent` (UI-layer models). Domain types
// (`Dashboard`, `DashboardCard`, `HaEntity`) live behind the ViewModel boundary.

package com.backpapp.hanative.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DashboardViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticEngine.current
    LaunchedEffect(viewModel) {
        viewModel.haptics.collect { haptic.fire(it) }
    }
    DashboardBody(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

@Composable
internal fun DashboardBody(
    state: DashboardUiState,
    onIntent: (DashboardIntent) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    cardSlot: @Composable (DashboardCardUi, Boolean, Boolean, Modifier) -> Unit = { card, isStale, _, m ->
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
    val editMode = when (state) {
        is DashboardUiState.Empty -> state.editMode
        is DashboardUiState.Success -> state.editMode
        DashboardUiState.Loading -> false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Long-press on dashboard background (anywhere not consumed by a card / button /
            // toggleable surface) opens the actions sheet — same destination as the title
            // long-press, so the gesture works on the empty state, around cards, and below
            // the add-card affordance. Toggle cards still consume their own press because
            // they intentionally fire on touch-down for snappy feedback; that's a feature,
            // not a regression — the gap-and-margin between cards is enough hit target.
            .pointerInput(state is DashboardUiState.Loading) {
                if (state is DashboardUiState.Loading) return@pointerInput
                detectTapGestures(
                    onLongPress = { onIntent(DashboardIntent.OpenDashboardActions) },
                )
            },
    ) {
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
                onTitleClick = { onIntent(DashboardIntent.OpenDashboardPicker) },
                onTitleLongClick = { onIntent(DashboardIntent.OpenDashboardActions) },
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

        // Done chip — visible only in edit mode, exits the mode when tapped.
        if (editMode && state is DashboardUiState.Success) {
            FilledTonalButton(
                onClick = { onIntent(DashboardIntent.ExitEditMode) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) { Text("Done") }
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

        // Tappable-title nav sheets — hosted in the Box so they overlay all dashboard content.
        val dashboardPickerVisible = when (state) {
            is DashboardUiState.Empty -> state.dashboardPickerVisible
            is DashboardUiState.Success -> state.dashboardPickerVisible
            DashboardUiState.Loading -> false
        }
        val dashboardActionsVisible = when (state) {
            is DashboardUiState.Empty -> state.dashboardActionsVisible
            is DashboardUiState.Success -> state.dashboardActionsVisible
            DashboardUiState.Loading -> false
        }
        val pickerQuery = when (state) {
            is DashboardUiState.Empty -> state.dashboardPickerQuery
            is DashboardUiState.Success -> state.dashboardPickerQuery
            DashboardUiState.Loading -> ""
        }
        if (dashboardPickerVisible && switcher != null) {
            DashboardPickerSheet(
                state = switcher,
                query = pickerQuery,
                onIntent = onIntent,
            )
        }
        if (dashboardActionsVisible) {
            DashboardActionsSheet(
                onEditCards = {
                    onIntent(DashboardIntent.DismissDashboardActions)
                    onIntent(DashboardIntent.EnterEditMode)
                },
                onManageDashboards = {
                    onIntent(DashboardIntent.DismissDashboardActions)
                    onIntent(DashboardIntent.OpenSwitcher)
                },
                onSettings = {
                    onIntent(DashboardIntent.DismissDashboardActions)
                    onNavigateToSettings()
                },
                onDismiss = { onIntent(DashboardIntent.DismissDashboardActions) },
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptyDashboardState(
    onTapToAdd: () -> Unit,
    onTitleClick: () -> Unit,
    onTitleLongClick: () -> Unit,
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            role = Role.Button,
                            onClick = onTitleClick,
                            onLongClick = onTitleLongClick,
                        )
                        .semantics { heading() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dashboardName,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = "Switch dashboard",
                    )
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuccessContent(
    state: DashboardUiState.Success,
    onIntent: (DashboardIntent) -> Unit,
    cardSlot: @Composable (DashboardCardUi, Boolean, Boolean, Modifier) -> Unit,
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
        DashboardHeader(
            name = state.dashboardName,
            indicator = state.indicator,
            editMode = state.editMode,
            onTitleClick = { onIntent(DashboardIntent.OpenDashboardPicker) },
            onTitleLongClick = { onIntent(DashboardIntent.OpenDashboardActions) },
        )
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.cards, key = { it.cardId }) { cardUi ->
                ReorderableItem(reorderState, key = cardUi.cardId) {
                    EditableCardRow(
                        cardUi = cardUi,
                        editMode = state.editMode,
                        isStale = state.isStale,
                        cardSlot = cardSlot,
                        onRemove = { onIntent(DashboardIntent.RemoveCard(cardUi.cardId)) },
                    )
                }
            }
            item(key = "__add_card__") {
                AddCardAffordanceRow(onClick = { onIntent(DashboardIntent.OpenPicker) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun sh.calvin.reorderable.ReorderableCollectionItemScope.EditableCardRow(
    cardUi: DashboardCardUi,
    editMode: Boolean,
    isStale: Boolean,
    cardSlot: @Composable (DashboardCardUi, Boolean, Boolean, Modifier) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editMode) {
            // Visible drag handle wired to the reorderable lib's existing long-press-drag detector.
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = "Drag to reorder ${cardUi.entityId}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(24.dp)
                    .longPressDraggableHandle(),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            // Inside edit mode, the row stops handling its own taps so the drag handle and
            // delete badge are the only interactive surfaces — entity toggles are paused.
            // Edit mode is entered via the title long-press → "Edit cards" action, not via
            // a card long-press, since each EntityCard already owns its tap-and-long-press
            // surface for entity-specific actions and stacking another long-press detector
            // here fights for events with the inner clickable.
            cardSlot(cardUi, isStale, editMode, Modifier.fillMaxWidth())
            if (editMode) {
                // Consume taps on the card body so EntityCard's own clickable doesn't fire
                // service calls while the user is editing. Sits above the card, below the
                // delete badge in the Box's paint order.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(cardUi.cardId) {
                            detectTapGestures(onTap = { /* swallow */ })
                        }
                        .semantics { contentDescription = "${cardUi.entityId} (editing)" },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .zIndex(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .clickable(role = Role.Button, onClick = onRemove)
                        .semantics { contentDescription = "Remove ${cardUi.entityId}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardHeader(
    name: String,
    indicator: StaleIndicatorUi,
    editMode: Boolean,
    onTitleClick: () -> Unit,
    onTitleLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    role = Role.Button,
                    onClick = onTitleClick,
                    onLongClick = onTitleLongClick,
                )
                .padding(vertical = 4.dp)
                .semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = "Switch dashboard",
            )
            if (editMode) {
                Spacer(Modifier.size(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "Editing",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
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
