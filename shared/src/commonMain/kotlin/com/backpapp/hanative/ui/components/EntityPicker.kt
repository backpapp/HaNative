// EntityPicker — modal bottom sheet for browsing & selecting HA entities,
// activity-sorted (lastUpdated DESC) with domain filter chips. Reusable: the
// caller decides what to do with the selected entity (Story 4.6 dashboard add,
// or future context-engine flows). Picker holds no dashboard concept itself.
//
// Architecture: strict Composable → ViewModel → UseCase. UI consumes only
// `EntityPickerUiState` + `EntityRowUi` (UI-layer models). Domain types
// (`HaEntity`) live behind the ViewModel boundary.

package com.backpapp.hanative.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Blinds
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_COUNT = 6

internal val PICKER_DOMAINS: List<String> = listOf(
    "light",
    "switch",
    "input_boolean",
    "climate",
    "cover",
    "media_player",
    "script",
    "scene",
    "sensor",
    "binary_sensor",
    "input_select",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityPicker(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onEntitySelected: (entityId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EntityPickerViewModel = koinViewModel(),
) {
    if (!isVisible) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedDomain by viewModel.selectedDomain.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        EntityPickerBody(
            state = state,
            selectedDomain = selectedDomain,
            onDomainSelect = viewModel::onDomainSelected,
            onRowTap = { row ->
                scope.launch {
                    sheetState.hide()
                }.invokeOnCompletion {
                    onEntitySelected(row.entityId)
                    onDismiss()
                }
            },
        )
    }
}

// Koin-free body — previews + tests drive this directly with a UI state.
// Composables here consume only UI-layer types (`EntityPickerUiState`,
// `EntityRowUi`, `String?` selectedDomain) — no domain models.
@Composable
internal fun EntityPickerBody(
    state: EntityPickerUiState,
    selectedDomain: String?,
    onDomainSelect: (String?) -> Unit,
    onRowTap: (EntityRowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DomainFilterRow(selected = selectedDomain, onSelect = onDomainSelect)
        Spacer(Modifier.height(8.dp))
        when (state) {
            EntityPickerUiState.Loading -> LoadingContent()
            is EntityPickerUiState.Loaded -> LoadedContent(rows = state.rows, onClick = onRowTap)
            is EntityPickerUiState.EmptyDomain -> EntityPickerEmptyState(
                icon = iconForDomain(state.domain),
                message = "No ${humanizeDomain(state.domain)} entities found in your HA",
            )
            EntityPickerUiState.EmptyAll -> EntityPickerEmptyState(
                icon = Icons.Outlined.Sensors,
                message = "No entities yet — connect to Home Assistant to populate this list",
            )
        }
    }
}

@Composable
private fun DomainFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .semantics {
                    contentDescription = "Filter by All"
                    this.selected = (selected == null)
                },
        )
        for (domain in PICKER_DOMAINS) {
            val isSelected = selected == domain
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else domain) },
                label = { Text(humanizeDomain(domain)) },
                colors = FilterChipDefaults.filterChipColors(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics {
                        contentDescription = "Filter by ${humanizeDomain(domain)}"
                        this.selected = isSelected
                    },
            )
        }
    }
}

@Composable
private fun LoadedContent(rows: List<EntityRowUi>, onClick: (EntityRowUi) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(rows, key = { it.entityId }) { row ->
            EntityPickerRow(row = row, onClick = { onClick(row) })
        }
    }
}

@Composable
private fun EntityPickerRow(row: EntityRowUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "${row.name}, ${row.stateLabel}, add to dashboard"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = iconForDomain(row.domain),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.stateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    val transition = rememberInfiniteTransition(label = "picker-skeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shimmer-alpha",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(SKELETON_COUNT) { index ->
            EntityPickerSkeletonRow(
                shimmerAlpha = shimmer,
                announce = index == 0,
            )
        }
    }
}

@Composable
private fun EntityPickerSkeletonRow(shimmerAlpha: Float, announce: Boolean) {
    val placeholder = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (announce) {
                    Modifier.semantics { contentDescription = "Loading entities" }
                } else {
                    Modifier.clearAndSetSemantics {}
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(placeholder, CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .background(placeholder, RoundedCornerShape(4.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .background(placeholder, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun EntityPickerEmptyState(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun humanizeDomain(raw: String): String =
    raw.split('_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

private fun iconForDomain(domain: String): ImageVector = when (domain) {
    "light" -> Icons.Outlined.Lightbulb
    "switch" -> Icons.Outlined.ToggleOn
    "input_boolean" -> Icons.Outlined.RadioButtonChecked
    "climate" -> Icons.Outlined.Thermostat
    "cover" -> Icons.Outlined.Blinds
    "media_player" -> Icons.Outlined.PlayCircle
    "script" -> Icons.Outlined.PlayArrow
    "scene" -> Icons.Outlined.AutoAwesome
    "sensor" -> Icons.Outlined.Sensors
    "binary_sensor" -> Icons.Outlined.Notifications
    "input_select" -> Icons.Outlined.List
    else -> Icons.Outlined.HelpOutline
}
