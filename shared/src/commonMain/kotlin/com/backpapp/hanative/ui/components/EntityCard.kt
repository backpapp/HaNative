// EntityCard — toggleable, read-only, stepper, trigger, media & unknown variants.
//
// CALLER PATTERN (Story 4.6 dashboard list):
//
//   LazyColumn {
//       items(cards, key = { it.entityId }) { card ->
//           key(card.entityId) { EntityCard(entityId = card.entityId) }
//       }
//   }
//
// Each EntityCard binds to its own EntityCardViewModel (parameterised on entityId
// via Koin), so a single entity update only recomposes its own card.
//
// Architecture: strict Composable → ViewModel → UseCase. UI consumes only
// `EntityCardUiState` + `EntityCardIntent` (UI-layer models). Domain types
// (`HaEntity`) live behind the ViewModel boundary.

package com.backpapp.hanative.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val NUDGE_DP = -8
private const val TRIGGER_PULSE_PEAK = 1.04f

@Composable
fun EntityCard(
    entityId: String,
    modifier: Modifier = Modifier,
    size: EntityCardSize = EntityCardSize.Standard,
    isStale: Boolean = false,
    viewModel: EntityCardViewModel = koinViewModel(key = entityId) { parametersOf(entityId) },
) {
    LaunchedEffect(viewModel, isStale) { viewModel.setStale(isStale) }
    val haptic = LocalHapticEngine.current
    LaunchedEffect(viewModel) {
        viewModel.haptics.collect { haptic.fire(it) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    EntityCardBody(
        state = state,
        onIntent = { intent ->
            when (intent) {
                EntityCardIntent.Toggle -> viewModel.onToggle()
                is EntityCardIntent.StepTemp -> viewModel.onStepTemp(intent.direction)
                EntityCardIntent.Trigger -> viewModel.onTrigger()
                EntityCardIntent.PlayPause -> viewModel.onPlayPause()
            }
        },
        size = size,
        modifier = modifier,
    )
}

// Koin-free body — previews + tests drive this directly with a UI state.
// Composables here consume only UI-layer types (`EntityCardUiState`,
// `EntityCardIntent`) — no domain models.
@Composable
internal fun EntityCardBody(
    state: EntityCardUiState,
    onIntent: (EntityCardIntent) -> Unit,
    size: EntityCardSize,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is EntityCardUiState.Toggle ->
            ToggleableEntityCard(state = state, size = size, onIntent = onIntent, modifier = modifier)
        is EntityCardUiState.Stepper ->
            StepperEntityCard(state = state, size = size, onIntent = onIntent, modifier = modifier)
        is EntityCardUiState.Trigger ->
            TriggerEntityCard(state = state, size = size, onIntent = onIntent, modifier = modifier)
        is EntityCardUiState.Media ->
            MediaEntityCard(state = state, size = size, onIntent = onIntent, modifier = modifier)
        is EntityCardUiState.Unknown ->
            UnknownEntityCard(state = state, size = size, modifier = modifier)
        is EntityCardUiState.ReadOnly ->
            ReadOnlyEntityCard(state = state, size = size, modifier = modifier)
    }
}

@Composable
private fun ReadOnlyEntityCard(
    state: EntityCardUiState.ReadOnly,
    size: EntityCardSize,
    modifier: Modifier = Modifier,
) {
    val label = rememberStaleSuffix(state.label, state.isStale, state.lastChanged)
    val contentDesc = rememberStaleSuffix("${state.title}, ${state.label}", state.isStale, state.lastChanged)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (state.isStale) 0.5f else 1f)
            .semantics { contentDescription = contentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = state.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.bodyLarge)
            // BinarySensor humanisation (e.g. "Detected"/"Clear") deferred — On/Off acceptable for MVP.
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleableEntityCard(
    state: EntityCardUiState.Toggle,
    size: EntityCardSize,
    onIntent: (EntityCardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val labelBase = stateLabel(if (state.isOn) "on" else "off")
    val contentDesc = rememberStaleSuffix("${state.title}, $labelBase", state.isStale, state.lastChanged)
    val shownLabel = rememberStaleSuffix(labelBase, state.isStale, state.lastChanged)
    val stateDesc = if (state.isOn) "on" else "off"

    // Mirrors Motion.entityStateChange (TweenSpec<Float>) re-typed for Color.
    val bg by animateColorAsState(
        targetValue = if (state.isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    )

    val nudgePx = with(density) { NUDGE_DP.dp.toPx() }
    val nudge = remember(state.entityId) { Animatable(0f) }
    LaunchedEffect(state.rejectionCounter) {
        if (state.rejectionCounter > 0L) {
            nudge.snapTo(0f)
            nudge.animateTo(nudgePx, Motion.snapBackRejection)
            nudge.animateTo(0f, Motion.snapBackRejection)
        }
    }

    val interactionSource = remember(state.entityId) { MutableInteractionSource() }

    // Touch-down dispatch fires on physical PressInteraction.Press BEFORE toggleable's
    // release-based onValueChange. VM debounces re-presses internally while optimistic
    // is in flight.
    LaunchedEffect(interactionSource, state.isInteractable) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press && state.isInteractable) {
                onIntent(EntityCardIntent.Toggle)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .background(bg, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { translationX = nudge.value }
            .alpha(if (state.isStale) 0.5f else 1f)
            .semantics {
                contentDescription = contentDesc
                role = Role.Switch
                stateDescription = stateDesc
            }
            .toggleable(
                value = state.isOn,
                enabled = state.isInteractable,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onValueChange = {
                    // Release-based path. Touch-down handler above already fired for physical
                    // presses; this branch covers a11y synthetic clicks (TalkBack double-tap,
                    // keyboard Enter, Switch Access) where PressInteraction may not arrive.
                    onIntent(EntityCardIntent.Toggle)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = state.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                shownLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.isOn,
            onCheckedChange = null,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun StepperEntityCard(
    state: EntityCardUiState.Stepper,
    size: EntityCardSize,
    onIntent: (EntityCardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val nudgePx = with(density) { NUDGE_DP.dp.toPx() }
    val nudge = remember(state.entityId) { Animatable(0f) }
    LaunchedEffect(state.rejectionCounter) {
        if (state.rejectionCounter > 0L) {
            nudge.snapTo(0f)
            nudge.animateTo(nudgePx, Motion.snapBackRejection)
            nudge.animateTo(0f, Motion.snapBackRejection)
        }
    }

    val tempDescriptor = if (state.hasTarget) "target ${state.formattedTemp}" else state.formattedTemp
    val rowContentDesc = rememberStaleSuffix(
        "${state.title}, ${state.currentLabel}, $tempDescriptor",
        state.isStale,
        state.lastChanged,
    )
    val shownSubtitle = rememberStaleSuffix(state.currentLabel, state.isStale, state.lastChanged)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { translationX = nudge.value }
            .alpha(if (state.isStale) 0.5f else 1f)
            .semantics { contentDescription = rowContentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = state.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                shownSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onIntent(EntityCardIntent.StepTemp(-1)) },
            enabled = state.isInteractable,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = "Decrease temperature" },
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = null)
        }
        Text(
            text = state.formattedTemp,
            fontSize = 20.sp,
            fontWeight = FontWeight.W800,
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(
            onClick = { onIntent(EntityCardIntent.StepTemp(1)) },
            enabled = state.isInteractable,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = "Increase temperature" },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
        }
    }
}

@Composable
private fun TriggerEntityCard(
    state: EntityCardUiState.Trigger,
    size: EntityCardSize,
    onIntent: (EntityCardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val pulse = remember(state.entityId) { Animatable(1f) }
    LaunchedEffect(state.triggerCounter) {
        if (state.triggerCounter > 0L) {
            pulse.snapTo(1f)
            pulse.animateTo(TRIGGER_PULSE_PEAK, Motion.entityStateChange)
            pulse.animateTo(1f, Motion.entityStateChange)
        }
    }

    val nudgePx = with(density) { NUDGE_DP.dp.toPx() }
    val nudge = remember(state.entityId) { Animatable(0f) }
    LaunchedEffect(state.rejectionCounter) {
        if (state.rejectionCounter > 0L) {
            nudge.snapTo(0f)
            nudge.animateTo(nudgePx, Motion.snapBackRejection)
            nudge.animateTo(0f, Motion.snapBackRejection)
        }
    }

    val rowContentDesc = rememberStaleSuffix("${state.title}, ${state.subtitle}", state.isStale, state.lastChanged)
    val shownSubtitle = rememberStaleSuffix(state.subtitle, state.isStale, state.lastChanged)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
                translationX = nudge.value
            }
            .clickable(
                role = Role.Button,
                enabled = state.isInteractable,
            ) { onIntent(EntityCardIntent.Trigger) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (state.isStale) 0.5f else 1f)
            .semantics { contentDescription = rowContentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = state.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(state.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                shownSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Run",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clearAndSetSemantics { },
        )
    }
}

@Composable
private fun MediaEntityCard(
    state: EntityCardUiState.Media,
    size: EntityCardSize,
    onIntent: (EntityCardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val nudgePx = with(density) { NUDGE_DP.dp.toPx() }
    val nudge = remember(state.entityId) { Animatable(0f) }
    LaunchedEffect(state.rejectionCounter) {
        if (state.rejectionCounter > 0L) {
            nudge.snapTo(0f)
            nudge.animateTo(nudgePx, Motion.snapBackRejection)
            nudge.animateTo(0f, Motion.snapBackRejection)
        }
    }

    val rowContentDesc = rememberStaleSuffix("${state.title}, ${state.subtitle}", state.isStale, state.lastChanged)
    val shownSubtitle = rememberStaleSuffix(state.subtitle, state.isStale, state.lastChanged)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { translationX = nudge.value }
            .alpha(if (state.isStale) 0.5f else 1f)
            .semantics { contentDescription = rowContentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = state.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                shownSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { onIntent(EntityCardIntent.PlayPause) },
            enabled = state.isInteractable,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
            )
        }
    }
}

@Composable
private fun UnknownEntityCard(
    state: EntityCardUiState.Unknown,
    size: EntityCardSize,
    modifier: Modifier = Modifier,
) {
    val rowContentDesc = rememberStaleSuffix("${state.title}, ${state.subtitle}", state.isStale, state.lastChanged)
    val shownSubtitle = rememberStaleSuffix(state.subtitle, state.isStale, state.lastChanged)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (state.isStale) 0.5f else 1f)
            .semantics { contentDescription = rowContentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = state.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                shownSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Composable wrapper that re-emits every 60s while stale, so "Xm ago" advances over time
// instead of freezing at the value sampled when the entity last emitted.
@Composable
internal fun rememberStaleSuffix(base: String, isStale: Boolean, lastChanged: Instant?): String {
    if (!isStale || lastChanged == null) return base
    val initial = appendStaleSuffix(base, isStale, lastChanged)
    val state = produceState(initialValue = initial, base, isStale, lastChanged) {
        while (true) {
            delay(60_000L)
            value = appendStaleSuffix(base, isStale, lastChanged)
        }
    }
    return state.value
}

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun appendStaleSuffix(base: String, isStale: Boolean, lastChanged: Instant?): String {
    if (!isStale || lastChanged == null) return base
    val delta = kotlin.time.Clock.System.now().toEpochMilliseconds() - lastChanged.toEpochMilliseconds()
    val suffix = when {
        delta < 60_000L -> ", updated just now"
        else -> ", updated ${(delta / 60_000L).coerceAtLeast(0L)}m ago"
    }
    return base + suffix
}

// Locale-safe half-degree formatter — KMP iOS Native lacks String.format. Half-degree
// increments only ever produce ".0" or ".5", so a simple round-and-cat is sufficient.
internal fun formatTemp(value: Double): String {
    // Sign derived from input, not rounded magnitude — preserves sign for values like
    // -0.04 that round to 0.0 (would otherwise lose the minus).
    val tenths = (abs(value) * 10.0).roundToInt()
    var whole = tenths / 10
    var frac = tenths % 10
    // Floating-point drift can yield frac == 10 in edge cases; normalize.
    if (frac == 10) {
        whole += 1
        frac = 0
    }
    val sign = if (value < 0 && (whole != 0 || frac != 0)) "-" else ""
    return "$sign$whole.$frac°"
}

internal fun stateLabel(state: String): String = when (state) {
    "" -> "Unknown"
    else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
