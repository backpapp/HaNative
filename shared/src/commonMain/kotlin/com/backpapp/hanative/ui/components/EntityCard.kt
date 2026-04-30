// EntityCard — toggleable + read-only variants for the dashboard.
//
// CALLER PATTERN (Story 4.6 dashboard list):
//
//   LazyColumn {
//       items(cards, key = { it.entityId }) { card ->
//           key(card.entityId) { EntityCard(entityId = card.entityId) }
//       }
//   }
//
// Each EntityCard subscribes to its own entity slice via
// ObserveEntityStateUseCase, so a single entity update only recomposes
// its own card.

package com.backpapp.hanative.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.usecase.CallServiceUseCase
import com.backpapp.hanative.domain.usecase.ObserveEntityStateUseCase
import com.backpapp.hanative.platform.HapticPattern
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val OPTIMISTIC_TIMEOUT_MS = 5_000L
private const val NUDGE_DP = -8

private val UNINTERACTABLE_STATES = setOf("unavailable", "unknown")

enum class EntityCardSize { Standard, Compact }

enum class EntityCardPreviewState { Default, Active, Stale, Optimistic, Error }

@Composable
fun EntityCard(
    entityId: String,
    modifier: Modifier = Modifier,
    size: EntityCardSize = EntityCardSize.Standard,
    isStale: Boolean = false,
) {
    val observe: ObserveEntityStateUseCase = koinInject()
    val call: CallServiceUseCase = koinInject()

    val flow = remember(entityId) { observe(entityId) }
    val entity by flow.collectAsState(initial = null)

    EntityCardBody(
        entityId = entityId,
        entity = entity,
        size = size,
        isStale = isStale,
        modifier = modifier,
        onToggle = { _ ->
            // homeassistant.toggle works for light/switch/input_boolean
            call("homeassistant", "toggle", entityId)
        },
    )
}

@Composable
internal fun EntityCardBody(
    entityId: String,
    entity: HaEntity?,
    size: EntityCardSize,
    isStale: Boolean,
    modifier: Modifier = Modifier,
    onToggle: suspend (nextOn: Boolean) -> Result<Unit> = { Result.success(Unit) },
    forcedOptimisticOn: Boolean? = null,
    forcedRejected: Boolean = false,
) {
    when (entity) {
        is HaEntity.Light, is HaEntity.Switch, is HaEntity.InputBoolean ->
            ToggleableEntityCard(
                entityId = entityId,
                entity = entity,
                size = size,
                isStale = isStale,
                onToggle = onToggle,
                forcedOptimisticOn = forcedOptimisticOn,
                forcedRejected = forcedRejected,
                modifier = modifier,
            )
        is HaEntity.Sensor, is HaEntity.BinarySensor, null ->
            ReadOnlyEntityCard(
                entityId = entityId,
                entity = entity,
                size = size,
                isStale = isStale,
                modifier = modifier,
            )
        else ->
            // Unsupported subtypes (Climate/Cover/etc.) fall back to read-only render in this story.
            ReadOnlyEntityCard(
                entityId = entityId,
                entity = entity,
                size = size,
                isStale = isStale,
                modifier = modifier,
            )
    }
}

@Composable
private fun ReadOnlyEntityCard(
    entityId: String,
    entity: HaEntity?,
    size: EntityCardSize,
    isStale: Boolean,
    modifier: Modifier = Modifier,
) {
    val name = friendlyName(entity, entityId)
    val unit = (entity as? HaEntity.Sensor)?.unit
    val baseLabel = stateLabel(entity?.state ?: "unknown") + if (!unit.isNullOrBlank()) " $unit" else ""
    val staleSuffix = staleSuffix(isStale, entity)
    val label = baseLabel + staleSuffix

    val contentDesc = "$name, $baseLabel$staleSuffix".trim()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (isStale) 0.5f else 1f)
            .semantics { contentDescription = contentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = domainIcon(entity),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
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
    entityId: String,
    entity: HaEntity?,
    size: EntityCardSize,
    isStale: Boolean,
    onToggle: suspend (nextOn: Boolean) -> Result<Unit>,
    forcedOptimisticOn: Boolean?,
    forcedRejected: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticEngine.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var optimisticOn: Boolean? by remember(entityId) { mutableStateOf(forcedOptimisticOn) }
    var rejectTrigger by remember(entityId) { mutableLongStateOf(if (forcedRejected) 1L else 0L) }

    val realOn = entity?.state == "on"
    val displayedOn = optimisticOn ?: realOn

    val isInteractable = entity != null && entity.state !in UNINTERACTABLE_STATES

    // Reconciliation: clear optimistic when WebSocket echoes the expected state.
    LaunchedEffect(entity?.state, optimisticOn) {
        val o = optimisticOn
        if (o != null && entity != null && (entity.state == "on") == o) {
            optimisticOn = null
        }
    }

    // Optimistic timeout — treat as rejection if echo never lands.
    LaunchedEffect(optimisticOn) {
        val o = optimisticOn ?: return@LaunchedEffect
        delay(OPTIMISTIC_TIMEOUT_MS)
        if (optimisticOn == o && (entity?.state == "on") != o) {
            haptic.fire(HapticPattern.ActionRejected)
            rejectTrigger += 1L
            optimisticOn = null
        }
    }

    // Snap-back animation: nudge -8dp then return to 0.
    val nudgePx = with(density) { NUDGE_DP.dp.toPx() }
    val nudge = remember(entityId) { Animatable(if (forcedRejected) nudgePx else 0f) }
    LaunchedEffect(rejectTrigger) {
        if (rejectTrigger > 0L) {
            nudge.snapTo(0f)
            nudge.animateTo(nudgePx, Motion.snapBackRejection)
            nudge.animateTo(0f, Motion.snapBackRejection)
        }
    }

    val name = friendlyName(entity, entityId)
    val label = stateLabel(if (displayedOn) "on" else "off")
    val staleSuffix = staleSuffix(isStale, entity)

    // Mirrors Motion.entityStateChange (TweenSpec<Float>) re-typed for Color.
    val bg by animateColorAsState(
        targetValue = if (displayedOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    )

    val contentDesc = "$name, $label$staleSuffix"
    val stateDesc = if (displayedOn) "on" else "off"
    val interactionSource = remember(entityId) { MutableInteractionSource() }

    // Touch-down haptic + optimistic + service. Fires on physical PressInteraction.Press
    // (touch-down) BEFORE toggleable's release-based onValueChange. Debounces re-presses
    // while an optimistic update is already in flight.
    LaunchedEffect(interactionSource, isInteractable) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press &&
                isInteractable &&
                optimisticOn == null
            ) {
                triggerToggle(
                    nextOn = !displayedOn,
                    haptic = haptic,
                    scope = scope,
                    onToggle = onToggle,
                    setOptimistic = { optimisticOn = it },
                    onRejected = { rejectTrigger += 1L },
                )
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
            .alpha(if (isStale) 0.5f else 1f)
            .semantics {
                contentDescription = contentDesc
                role = Role.Switch
                stateDescription = stateDesc
            }
            .toggleable(
                value = displayedOn,
                enabled = isInteractable,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onValueChange = {
                    // Release-based path. Touch-down handler above already fired for physical
                    // presses; this branch covers a11y synthetic clicks (TalkBack double-tap,
                    // keyboard Enter, Switch Access) where PressInteraction may not arrive.
                    if (optimisticOn == null) {
                        triggerToggle(
                            nextOn = !displayedOn,
                            haptic = haptic,
                            scope = scope,
                            onToggle = onToggle,
                            setOptimistic = { optimisticOn = it },
                            onRejected = { rejectTrigger += 1L },
                        )
                    }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = domainIcon(entity),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                label + staleSuffix,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = displayedOn,
            onCheckedChange = null,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clearAndSetSemantics { },
        )
    }
}

private fun triggerToggle(
    nextOn: Boolean,
    haptic: com.backpapp.hanative.platform.HapticEngine,
    scope: kotlinx.coroutines.CoroutineScope,
    onToggle: suspend (Boolean) -> Result<Unit>,
    setOptimistic: (Boolean?) -> Unit,
    onRejected: () -> Unit,
) {
    haptic.fire(if (nextOn) HapticPattern.ToggleOn else HapticPattern.ToggleOff)
    setOptimistic(nextOn)
    scope.launch {
        val result = onToggle(nextOn)
        if (result.isFailure) {
            haptic.fire(HapticPattern.ActionRejected)
            setOptimistic(null)
            onRejected()
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun staleSuffix(isStale: Boolean, entity: HaEntity?): String {
    if (!isStale || entity == null) return ""
    val delta = kotlin.time.Clock.System.now().toEpochMilliseconds() - entity.lastChanged.toEpochMilliseconds()
    return when {
        delta < 60_000L -> ", updated just now"
        else -> ", updated ${(delta / 60_000L).coerceAtLeast(0L)}m ago"
    }
}

internal fun friendlyName(entity: HaEntity?, fallbackId: String): String {
    val attr = entity?.attributes?.get("friendly_name") as? String
    if (!attr.isNullOrBlank()) return attr
    val raw = entity?.entityId ?: fallbackId
    val withoutDomain = raw.substringAfter('.', raw)
    return withoutDomain
        .split('_')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

internal fun stateLabel(state: String): String = when (state) {
    "" -> "Unknown"
    else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal fun domainIcon(entity: HaEntity?): ImageVector = when (entity) {
    is HaEntity.Light -> Icons.Outlined.Lightbulb
    is HaEntity.Switch -> Icons.Outlined.ToggleOn
    is HaEntity.InputBoolean -> Icons.Outlined.RadioButtonChecked
    is HaEntity.Sensor -> Icons.Outlined.Sensors
    is HaEntity.BinarySensor -> Icons.Outlined.Notifications
    else -> Icons.Outlined.Sensors
}
