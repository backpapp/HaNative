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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.usecase.CallServiceUseCase
import com.backpapp.hanative.domain.usecase.ObserveEntityStateUseCase
import com.backpapp.hanative.platform.HapticPattern
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.koin.compose.koinInject

private const val OPTIMISTIC_TIMEOUT_MS = 5_000L
private const val NUDGE_DP = -8
private const val STEPPER_DELTA_C = 0.5
private const val TRIGGER_PULSE_PEAK = 1.04f

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
        call = call,
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
    call: CallServiceUseCase? = null,
    forcedOptimisticOn: Boolean? = null,
    forcedOptimisticTemp: Double? = null,
    forcedRejected: Boolean = false,
    forcedTriggerPulse: Boolean = false,
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
        is HaEntity.Climate ->
            StepperEntityCard(
                entity = entity,
                size = size,
                isStale = isStale,
                call = call,
                forcedOptimisticTemp = forcedOptimisticTemp,
                forcedRejected = forcedRejected,
                modifier = modifier,
            )
        is HaEntity.Script, is HaEntity.Scene ->
            TriggerEntityCard(
                entity = entity,
                size = size,
                isStale = isStale,
                call = call,
                forcedTriggerPulse = forcedTriggerPulse,
                modifier = modifier,
            )
        is HaEntity.MediaPlayer ->
            MediaEntityCard(
                entity = entity,
                size = size,
                isStale = isStale,
                call = call,
                modifier = modifier,
            )
        is HaEntity.Unknown ->
            UnknownEntityCard(
                entity = entity,
                size = size,
                isStale = isStale,
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
            // Unsupported subtypes (Cover/InputSelect) fall back to read-only render in this story.
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
    val label = rememberStaleSuffix(baseLabel, isStale, entity?.lastChanged)
    val contentDesc = rememberStaleSuffix("$name, $baseLabel", isStale, entity?.lastChanged)

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
    val contentDesc = rememberStaleSuffix("$name, $label", isStale, entity?.lastChanged)
    val shownLabel = rememberStaleSuffix(label, isStale, entity?.lastChanged)
    val stateDesc = if (displayedOn) "on" else "off"

    // Mirrors Motion.entityStateChange (TweenSpec<Float>) re-typed for Color.
    val bg by animateColorAsState(
        targetValue = if (displayedOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    )

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
                shownLabel,
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

@Composable
private fun StepperEntityCard(
    entity: HaEntity.Climate,
    size: EntityCardSize,
    isStale: Boolean,
    call: CallServiceUseCase?,
    forcedOptimisticTemp: Double?,
    forcedRejected: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticEngine.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var optimisticTemp: Double? by remember(entity.entityId) { mutableStateOf(forcedOptimisticTemp) }
    var rejectTrigger by remember(entity.entityId) { mutableLongStateOf(if (forcedRejected) 1L else 0L) }

    val displayedTemp: Double =
        optimisticTemp ?: entity.targetTemperature ?: entity.currentTemperature ?: 0.0
    val formatted = formatTemp(displayedTemp)

    val disabled = entity.state in UNINTERACTABLE_STATES

    // HA-reported climate bounds — clamp +/- to these to avoid sending out-of-range
    // set_temperature calls. target_temp_step overrides STEPPER_DELTA_C when present.
    val minTemp = (entity.attributes["min_temp"] as? Number)?.toDouble()
    val maxTemp = (entity.attributes["max_temp"] as? Number)?.toDouble()
    val tempStep = (entity.attributes["target_temp_step"] as? Number)?.toDouble() ?: STEPPER_DELTA_C

    // Reconcile: clear optimistic once HA echoes our target.
    LaunchedEffect(entity.targetTemperature) {
        val opt = optimisticTemp
        val target = entity.targetTemperature
        if (opt != null && target != null && abs(target - opt) <= 0.01) {
            optimisticTemp = null
        }
    }

    // Optimistic timeout.
    LaunchedEffect(optimisticTemp) {
        val o = optimisticTemp ?: return@LaunchedEffect
        delay(OPTIMISTIC_TIMEOUT_MS)
        val target = entity.targetTemperature ?: 0.0
        if (optimisticTemp == o && abs(target - o) > 0.01) {
            haptic.fire(HapticPattern.ActionRejected)
            rejectTrigger += 1L
            optimisticTemp = null
        }
    }

    // Snap-back nudge — start at rest; LaunchedEffect drives the animation when
    // rejectTrigger increments (incl. forced previews where rejectTrigger starts at 1L).
    val nudgePx = with(density) { NUDGE_DP.dp.toPx() }
    val nudge = remember(entity.entityId) { Animatable(0f) }
    LaunchedEffect(rejectTrigger) {
        if (rejectTrigger > 0L) {
            nudge.snapTo(0f)
            nudge.animateTo(nudgePx, Motion.snapBackRejection)
            nudge.animateTo(0f, Motion.snapBackRejection)
        }
    }

    fun step(direction: Int, pattern: HapticPattern) {
        if (disabled || call == null) return
        val raw = displayedTemp + direction * tempStep
        val clamped = when {
            minTemp != null && maxTemp != null -> raw.coerceIn(minTemp, maxTemp)
            minTemp != null -> raw.coerceAtLeast(minTemp)
            maxTemp != null -> raw.coerceAtMost(maxTemp)
            else -> raw
        }
        // Already at bound — reject without firing service call.
        if (abs(clamped - displayedTemp) < 0.001) {
            haptic.fire(HapticPattern.ActionRejected)
            rejectTrigger += 1L
            return
        }
        haptic.fire(pattern)
        optimisticTemp = clamped
        scope.launch {
            // Heat-cool dual-setpoint deferred to Story 4.4.x or later.
            val result = call("climate", "set_temperature", entity.entityId, mapOf("temperature" to clamped))
            if (result.isFailure) {
                haptic.fire(HapticPattern.ActionRejected)
                rejectTrigger += 1L
                optimisticTemp = null
            }
        }
    }

    val name = friendlyName(entity, entity.entityId)
    val currentLabel = entity.currentTemperature?.let { "Current ${formatTemp(it)}" } ?: stateLabel(entity.state)
    // Spec wording: when no target reported (e.g. mode "off"), avoid "target X" framing
    // since the displayed value is the current temp or 0.0 fallback rather than a setpoint.
    val tempDescriptor = if (entity.targetTemperature != null || optimisticTemp != null) {
        "target $formatted"
    } else {
        formatted
    }
    val rowContentDesc = rememberStaleSuffix(
        "$name, $currentLabel, $tempDescriptor",
        isStale,
        entity.lastChanged,
    )
    val shownSubtitle = rememberStaleSuffix(currentLabel, isStale, entity.lastChanged)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { translationX = nudge.value }
            .alpha(if (isStale) 0.5f else 1f)
            .semantics { contentDescription = rowContentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = domainIcon(entity),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                shownSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { step(-1, HapticPattern.StepperDec) },
            enabled = !disabled,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = "Decrease temperature" },
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = null)
        }
        Text(
            text = formatted,
            fontSize = 20.sp,
            fontWeight = FontWeight.W800,
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(
            onClick = { step(1, HapticPattern.StepperInc) },
            enabled = !disabled,
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
    entity: HaEntity,
    size: EntityCardSize,
    isStale: Boolean,
    call: CallServiceUseCase?,
    forcedTriggerPulse: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticEngine.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var triggerCounter by remember(entity.entityId) {
        mutableLongStateOf(if (forcedTriggerPulse) 1L else 0L)
    }
    var rejectTrigger by remember(entity.entityId) { mutableLongStateOf(0L) }

    val pulse = remember(entity.entityId) { Animatable(if (forcedTriggerPulse) TRIGGER_PULSE_PEAK else 1f) }
    LaunchedEffect(triggerCounter) {
        if (triggerCounter > 0L) {
            pulse.snapTo(1f)
            pulse.animateTo(TRIGGER_PULSE_PEAK, Motion.entityStateChange)
            pulse.animateTo(1f, Motion.entityStateChange)
        }
    }

    // Failure shake — same -8dp snap-back as Stepper/Toggleable for visual consistency.
    val nudgePx = with(density) { NUDGE_DP.dp.toPx() }
    val nudge = remember(entity.entityId) { Animatable(0f) }
    LaunchedEffect(rejectTrigger) {
        if (rejectTrigger > 0L) {
            nudge.snapTo(0f)
            nudge.animateTo(nudgePx, Motion.snapBackRejection)
            nudge.animateTo(0f, Motion.snapBackRejection)
        }
    }

    val disabled = entity.state in UNINTERACTABLE_STATES
    val domain = if (entity is HaEntity.Script) "script" else "scene"

    val name = friendlyName(entity, entity.entityId)
    val labelBase = stateLabel(entity.state)
    val rowContentDesc = rememberStaleSuffix("$name, $labelBase", isStale, entity.lastChanged)
    val shownSubtitle = rememberStaleSuffix(labelBase, isStale, entity.lastChanged)

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
                enabled = !disabled,
            ) {
                if (call == null) return@clickable
                haptic.fire(HapticPattern.ActionTriggered)
                scope.launch {
                    val result = call(domain, "turn_on", entity.entityId)
                    if (result.isFailure) {
                        haptic.fire(HapticPattern.ActionRejected)
                        rejectTrigger += 1L
                    } else {
                        triggerCounter += 1L
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (isStale) 0.5f else 1f)
            .semantics { contentDescription = rowContentDesc },
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
    entity: HaEntity.MediaPlayer,
    size: EntityCardSize,
    isStale: Boolean,
    call: CallServiceUseCase?,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticEngine.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val isPlaying = entity.state == "playing"
    val disabled = entity.state in UNINTERACTABLE_STATES

    var rejectTrigger by remember(entity.entityId) { mutableLongStateOf(0L) }
    val nudgePx = with(density) { NUDGE_DP.dp.toPx() }
    val nudge = remember(entity.entityId) { Animatable(0f) }
    LaunchedEffect(rejectTrigger) {
        if (rejectTrigger > 0L) {
            nudge.snapTo(0f)
            nudge.animateTo(nudgePx, Motion.snapBackRejection)
            nudge.animateTo(0f, Motion.snapBackRejection)
        }
    }

    // Treat blank media_title as null so an empty HA attribute doesn't leave the row title blank.
    val title = entity.mediaTitle?.takeIf { it.isNotBlank() } ?: friendlyName(entity, entity.entityId)
    val labelBase = when (entity.state) {
        "playing" -> "Playing"
        "paused" -> "Paused"
        else -> stateLabel(entity.state)
    }
    val rowContentDesc = rememberStaleSuffix("$title, $labelBase", isStale, entity.lastChanged)
    val shownSubtitle = rememberStaleSuffix(labelBase, isStale, entity.lastChanged)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { translationX = nudge.value }
            .alpha(if (isStale) 0.5f else 1f)
            .semantics { contentDescription = rowContentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = domainIcon(entity),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
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
            onClick = {
                if (call == null) return@IconButton
                haptic.fire(HapticPattern.ActionTriggered)
                scope.launch {
                    val result = call("media_player", "media_play_pause", entity.entityId)
                    if (result.isFailure) {
                        haptic.fire(HapticPattern.ActionRejected)
                        rejectTrigger += 1L
                    }
                }
            },
            enabled = !disabled,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
            )
        }
    }
}

@Composable
private fun UnknownEntityCard(
    entity: HaEntity.Unknown,
    size: EntityCardSize,
    isStale: Boolean,
    modifier: Modifier = Modifier,
) {
    // Per AC5: NEVER index entity.attributes — render must not crash on any shape.
    val labelBase = stateLabel(entity.state)
    val rowContentDesc = rememberStaleSuffix("${entity.entityId}, $labelBase", isStale, entity.lastChanged)
    val shownSubtitle = rememberStaleSuffix(labelBase, isStale, entity.lastChanged)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (size == EntityCardSize.Standard) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (isStale) 0.5f else 1f)
            .semantics { contentDescription = rowContentDesc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.HelpOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                entity.entityId,
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
    is HaEntity.Climate -> Icons.Outlined.Thermostat
    is HaEntity.Script -> Icons.Outlined.PlayArrow
    is HaEntity.Scene -> Icons.Outlined.AutoAwesome
    is HaEntity.MediaPlayer -> Icons.Outlined.PlayCircle
    is HaEntity.Unknown -> Icons.Outlined.HelpOutline
    else -> Icons.Outlined.Sensors
}
