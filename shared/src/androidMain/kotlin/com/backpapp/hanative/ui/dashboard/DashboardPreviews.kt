package com.backpapp.hanative.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.backpapp.hanative.ui.theme.HaNativeTheme

// Previews drive `DashboardBody` directly with `DashboardUiState`. UI-layer types
// only — no `Dashboard` / `DashboardCard` / `HaEntity` import. EntityCard +
// EntityPicker are replaced by Koin-free stubs via DashboardBody slots.

private fun cardUi(cardId: String, entityId: String) = DashboardCardUi(cardId = cardId, entityId = entityId)

private val fewCards = listOf(
    cardUi("c1", "light.living_room"),
    cardUi("c2", "switch.kitchen_outlet"),
    cardUi("c3", "climate.lounge"),
)

private val manyCards = (1..12).map { cardUi("c$it", "light.entity_$it") }

@Composable
private fun StubEntityCard(card: DashboardCardUi, isStale: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .alpha(if (isStale) 0.5f else 1f)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(card.entityId, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StubPicker(isVisible: Boolean, onDismiss: () -> Unit, onEntitySelected: (String) -> Unit) {
    if (!isVisible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onDismiss() }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "EntityPicker (preview stub) — tap surface to dismiss",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(12.dp))
            Button(onClick = { onEntitySelected("light.preview_pick") }) {
                Text("Select sample entity")
            }
        }
    }
}

// `ModalBottomSheet` does not render in `@Preview`; flatten to an inline column so the
// switcher anatomy is visible. Stub takes the same parameters as the real `switcherSlot`.
@Composable
private fun StubSwitcher(switcher: DashboardSwitcherUi, onIntent: (DashboardIntent) -> Unit) {
    if (!switcher.visible) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 12.dp),
    ) {
        Text(
            "Dashboards",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        switcher.dashboards.forEach { row ->
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (switcher.renamingId == row.id) {
                    OutlinedTextField(
                        value = switcher.pendingRenameText,
                        onValueChange = {},
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = if (row.id == switcher.activeDashboardId) "${row.name} ✓" else row.name,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            HorizontalDivider()
        }
        if (switcher.creating) {
            OutlinedTextField(
                value = switcher.pendingNewName,
                onValueChange = {},
                singleLine = true,
                placeholder = { Text("Name your dashboard") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Text(
                "+ New Dashboard",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIntent(DashboardIntent.BeginCreateDashboard) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (switcher.pendingDeleteId != null) {
            val name = switcher.dashboards.firstOrNull { it.id == switcher.pendingDeleteId }?.name.orEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(16.dp),
            ) {
                Column {
                    Text("Delete $name?", style = MaterialTheme.typography.titleMedium)
                    Text("This cannot be undone.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = {}) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun PreviewWrap(state: DashboardUiState) {
    HaNativeTheme {
        DashboardBody(
            state = state,
            onIntent = {},
            onNavigateToSettings = {},
            cardSlot = { card, isStale, _, m -> StubEntityCard(card, isStale, m) },
            pickerSlot = { v, d, s -> StubPicker(v, d, s) },
            switcherSlot = { switcher, onIntent -> StubSwitcher(switcher, onIntent) },
        )
    }
}

@Preview(name = "Dashboard_Loading")
@Composable
private fun DashboardLoading() = PreviewWrap(DashboardUiState.Loading)

@Preview(name = "Dashboard_Empty")
@Composable
private fun DashboardEmpty() = PreviewWrap(DashboardUiState.Empty(pickerVisible = false))

@Preview(name = "Dashboard_Empty_PickerOpen")
@Composable
private fun DashboardEmptyPickerOpen() = PreviewWrap(DashboardUiState.Empty(pickerVisible = true))

@Preview(name = "Dashboard_Success_FewCards")
@Composable
private fun DashboardSuccessFew() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        cards = fewCards,
        isStale = false,
        pickerVisible = false,
    ),
)

@Preview(name = "Dashboard_Success_ManyCards", heightDp = 1200)
@Composable
private fun DashboardSuccessMany() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        cards = manyCards,
        isStale = false,
        pickerVisible = false,
    ),
)

@Preview(name = "Dashboard_Success_Stale")
@Composable
private fun DashboardSuccessStale() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        cards = fewCards,
        isStale = true,
        pickerVisible = false,
    ),
)

@Preview(name = "Dashboard_Success_PickerOpen")
@Composable
private fun DashboardSuccessPickerOpen() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        cards = fewCards,
        isStale = false,
        pickerVisible = true,
    ),
)

// ── Story 4.7 switcher / dialog previews ────────────────────────────────────

private val threeSummaries = listOf(
    DashboardSummaryUi(id = "d1", name = "Home", cardCount = 3),
    DashboardSummaryUi(id = "d2", name = "Living Room", cardCount = 5),
    DashboardSummaryUi(id = "d3", name = "Kitchen Wall", cardCount = 2),
)

@Preview(name = "Dashboard_Switcher_Closed")
@Composable
private fun DashboardSwitcherClosed() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        activeDashboardId = "d1",
        cards = fewCards,
        isStale = false,
        switcher = DashboardSwitcherUi(
            visible = false,
            dashboards = threeSummaries,
            activeDashboardId = "d1",
            canDelete = true,
        ),
    ),
)

@Preview(name = "Dashboard_Switcher_OpenThree", heightDp = 720)
@Composable
private fun DashboardSwitcherOpenThree() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Living Room",
        activeDashboardId = "d2",
        cards = fewCards,
        isStale = false,
        switcher = DashboardSwitcherUi(
            visible = true,
            dashboards = threeSummaries,
            activeDashboardId = "d2",
            canDelete = true,
        ),
    ),
)

@Preview(name = "Dashboard_Switcher_OpenSingle_DeleteDisabled", heightDp = 600)
@Composable
private fun DashboardSwitcherOpenSingle() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        activeDashboardId = "d1",
        cards = fewCards,
        isStale = false,
        switcher = DashboardSwitcherUi(
            visible = true,
            dashboards = listOf(DashboardSummaryUi(id = "d1", name = "Home", cardCount = 3)),
            activeDashboardId = "d1",
            canDelete = false,
        ),
    ),
)

@Preview(name = "Dashboard_Switcher_Creating", heightDp = 720)
@Composable
private fun DashboardSwitcherCreating() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        activeDashboardId = "d1",
        cards = fewCards,
        isStale = false,
        switcher = DashboardSwitcherUi(
            visible = true,
            dashboards = threeSummaries,
            activeDashboardId = "d1",
            creating = true,
            pendingNewName = "Office",
            canDelete = true,
        ),
    ),
)

@Preview(name = "Dashboard_Switcher_Renaming", heightDp = 720)
@Composable
private fun DashboardSwitcherRenaming() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        activeDashboardId = "d1",
        cards = fewCards,
        isStale = false,
        switcher = DashboardSwitcherUi(
            visible = true,
            dashboards = threeSummaries,
            activeDashboardId = "d1",
            renamingId = "d2",
            pendingRenameText = "Living Room v2",
            canDelete = true,
        ),
    ),
)

@Preview(name = "Dashboard_DeleteDialog_Open", heightDp = 720)
@Composable
private fun DashboardDeleteDialogOpen() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        activeDashboardId = "d1",
        cards = fewCards,
        isStale = false,
        switcher = DashboardSwitcherUi(
            visible = true,
            dashboards = threeSummaries,
            activeDashboardId = "d1",
            pendingDeleteId = "d3",
            canDelete = true,
        ),
    ),
)

// ── Story 4.8 indicator previews ─────────────────────────────────────────────

@Preview(name = "Dashboard_Success_Indicator_Connected")
@Composable
private fun DashboardIndicatorConnected() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        cards = fewCards,
        isStale = false,
        indicator = StaleIndicatorUi(StaleIndicatorKind.Connected),
    ),
)

@Preview(name = "Dashboard_Success_Indicator_Stale_RecentMessage")
@Composable
private fun DashboardIndicatorStaleRecent() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        cards = fewCards,
        isStale = true,
        indicator = StaleIndicatorUi(
            kind = StaleIndicatorKind.Stale,
            // ~30s ago relative to a fixed preview baseline; runtime clock makes
            // exact value drift in IDE preview, but the "Xs ago" template renders.
            lastMessageEpochMs = System.currentTimeMillis() - 30_000L,
        ),
    ),
)

@Preview(name = "Dashboard_Success_Indicator_Stale_NoMessage")
@Composable
private fun DashboardIndicatorStaleNoMessage() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        cards = fewCards,
        isStale = true,
        indicator = StaleIndicatorUi(
            kind = StaleIndicatorKind.Stale,
            lastMessageEpochMs = null,
        ),
    ),
)

@Preview(name = "Dashboard_Success_Indicator_Reconnecting")
@Composable
private fun DashboardIndicatorReconnecting() = PreviewWrap(
    DashboardUiState.Success(
        dashboardName = "Home",
        cards = fewCards,
        isStale = true,
        indicator = StaleIndicatorUi(
            kind = StaleIndicatorKind.Reconnecting,
            lastMessageEpochMs = System.currentTimeMillis() - 5_000L,
        ),
    ),
)

@Preview(name = "Dashboard_Empty_Indicator_Stale")
@Composable
private fun DashboardEmptyIndicatorStale() = PreviewWrap(
    DashboardUiState.Empty(
        switcher = DashboardSwitcherUi(
            dashboards = listOf(DashboardSummaryUi(id = "d1", name = "Home", cardCount = 0)),
            activeDashboardId = "d1",
        ),
        indicator = StaleIndicatorUi(
            kind = StaleIndicatorKind.Stale,
            lastMessageEpochMs = null,
        ),
    ),
)
