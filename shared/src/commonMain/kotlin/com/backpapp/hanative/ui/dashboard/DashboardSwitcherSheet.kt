package com.backpapp.hanative.ui.dashboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardSwitcherSheet(
    state: DashboardSwitcherUi,
    onIntent: (DashboardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = { onIntent(DashboardIntent.DismissSwitcher) },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        SwitcherBody(state = state, onIntent = onIntent)
    }
}

@Composable
internal fun SwitcherBody(
    state: DashboardSwitcherUi,
    onIntent: (DashboardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) { paneTitle = "Dashboard switcher" },
    ) {
        Text(
            text = "Dashboards",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(state.dashboards, key = { it.id }) { row ->
                DashboardRow(
                    row = row,
                    isActive = row.id == state.activeDashboardId,
                    isRenaming = state.renamingId == row.id,
                    pendingRenameText = state.pendingRenameText,
                    canDelete = state.canDelete,
                    onIntent = onIntent,
                )
                HorizontalDivider()
            }
        }

        if (state.creating) {
            CreateNewInline(
                text = state.pendingNewName,
                onTextChange = { onIntent(DashboardIntent.UpdateNewDashboardName(it)) },
                onConfirm = { onIntent(DashboardIntent.ConfirmCreateDashboard(state.pendingNewName)) },
                onCancel = { onIntent(DashboardIntent.CancelCreateDashboard) },
            )
        } else {
            CreateNewFooter(onClick = { onIntent(DashboardIntent.BeginCreateDashboard) })
        }
    }
}

@Composable
private fun DashboardRow(
    row: DashboardSummaryUi,
    isActive: Boolean,
    isRenaming: Boolean,
    pendingRenameText: String,
    canDelete: Boolean,
    onIntent: (DashboardIntent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (!isRenaming) it.clickable(role = Role.Button) { onIntent(DashboardIntent.SelectDashboard(row.id)) } else it }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // P4: when editing, the row no longer behaves as a Switch button — drop the
            // "Switch to ${name}" announcement so TalkBack reads only the rename TextField.
            .let { base ->
                if (isRenaming) {
                    base.semantics(mergeDescendants = false) {
                        contentDescription = "Renaming dashboard ${row.name}"
                    }
                } else {
                    base.semantics {
                        contentDescription = buildString {
                            append("Switch to ${row.name} dashboard. ${row.cardCount} cards.")
                            if (isActive) append(" Currently active.")
                        }
                        role = Role.Button
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.GridView,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        if (isRenaming) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(row.id) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = pendingRenameText,
                onValueChange = { onIntent(DashboardIntent.UpdateRenameText(it)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onIntent(DashboardIntent.ConfirmRename(row.id, pendingRenameText)) },
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Rename dashboard ${row.name}" },
            )
            IconButton(onClick = { onIntent(DashboardIntent.ConfirmRename(row.id, pendingRenameText)) }) {
                Icon(Icons.Outlined.Check, contentDescription = "Confirm rename")
            }
            IconButton(onClick = { onIntent(DashboardIntent.CancelRenameDashboard) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Cancel rename")
            }
        } else {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (isActive) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            RowOverflowMenu(
                row = row,
                // P11 + D3=C: pending dashboard always allows Delete (VM short-circuits to discard
                // without dialog); persisted dashboards gated on canDelete.
                canDelete = canDelete || row.isPending,
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun RowOverflowMenu(
    row: DashboardSummaryUi,
    canDelete: Boolean,
    onIntent: (DashboardIntent) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { menuOpen = true },
            modifier = Modifier.semantics { contentDescription = "More options for ${row.name}" },
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onIntent(DashboardIntent.BeginRenameDashboard(row.id))
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                enabled = canDelete,
                onClick = {
                    menuOpen = false
                    onIntent(DashboardIntent.RequestDeleteDashboard(row.id))
                },
            )
        }
    }
}

@Composable
private fun CreateNewFooter(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                contentDescription = "Create new dashboard"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(24.dp)
                .minimumInteractiveComponentSize(),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "New Dashboard",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CreateNewInline(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            placeholder = { Text("Name your dashboard") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = "New dashboard name" },
        )
        IconButton(onClick = onConfirm) {
            Icon(Icons.Outlined.Check, contentDescription = "Confirm new dashboard")
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Outlined.Close, contentDescription = "Cancel new dashboard")
        }
    }
}
