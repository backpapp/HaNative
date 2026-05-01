package com.backpapp.hanative.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.backpapp.hanative.platform.HapticEngine
import com.backpapp.hanative.platform.HapticPattern
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.theme.HaNativeTheme
import kotlinx.datetime.Instant

private val previewInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

private object NoopHaptic : HapticEngine {
    override fun fire(pattern: HapticPattern) = Unit
}

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    HaNativeTheme {
        CompositionLocalProvider(LocalHapticEngine provides NoopHaptic) {
            Column { content() }
        }
    }
}

private fun toggleState(
    isOn: Boolean,
    isStale: Boolean = false,
    rejectionCounter: Long = 0L,
    isInteractable: Boolean = true,
) = EntityCardUiState.Toggle(
    entityId = "light.living_room",
    title = "Living Room",
    icon = Icons.Outlined.Lightbulb,
    isStale = isStale,
    lastChanged = previewInstant,
    rejectionCounter = rejectionCounter,
    isOn = isOn,
    isInteractable = isInteractable,
)

private fun readOnlyState(
    title: String = "Living Room Temp",
    label: String = "21.4 °C",
    isStale: Boolean = false,
) = EntityCardUiState.ReadOnly(
    entityId = "sensor.living_room_temp",
    title = title,
    icon = Icons.Outlined.Sensors,
    isStale = isStale,
    lastChanged = previewInstant,
    label = label,
)

private fun stepperState(
    currentLabel: String = "Current 20.5°",
    formattedTemp: String = "21.0°",
    hasTarget: Boolean = true,
    isStale: Boolean = false,
    rejectionCounter: Long = 0L,
    isInteractable: Boolean = true,
) = EntityCardUiState.Stepper(
    entityId = "climate.lounge",
    title = "Lounge Climate",
    icon = Icons.Outlined.Thermostat,
    isStale = isStale,
    lastChanged = previewInstant,
    rejectionCounter = rejectionCounter,
    currentLabel = currentLabel,
    formattedTemp = formattedTemp,
    hasTarget = hasTarget,
    isInteractable = isInteractable,
)

private fun triggerState(
    subtitle: String = "Off",
    triggerCounter: Long = 0L,
    isStale: Boolean = false,
    isInteractable: Boolean = true,
) = EntityCardUiState.Trigger(
    entityId = "script.goodnight",
    title = "Goodnight",
    icon = Icons.Outlined.PlayArrow,
    isStale = isStale,
    lastChanged = previewInstant,
    subtitle = subtitle,
    triggerCounter = triggerCounter,
    isInteractable = isInteractable,
)

private fun mediaState(
    title: String = "Beethoven Symphony 9",
    subtitle: String = "Playing",
    isPlaying: Boolean = true,
    isStale: Boolean = false,
    isInteractable: Boolean = true,
) = EntityCardUiState.Media(
    entityId = "media_player.living_room",
    title = title,
    icon = Icons.Outlined.PlayCircle,
    isStale = isStale,
    lastChanged = previewInstant,
    subtitle = subtitle,
    isPlaying = isPlaying,
    isInteractable = isInteractable,
)

private fun unknownState(
    subtitle: String = "Active",
    isStale: Boolean = false,
) = EntityCardUiState.Unknown(
    entityId = "vacuum.upstairs",
    title = "vacuum.upstairs",
    icon = Icons.Outlined.HelpOutline,
    isStale = isStale,
    lastChanged = previewInstant,
    subtitle = subtitle,
)

// ----- Toggleable -----

@Preview(name = "Toggleable_Default")
@Composable
private fun ToggleableDefault() = PreviewWrap {
    EntityCardBody(
        state = toggleState(isOn = false),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Toggleable_Active")
@Composable
private fun ToggleableActive() = PreviewWrap {
    EntityCardBody(
        state = toggleState(isOn = true),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Toggleable_Stale")
@Composable
private fun ToggleableStale() = PreviewWrap {
    EntityCardBody(
        state = toggleState(isOn = true, isStale = true),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// Optimistic = real state inverted while in flight; previewed by toggling isOn.
@Preview(name = "Toggleable_Optimistic")
@Composable
private fun ToggleableOptimistic() = PreviewWrap {
    EntityCardBody(
        state = toggleState(isOn = true),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Toggleable_Error")
@Composable
private fun ToggleableError() = PreviewWrap {
    EntityCardBody(
        state = toggleState(isOn = false, rejectionCounter = 1L),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// ----- ReadOnly -----

@Preview(name = "ReadOnly_Default")
@Composable
private fun ReadOnlyDefault() = PreviewWrap {
    EntityCardBody(
        state = readOnlyState(),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "ReadOnly_Active")
@Composable
private fun ReadOnlyActive() = PreviewWrap {
    EntityCardBody(
        state = EntityCardUiState.ReadOnly(
            entityId = "binary_sensor.front_door",
            title = "Front Door",
            icon = Icons.Outlined.Notifications,
            isStale = false,
            lastChanged = previewInstant,
            label = "On",
        ),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "ReadOnly_Stale")
@Composable
private fun ReadOnlyStale() = PreviewWrap {
    EntityCardBody(
        state = readOnlyState(isStale = true),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// No-op for read-only — renders identically to default.
@Preview(name = "ReadOnly_Optimistic")
@Composable
private fun ReadOnlyOptimistic() = PreviewWrap {
    EntityCardBody(
        state = readOnlyState(),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// No-op for read-only — renders identically to default.
@Preview(name = "ReadOnly_Error")
@Composable
private fun ReadOnlyError() = PreviewWrap {
    EntityCardBody(
        state = readOnlyState(),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// ----- Stepper -----

@Preview(name = "Stepper_Default")
@Composable
private fun StepperDefault() = PreviewWrap {
    EntityCardBody(
        state = stepperState(currentLabel = "Off", formattedTemp = "0.0°", hasTarget = false),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Stepper_Active")
@Composable
private fun StepperActive() = PreviewWrap {
    EntityCardBody(
        state = stepperState(),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Stepper_Stale")
@Composable
private fun StepperStale() = PreviewWrap {
    EntityCardBody(
        state = stepperState(isStale = true),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Stepper_Optimistic")
@Composable
private fun StepperOptimistic() = PreviewWrap {
    EntityCardBody(
        state = stepperState(formattedTemp = "21.5°"),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Stepper_Error")
@Composable
private fun StepperError() = PreviewWrap {
    EntityCardBody(
        state = stepperState(rejectionCounter = 1L),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// ----- Trigger -----

@Preview(name = "Trigger_Default")
@Composable
private fun TriggerDefault() = PreviewWrap {
    EntityCardBody(
        state = triggerState(),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Trigger_Active")
@Composable
private fun TriggerActive() = PreviewWrap {
    EntityCardBody(
        state = triggerState(subtitle = "On", triggerCounter = 1L),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Trigger_Stale")
@Composable
private fun TriggerStale() = PreviewWrap {
    EntityCardBody(
        state = triggerState(isStale = true),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// Trigger_Optimistic == Trigger_Active per spec — fire-and-forget.
@Preview(name = "Trigger_Optimistic")
@Composable
private fun TriggerOptimistic() = PreviewWrap {
    EntityCardBody(
        state = triggerState(subtitle = "On", triggerCounter = 1L),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Trigger_Error")
@Composable
private fun TriggerError() = PreviewWrap {
    EntityCardBody(
        state = triggerState(subtitle = "Unavailable", isInteractable = false),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// ----- Media -----

@Preview(name = "Media_Playing")
@Composable
private fun MediaPlaying() = PreviewWrap {
    EntityCardBody(
        state = mediaState(),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Media_Paused")
@Composable
private fun MediaPaused() = PreviewWrap {
    EntityCardBody(
        state = mediaState(subtitle = "Paused", isPlaying = false),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Media_Idle")
@Composable
private fun MediaIdle() = PreviewWrap {
    EntityCardBody(
        state = mediaState(title = "Living Room Speaker", subtitle = "Idle", isPlaying = false),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Media_Stale")
@Composable
private fun MediaStale() = PreviewWrap {
    EntityCardBody(
        state = mediaState(isStale = true),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Media_Error")
@Composable
private fun MediaError() = PreviewWrap {
    EntityCardBody(
        state = mediaState(subtitle = "Unavailable", isPlaying = false, isInteractable = false),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

// ----- Unknown -----

@Preview(name = "Unknown_Default")
@Composable
private fun UnknownDefault() = PreviewWrap {
    EntityCardBody(
        state = unknownState(),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}

@Preview(name = "Unknown_Stale")
@Composable
private fun UnknownStale() = PreviewWrap {
    EntityCardBody(
        state = unknownState(subtitle = "Docked", isStale = true),
        onIntent = {},
        size = EntityCardSize.Standard,
    )
}
