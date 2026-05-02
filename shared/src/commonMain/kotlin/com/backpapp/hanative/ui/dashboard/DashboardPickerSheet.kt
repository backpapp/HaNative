package com.backpapp.hanative.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val SEARCH_THRESHOLD = 5

/**
 * Tap-the-title bottom sheet for jumping to any dashboard. Search is enabled once the
 * library grows past SEARCH_THRESHOLD so small libraries get a clean list with no extra
 * input affordance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardPickerSheet(
    state: DashboardSwitcherUi,
    query: String,
    onIntent: (DashboardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = { onIntent(DashboardIntent.DismissDashboardPicker) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = false) { paneTitle = "Switch dashboard" },
        ) {
            Text(
                text = "Dashboards",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )

            if (state.dashboards.size > SEARCH_THRESHOLD) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { onIntent(DashboardIntent.UpdateDashboardPickerQuery(it)) },
                    placeholder = { Text("Search dashboards") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            val filtered = if (query.isBlank()) {
                state.dashboards
            } else {
                state.dashboards.filter { it.name.contains(query, ignoreCase = true) }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 0.dp, max = 480.dp)) {
                items(filtered, key = { it.id }) { row ->
                    PickerRow(
                        name = row.name,
                        isActive = row.id == state.activeDashboardId,
                        onClick = { onIntent(DashboardIntent.SelectDashboard(row.id)) },
                    )
                    HorizontalDivider()
                }
            }

            if (filtered.isEmpty() && query.isNotBlank()) {
                Text(
                    text = "No dashboards match \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            CreateNewFooterRow(
                onClick = {
                    onIntent(DashboardIntent.DismissDashboardPicker)
                    onIntent(DashboardIntent.OpenSwitcher)
                    onIntent(DashboardIntent.BeginCreateDashboard)
                },
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun PickerRow(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isActive) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CreateNewFooterRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Text(
            text = "New dashboard",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
