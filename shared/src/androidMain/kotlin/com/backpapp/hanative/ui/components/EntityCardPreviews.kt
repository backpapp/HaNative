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

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    HaNativeTheme {
        CompositionLocalProvider(LocalHapticEngine provides NoopHaptic) {
            Column { content() }
        }
    }
}

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
