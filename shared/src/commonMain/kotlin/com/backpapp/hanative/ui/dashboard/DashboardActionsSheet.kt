package com.backpapp.hanative.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Long-press-the-title sheet. Three rows: Edit cards (enter dashboard edit mode),
 * Manage dashboards (re-uses the existing rename/reorder/delete switcher sheet via
 * onIntent), and Settings (lambda callback so the host nav graph owns route changes).
 *
 * Disconnect is intentionally NOT here — keeping the destructive action behind a
 * single Settings-screen entry prevents accidental dismissal of the connection from
 * a half-pressed sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardActionsSheet(
    onEditCards: () -> Unit,
    onManageDashboards: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = false) { paneTitle = "Dashboard actions" },
        ) {
            ActionRow(
                icon = Icons.Outlined.Edit,
                label = "Edit cards",
                onClick = onEditCards,
            )
            HorizontalDivider()
            ActionRow(
                icon = Icons.Outlined.GridView,
                label = "Manage dashboards",
                onClick = onManageDashboards,
            )
            HorizontalDivider()
            ActionRow(
                icon = Icons.Outlined.Settings,
                label = "Settings",
                onClick = onSettings,
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
