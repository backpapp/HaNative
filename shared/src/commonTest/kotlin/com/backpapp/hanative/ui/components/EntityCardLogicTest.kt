package com.backpapp.hanative.ui.components

import com.backpapp.hanative.domain.model.HaEntity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// TODO(Story 4.7-or-later): add Compose UI tests for touch-down haptic + optimistic + reject flow
//   plus stepper +/- composition behaviour, once compose.uiTest runner is wired into this project.

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

    @Test
    fun mediaStatesTitleCase() {
        assertEquals("Playing", stateLabel("playing"))
        assertEquals("Paused", stateLabel("paused"))
        assertEquals("Idle", stateLabel("idle"))
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
    fun resolvesIconForStory44Subtypes() {
        assertNotNull(domainIcon(HaEntity.Climate("climate.x", "heat", emptyMap(), instant, instant)))
        assertNotNull(domainIcon(HaEntity.Script("script.x", "off", emptyMap(), instant, instant)))
        assertNotNull(domainIcon(HaEntity.Scene("scene.x", "scening", emptyMap(), instant, instant)))
        assertNotNull(domainIcon(HaEntity.MediaPlayer("media_player.x", "playing", emptyMap(), instant, instant)))
        assertNotNull(domainIcon(HaEntity.Unknown("vacuum.x", "active", emptyMap(), instant, instant, "vacuum")))
    }

    @Test
    fun resolvesFallbackIconForNullEntity() {
        assertNotNull(domainIcon(null))
    }
}

class AppendStaleSuffixTest {
    @Test
    fun returnsBaseUnchangedWhenNotStale() {
        assertEquals("On", appendStaleSuffix("On", isStale = false, lastChanged = instant))
    }

    @Test
    fun returnsBaseUnchangedWhenLastChangedNull() {
        assertEquals("On", appendStaleSuffix("On", isStale = true, lastChanged = null))
    }

    @Test
    fun appendsJustNowForRecentDelta() {
        val out = appendStaleSuffix("On", isStale = true, lastChanged = Clock.System.now())
        assertEquals("On, updated just now", out)
    }

    @Test
    fun appendsMinutesForOlderDelta() {
        val older = Instant.fromEpochMilliseconds(
            Clock.System.now().toEpochMilliseconds() - 3 * 60_000L - 1000L,
        )
        val out = appendStaleSuffix("On", isStale = true, lastChanged = older)
        assertTrue(out.startsWith("On, updated "), "expected minutes suffix, got: $out")
        assertTrue(out.endsWith("m ago"), "expected 'm ago' suffix, got: $out")
    }
}

class FormatTempTest {
    @Test
    fun formatsHalfDegreeIncrements() {
        assertEquals("21.0°", formatTemp(21.0))
        assertEquals("21.5°", formatTemp(21.5))
        assertEquals("22.0°", formatTemp(22.0))
    }

    @Test
    fun roundsAribitraryDoubleToOneDecimal() {
        assertEquals("21.4°", formatTemp(21.43))
        assertEquals("21.5°", formatTemp(21.46))
    }

    @Test
    fun handlesNegativeTemperatures() {
        assertEquals("-5.0°", formatTemp(-5.0))
        assertEquals("-0.5°", formatTemp(-0.5))
    }
}
