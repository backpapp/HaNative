package com.backpapp.hanative.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.usecase.CallServiceUseCase
import com.backpapp.hanative.domain.usecase.ObserveEntityStateUseCase
import com.backpapp.hanative.platform.HapticPattern
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val OPTIMISTIC_TIMEOUT_MS = 5_000L
private const val STEPPER_DELTA_C = 0.5
private val UNINTERACTABLE_STATES = setOf("unavailable", "unknown")

private data class TransientState(
    val optimisticOn: Boolean? = null,
    val optimisticTemp: Double? = null,
    val rejectionCounter: Long = 0L,
    val triggerCounter: Long = 0L,
    val isStale: Boolean = false,
)

class EntityCardViewModel(
    private val entityId: String,
    observe: ObserveEntityStateUseCase,
    private val call: CallServiceUseCase,
) : ViewModel() {

    private val entityFlow: StateFlow<HaEntity?> = observe(entityId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = null,
    )

    private val _transient = MutableStateFlow(TransientState())

    private val _haptics = MutableSharedFlow<HapticPattern>(extraBufferCapacity = 16)
    val haptics: SharedFlow<HapticPattern> = _haptics.asSharedFlow()

    private var optimisticToggleTimeout: Job? = null
    private var optimisticTempTimeout: Job? = null

    val state: StateFlow<EntityCardUiState> = combine(entityFlow, _transient) { entity, transient ->
        derive(entityId, entity, transient)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = derive(entityId, null, TransientState()),
    )

    init {
        viewModelScope.launch {
            entityFlow.collect { entity ->
                val t = _transient.value
                val opt = t.optimisticOn
                if (opt != null && entity != null && (entity.state == "on") == opt) {
                    optimisticToggleTimeout?.cancel()
                    _transient.update { it.copy(optimisticOn = null) }
                }
                val ot = t.optimisticTemp
                val target = (entity as? HaEntity.Climate)?.targetTemperature
                if (ot != null && target != null && abs(target - ot) <= 0.01) {
                    optimisticTempTimeout?.cancel()
                    _transient.update { it.copy(optimisticTemp = null) }
                }
            }
        }
    }

    fun setStale(stale: Boolean) {
        _transient.update { it.copy(isStale = stale) }
    }

    fun onToggle() {
        val current = state.value as? EntityCardUiState.Toggle ?: return
        if (!current.isInteractable) return
        if (_transient.value.optimisticOn != null) return
        val nextOn = !current.isOn
        _haptics.tryEmit(if (nextOn) HapticPattern.ToggleOn else HapticPattern.ToggleOff)
        _transient.update { it.copy(optimisticOn = nextOn) }
        viewModelScope.launch {
            val result = call("homeassistant", "toggle", entityId)
            if (result.isFailure) {
                _haptics.tryEmit(HapticPattern.ActionRejected)
                _transient.update {
                    it.copy(
                        optimisticOn = null,
                        rejectionCounter = it.rejectionCounter + 1,
                    )
                }
            }
        }
        scheduleToggleTimeout(nextOn)
    }

    fun onStepTemp(direction: Int) {
        val entity = entityFlow.value as? HaEntity.Climate ?: return
        if (entity.state in UNINTERACTABLE_STATES) return
        val current = state.value as? EntityCardUiState.Stepper ?: return
        if (!current.isInteractable) return

        val displayed = _transient.value.optimisticTemp
            ?: entity.targetTemperature
            ?: entity.currentTemperature
            ?: 0.0
        val minTemp = (entity.attributes["min_temp"] as? Number)?.toDouble()
        val maxTemp = (entity.attributes["max_temp"] as? Number)?.toDouble()
        val step = (entity.attributes["target_temp_step"] as? Number)?.toDouble() ?: STEPPER_DELTA_C

        val raw = displayed + direction * step
        val clamped = when {
            minTemp != null && maxTemp != null -> raw.coerceIn(minTemp, maxTemp)
            minTemp != null -> raw.coerceAtLeast(minTemp)
            maxTemp != null -> raw.coerceAtMost(maxTemp)
            else -> raw
        }
        if (abs(clamped - displayed) < 0.001) {
            _haptics.tryEmit(HapticPattern.ActionRejected)
            _transient.update { it.copy(rejectionCounter = it.rejectionCounter + 1) }
            return
        }
        _haptics.tryEmit(if (direction > 0) HapticPattern.StepperInc else HapticPattern.StepperDec)
        _transient.update { it.copy(optimisticTemp = clamped) }
        viewModelScope.launch {
            val result = call(
                "climate",
                "set_temperature",
                entityId,
                mapOf("temperature" to clamped),
            )
            if (result.isFailure) {
                _haptics.tryEmit(HapticPattern.ActionRejected)
                _transient.update {
                    it.copy(
                        optimisticTemp = null,
                        rejectionCounter = it.rejectionCounter + 1,
                    )
                }
            }
        }
        scheduleTempTimeout(clamped)
    }

    fun onTrigger() {
        val entity = entityFlow.value ?: return
        if (entity.state in UNINTERACTABLE_STATES) return
        val domain = if (entity is HaEntity.Script) "script" else "scene"
        _haptics.tryEmit(HapticPattern.ActionTriggered)
        viewModelScope.launch {
            val result = call(domain, "turn_on", entityId)
            if (result.isFailure) {
                _haptics.tryEmit(HapticPattern.ActionRejected)
                _transient.update { it.copy(rejectionCounter = it.rejectionCounter + 1) }
            } else {
                _transient.update { it.copy(triggerCounter = it.triggerCounter + 1) }
            }
        }
    }

    fun onPlayPause() {
        val entity = entityFlow.value as? HaEntity.MediaPlayer ?: return
        if (entity.state in UNINTERACTABLE_STATES) return
        _haptics.tryEmit(HapticPattern.ActionTriggered)
        viewModelScope.launch {
            val result = call("media_player", "media_play_pause", entityId)
            if (result.isFailure) {
                _haptics.tryEmit(HapticPattern.ActionRejected)
                _transient.update { it.copy(rejectionCounter = it.rejectionCounter + 1) }
            }
        }
    }

    private fun scheduleToggleTimeout(target: Boolean) {
        optimisticToggleTimeout?.cancel()
        optimisticToggleTimeout = viewModelScope.launch {
            delay(OPTIMISTIC_TIMEOUT_MS)
            val t = _transient.value
            val echoed = (entityFlow.value?.state == "on") == target
            if (t.optimisticOn == target && !echoed) {
                _haptics.tryEmit(HapticPattern.ActionRejected)
                _transient.update {
                    it.copy(
                        optimisticOn = null,
                        rejectionCounter = it.rejectionCounter + 1,
                    )
                }
            }
        }
    }

    private fun scheduleTempTimeout(target: Double) {
        optimisticTempTimeout?.cancel()
        optimisticTempTimeout = viewModelScope.launch {
            delay(OPTIMISTIC_TIMEOUT_MS)
            val t = _transient.value
            val current = (entityFlow.value as? HaEntity.Climate)?.targetTemperature ?: 0.0
            if (t.optimisticTemp == target && abs(current - target) > 0.01) {
                _haptics.tryEmit(HapticPattern.ActionRejected)
                _transient.update {
                    it.copy(
                        optimisticTemp = null,
                        rejectionCounter = it.rejectionCounter + 1,
                    )
                }
            }
        }
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

private fun derive(
    entityId: String,
    entity: HaEntity?,
    transient: TransientState,
): EntityCardUiState {
    val title = friendlyName(entity, entityId)
    val icon = domainIcon(entity)
    val isStale = transient.isStale
    val lastChanged = entity?.lastChanged
    val rejects = transient.rejectionCounter

    return when (entity) {
        is HaEntity.Light, is HaEntity.Switch, is HaEntity.InputBoolean -> {
            val realOn = entity.state == "on"
            val displayedOn = transient.optimisticOn ?: realOn
            val interactable = entity.state !in UNINTERACTABLE_STATES
            EntityCardUiState.Toggle(
                entityId = entityId,
                title = title,
                icon = icon,
                isStale = isStale,
                lastChanged = lastChanged,
                rejectionCounter = rejects,
                isOn = displayedOn,
                isInteractable = interactable,
            )
        }
        is HaEntity.Climate -> {
            val displayed = transient.optimisticTemp
                ?: entity.targetTemperature
                ?: entity.currentTemperature
                ?: 0.0
            val currentLabel = entity.currentTemperature?.let { "Current ${formatTemp(it)}" }
                ?: stateLabel(entity.state)
            val hasTarget = entity.targetTemperature != null || transient.optimisticTemp != null
            EntityCardUiState.Stepper(
                entityId = entityId,
                title = title,
                icon = icon,
                isStale = isStale,
                lastChanged = lastChanged,
                rejectionCounter = rejects,
                currentLabel = currentLabel,
                formattedTemp = formatTemp(displayed),
                hasTarget = hasTarget,
                isInteractable = entity.state !in UNINTERACTABLE_STATES,
            )
        }
        is HaEntity.Script, is HaEntity.Scene -> EntityCardUiState.Trigger(
            entityId = entityId,
            title = title,
            icon = icon,
            isStale = isStale,
            lastChanged = lastChanged,
            rejectionCounter = rejects,
            subtitle = stateLabel(entity.state),
            triggerCounter = transient.triggerCounter,
            isInteractable = entity.state !in UNINTERACTABLE_STATES,
        )
        is HaEntity.MediaPlayer -> {
            val displayTitle = entity.mediaTitle?.takeIf { it.isNotBlank() } ?: title
            val subtitle = when (entity.state) {
                "playing" -> "Playing"
                "paused" -> "Paused"
                else -> stateLabel(entity.state)
            }
            EntityCardUiState.Media(
                entityId = entityId,
                title = displayTitle,
                icon = icon,
                isStale = isStale,
                lastChanged = lastChanged,
                rejectionCounter = rejects,
                subtitle = subtitle,
                isPlaying = entity.state == "playing",
                isInteractable = entity.state !in UNINTERACTABLE_STATES,
            )
        }
        is HaEntity.Unknown -> EntityCardUiState.Unknown(
            entityId = entityId,
            title = entity.entityId,
            icon = Icons.Outlined.HelpOutline,
            isStale = isStale,
            lastChanged = lastChanged,
            rejectionCounter = rejects,
            subtitle = stateLabel(entity.state),
        )
        is HaEntity.Sensor -> {
            val unit = entity.unit
            val label = stateLabel(entity.state) + if (!unit.isNullOrBlank()) " $unit" else ""
            EntityCardUiState.ReadOnly(
                entityId = entityId,
                title = title,
                icon = icon,
                isStale = isStale,
                lastChanged = lastChanged,
                rejectionCounter = rejects,
                label = label,
            )
        }
        is HaEntity.BinarySensor, null -> EntityCardUiState.ReadOnly(
            entityId = entityId,
            title = title,
            icon = icon,
            isStale = isStale,
            lastChanged = lastChanged,
            rejectionCounter = rejects,
            label = stateLabel(entity?.state ?: "unknown"),
        )
        else -> EntityCardUiState.ReadOnly(
            entityId = entityId,
            title = title,
            icon = icon,
            isStale = isStale,
            lastChanged = lastChanged,
            rejectionCounter = rejects,
            label = stateLabel(entity.state),
        )
    }
}
