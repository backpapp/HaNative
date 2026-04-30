package com.backpapp.hanative.ui.components

import com.backpapp.hanative.domain.model.HaEntity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// TODO(Story 4.7-or-later): add Compose UI tests for touch-down haptic + optimistic + reject flow
//   once compose.uiTest runner is wired into this project.

private val instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

private fun light(state: String, attrs: Map<String, Any?> = emptyMap()): HaEntity =
    HaEntity.Light("light.living_room", state, attrs, instant, instant)

private fun sensor(state: String, attrs: Map<String, Any?> = emptyMap()): HaEntity =
    HaEntity.Sensor("sensor.outside_temp", state, attrs, instant, instant)

class FriendlyNameTest {
    @Test
    fun usesFriendlyNameAttributeWhenPresent() {
        val e = light("on", mapOf("friendly_name" to "Living Room"))
        assertEquals("Living Room", friendlyName(e, "light.living_room"))
    }

    @Test
    fun fallsBackToHumanisedEntityIdWhenAttributeMissing() {
        val e = light("on")
        assertEquals("Living Room", friendlyName(e, "light.living_room"))
    }

    @Test
    fun fallsBackUsingFallbackIdWhenEntityNull() {
        assertEquals("Bedroom Lamp", friendlyName(null, "light.bedroom_lamp"))
    }

    @Test
    fun handlesEntityIdWithoutDomainPrefix() {
        assertEquals("Solo", friendlyName(null, "solo"))
    }
}

class StateLabelTest {
    @Test
    fun titleCasesSimpleState() {
        assertEquals("On", stateLabel("on"))
        assertEquals("Off", stateLabel("off"))
    }

    @Test
    fun replacesUnderscoresAndTitleCases() {
        assertEquals("Unavailable", stateLabel("unavailable"))
        assertEquals("Not home", stateLabel("not_home"))
    }

    @Test
    fun emptyStateRendersUnknown() {
        assertEquals("Unknown", stateLabel(""))
    }
}

class DomainIconTest {
    @Test
    fun resolvesIconForEachSupportedSubtype() {
        assertNotNull(domainIcon(light("on")))
        assertNotNull(domainIcon(HaEntity.Switch("switch.x", "on", emptyMap(), instant, instant)))
        assertNotNull(domainIcon(HaEntity.InputBoolean("input_boolean.x", "on", emptyMap(), instant, instant)))
        assertNotNull(domainIcon(sensor("21.4")))
        assertNotNull(domainIcon(HaEntity.BinarySensor("binary_sensor.x", "on", emptyMap(), instant, instant)))
    }

    @Test
    fun resolvesFallbackIconForNullEntity() {
        assertNotNull(domainIcon(null))
    }
}
