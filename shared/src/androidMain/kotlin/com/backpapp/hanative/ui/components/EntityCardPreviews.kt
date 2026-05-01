package com.backpapp.hanative.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.platform.HapticEngine
import com.backpapp.hanative.platform.HapticPattern
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.theme.HaNativeTheme
import kotlinx.datetime.Instant

private val previewInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

private object NoopHaptic : HapticEngine {
    override fun fire(pattern: HapticPattern) = Unit
}

private fun lightEntity(state: String, name: String = "Living Room"): HaEntity =
    HaEntity.Light(
        entityId = "light.living_room",
        state = state,
        attributes = mapOf("friendly_name" to name),
        lastChanged = previewInstant,
        lastUpdated = previewInstant,
    )

private fun sensorEntity(state: String = "21.4", unit: String = "°C", name: String = "Living Room Temp"): HaEntity =
    HaEntity.Sensor(
        entityId = "sensor.living_room_temp",
        state = state,
        attributes = mapOf("friendly_name" to name, "unit_of_measurement" to unit),
        lastChanged = previewInstant,
        lastUpdated = previewInstant,
    )

private fun climateEntity(
    state: String = "heat",
    target: Double? = 21.0,
    current: Double? = 20.5,
    name: String = "Lounge Climate",
): HaEntity.Climate = HaEntity.Climate(
    entityId = "climate.lounge",
    state = state,
    attributes = buildMap<String, Any?> {
        put("friendly_name", name)
        if (target != null) put("temperature", target)
        if (current != null) put("current_temperature", current)
    },
    lastChanged = previewInstant,
    lastUpdated = previewInstant,
)

private fun scriptEntity(state: String = "off", name: String = "Goodnight"): HaEntity.Script =
    HaEntity.Script(
        entityId = "script.goodnight",
        state = state,
        attributes = mapOf("friendly_name" to name),
        lastChanged = previewInstant,
        lastUpdated = previewInstant,
    )

private fun mediaEntity(
    state: String = "playing",
    title: String? = "Beethoven Symphony 9",
    name: String = "Living Room Speaker",
): HaEntity.MediaPlayer = HaEntity.MediaPlayer(
    entityId = "media_player.living_room",
    state = state,
    attributes = buildMap<String, Any?> {
        put("friendly_name", name)
        if (title != null) put("media_title", title)
    },
    lastChanged = previewInstant,
    lastUpdated = previewInstant,
)

private fun unknownEntity(state: String = "active"): HaEntity.Unknown =
    HaEntity.Unknown(
        entityId = "vacuum.upstairs",
        state = state,
        // Intentionally include a random attributes shape — Unknown variant must not index it.
        attributes = mapOf("battery_level" to 42, "fan_speed" to "high"),
        lastChanged = previewInstant,
        lastUpdated = previewInstant,
        domain = "vacuum",
    )

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    HaNativeTheme {
        CompositionLocalProvider(LocalHapticEngine provides NoopHaptic) {
            Column { content() }
        }
    }
}

// ----- Toggleable -----

@Preview(name = "Toggleable_Default")
@Composable
private fun ToggleableDefault() = PreviewWrap {
    EntityCardBody(
        entityId = "light.living_room",
        entity = lightEntity(state = "off"),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Toggleable_Active")
@Composable
private fun ToggleableActive() = PreviewWrap {
    EntityCardBody(
        entityId = "light.living_room",
        entity = lightEntity(state = "on"),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Toggleable_Stale")
@Composable
private fun ToggleableStale() = PreviewWrap {
    EntityCardBody(
        entityId = "light.living_room",
        entity = lightEntity(state = "on"),
        size = EntityCardSize.Standard,
        isStale = true,
    )
}

@Preview(name = "Toggleable_Optimistic")
@Composable
private fun ToggleableOptimistic() = PreviewWrap {
    EntityCardBody(
        entityId = "light.living_room",
        entity = lightEntity(state = "off"),
        size = EntityCardSize.Standard,
        isStale = false,
        forcedOptimisticOn = true,
    )
}

@Preview(name = "Toggleable_Error")
@Composable
private fun ToggleableError() = PreviewWrap {
    EntityCardBody(
        entityId = "light.living_room",
        entity = lightEntity(state = "off"),
        size = EntityCardSize.Standard,
        isStale = false,
        forcedRejected = true,
    )
}

// ----- ReadOnly -----

@Preview(name = "ReadOnly_Default")
@Composable
private fun ReadOnlyDefault() = PreviewWrap {
    EntityCardBody(
        entityId = "sensor.living_room_temp",
        entity = sensorEntity(),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "ReadOnly_Active")
@Composable
private fun ReadOnlyActive() = PreviewWrap {
    EntityCardBody(
        entityId = "binary_sensor.front_door",
        entity = HaEntity.BinarySensor(
            entityId = "binary_sensor.front_door",
            state = "on",
            attributes = mapOf("friendly_name" to "Front Door"),
            lastChanged = previewInstant,
            lastUpdated = previewInstant,
        ),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "ReadOnly_Stale")
@Composable
private fun ReadOnlyStale() = PreviewWrap {
    EntityCardBody(
        entityId = "sensor.living_room_temp",
        entity = sensorEntity(),
        size = EntityCardSize.Standard,
        isStale = true,
    )
}

// No-op for read-only variant — renders identically to ReadOnly_Default.
@Preview(name = "ReadOnly_Optimistic")
@Composable
private fun ReadOnlyOptimistic() = PreviewWrap {
    EntityCardBody(
        entityId = "sensor.living_room_temp",
        entity = sensorEntity(),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

// No-op for read-only variant — renders identically to ReadOnly_Default.
@Preview(name = "ReadOnly_Error")
@Composable
private fun ReadOnlyError() = PreviewWrap {
    EntityCardBody(
        entityId = "sensor.living_room_temp",
        entity = sensorEntity(),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

// ----- Stepper -----

@Preview(name = "Stepper_Default")
@Composable
private fun StepperDefault() = PreviewWrap {
    EntityCardBody(
        entityId = "climate.lounge",
        entity = climateEntity(state = "off", target = null, current = null),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Stepper_Active")
@Composable
private fun StepperActive() = PreviewWrap {
    EntityCardBody(
        entityId = "climate.lounge",
        entity = climateEntity(),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Stepper_Stale")
@Composable
private fun StepperStale() = PreviewWrap {
    EntityCardBody(
        entityId = "climate.lounge",
        entity = climateEntity(),
        size = EntityCardSize.Standard,
        isStale = true,
    )
}

@Preview(name = "Stepper_Optimistic")
@Composable
private fun StepperOptimistic() = PreviewWrap {
    EntityCardBody(
        entityId = "climate.lounge",
        entity = climateEntity(target = 21.0),
        size = EntityCardSize.Standard,
        isStale = false,
        forcedOptimisticTemp = 21.5,
    )
}

@Preview(name = "Stepper_Error")
@Composable
private fun StepperError() = PreviewWrap {
    EntityCardBody(
        entityId = "climate.lounge",
        entity = climateEntity(),
        size = EntityCardSize.Standard,
        isStale = false,
        forcedRejected = true,
    )
}

// ----- Trigger -----

@Preview(name = "Trigger_Default")
@Composable
private fun TriggerDefault() = PreviewWrap {
    EntityCardBody(
        entityId = "script.goodnight",
        entity = scriptEntity(),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Trigger_Active")
@Composable
private fun TriggerActive() = PreviewWrap {
    EntityCardBody(
        entityId = "script.goodnight",
        entity = scriptEntity(state = "on"),
        size = EntityCardSize.Standard,
        isStale = false,
        forcedTriggerPulse = true,
    )
}

@Preview(name = "Trigger_Stale")
@Composable
private fun TriggerStale() = PreviewWrap {
    EntityCardBody(
        entityId = "script.goodnight",
        entity = scriptEntity(),
        size = EntityCardSize.Standard,
        isStale = true,
    )
}

// Trigger_Optimistic == Trigger_Active per spec — fire-and-forget, no optimistic state.
@Preview(name = "Trigger_Optimistic")
@Composable
private fun TriggerOptimistic() = PreviewWrap {
    EntityCardBody(
        entityId = "script.goodnight",
        entity = scriptEntity(state = "on"),
        size = EntityCardSize.Standard,
        isStale = false,
        forcedTriggerPulse = true,
    )
}

@Preview(name = "Trigger_Error")
@Composable
private fun TriggerError() = PreviewWrap {
    EntityCardBody(
        entityId = "script.goodnight",
        entity = scriptEntity(state = "unavailable"),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

// ----- Media -----

@Preview(name = "Media_Playing")
@Composable
private fun MediaPlaying() = PreviewWrap {
    EntityCardBody(
        entityId = "media_player.living_room",
        entity = mediaEntity(state = "playing"),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Media_Paused")
@Composable
private fun MediaPaused() = PreviewWrap {
    EntityCardBody(
        entityId = "media_player.living_room",
        entity = mediaEntity(state = "paused"),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Media_Idle")
@Composable
private fun MediaIdle() = PreviewWrap {
    EntityCardBody(
        entityId = "media_player.living_room",
        entity = mediaEntity(state = "idle", title = null),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Media_Stale")
@Composable
private fun MediaStale() = PreviewWrap {
    EntityCardBody(
        entityId = "media_player.living_room",
        entity = mediaEntity(state = "playing"),
        size = EntityCardSize.Standard,
        isStale = true,
    )
}

@Preview(name = "Media_Error")
@Composable
private fun MediaError() = PreviewWrap {
    EntityCardBody(
        entityId = "media_player.living_room",
        entity = mediaEntity(state = "unavailable"),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

// ----- Unknown -----

@Preview(name = "Unknown_Default")
@Composable
private fun UnknownDefault() = PreviewWrap {
    EntityCardBody(
        entityId = "vacuum.upstairs",
        entity = unknownEntity(),
        size = EntityCardSize.Standard,
        isStale = false,
    )
}

@Preview(name = "Unknown_Stale")
@Composable
private fun UnknownStale() = PreviewWrap {
    EntityCardBody(
        entityId = "vacuum.upstairs",
        entity = unknownEntity(state = "docked"),
        size = EntityCardSize.Standard,
        isStale = true,
    )
}
