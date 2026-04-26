package com.backpapp.hanative.domain.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HaEntityTest {

    private val ts = Instant.fromEpochSeconds(0)

    private fun entity(entityId: String, state: String = "on", attributes: Map<String, Any?> = emptyMap()) =
        HaEntity(entityId, state, attributes, ts, ts)

    @Test
    fun `factory creates Light for light domain`() {
        assertIs<HaEntity.Light>(entity("light.living_room"))
    }

    @Test
    fun `factory creates Switch for switch domain`() {
        assertIs<HaEntity.Switch>(entity("switch.outlet"))
    }

    @Test
    fun `factory creates Climate for climate domain`() {
        assertIs<HaEntity.Climate>(entity("climate.thermostat"))
    }

    @Test
    fun `factory creates Cover for cover domain`() {
        assertIs<HaEntity.Cover>(entity("cover.garage"))
    }

    @Test
    fun `factory creates MediaPlayer for media_player domain`() {
        assertIs<HaEntity.MediaPlayer>(entity("media_player.tv"))
    }

    @Test
    fun `factory creates Sensor for sensor domain`() {
        assertIs<HaEntity.Sensor>(entity("sensor.temperature"))
    }

    @Test
    fun `factory creates BinarySensor for binary_sensor domain`() {
        assertIs<HaEntity.BinarySensor>(entity("binary_sensor.motion"))
    }

    @Test
    fun `factory creates InputBoolean for input_boolean domain`() {
        assertIs<HaEntity.InputBoolean>(entity("input_boolean.guest_mode"))
    }

    @Test
    fun `factory creates InputSelect for input_select domain`() {
        assertIs<HaEntity.InputSelect>(entity("input_select.scene"))
    }

    @Test
    fun `factory creates Script for script domain`() {
        assertIs<HaEntity.Script>(entity("script.morning_routine"))
    }

    @Test
    fun `factory creates Scene for scene domain`() {
        assertIs<HaEntity.Scene>(entity("scene.movie_time"))
    }

    @Test
    fun `factory creates Unknown for unrecognized domain`() {
        val result = entity("custom_domain.sensor")
        assertIs<HaEntity.Unknown>(result)
        assertEquals("custom_domain", result.domain)
    }

    @Test
    fun `Light isOn true when state on`() {
        val light = entity("light.room", state = "on") as HaEntity.Light
        assertTrue(light.isOn)
    }

    @Test
    fun `Light isOn false when state off`() {
        val light = entity("light.room", state = "off") as HaEntity.Light
        assertFalse(light.isOn)
    }

    @Test
    fun `Light brightness from attributes`() {
        val light = entity("light.room", attributes = mapOf("brightness" to 200)) as HaEntity.Light
        assertEquals(200, light.brightness)
    }

    @Test
    fun `Light brightness null when absent`() {
        val light = entity("light.room") as HaEntity.Light
        assertNull(light.brightness)
    }

    @Test
    fun `Sensor unit from attributes`() {
        val sensor = entity("sensor.temp", attributes = mapOf("unit_of_measurement" to "°C")) as HaEntity.Sensor
        assertEquals("°C", sensor.unit)
    }

    @Test
    fun `Climate currentTemperature from attributes`() {
        val climate = entity("climate.hvac", attributes = mapOf("current_temperature" to 21.5)) as HaEntity.Climate
        assertEquals(21.5, climate.currentTemperature)
    }

    @Test
    fun `MediaPlayer mediaTitle from attributes`() {
        val player = entity("media_player.tv", attributes = mapOf("media_title" to "Breaking Bad")) as HaEntity.MediaPlayer
        assertEquals("Breaking Bad", player.mediaTitle)
    }

    @Test
    fun `entity preserves entityId and state`() {
        val e = entity("light.room", state = "on")
        assertEquals("light.room", e.entityId)
        assertEquals("on", e.state)
    }

    @Test
    fun `Light colorTemp from attributes`() {
        val light = entity("light.room", attributes = mapOf("color_temp" to 370)) as HaEntity.Light
        assertEquals(370, light.colorTemp)
    }

    @Test
    fun `Light rgbColor from attributes`() {
        val light = entity("light.room", attributes = mapOf("rgb_color" to listOf(255, 128, 0))) as HaEntity.Light
        assertEquals(listOf(255, 128, 0), light.rgbColor)
    }

    @Test
    fun `Cover isOpen true when state open`() {
        val cover = entity("cover.garage", state = "open") as HaEntity.Cover
        assertTrue(cover.isOpen)
    }

    @Test
    fun `Cover currentPosition from attributes`() {
        val cover = entity("cover.garage", attributes = mapOf("current_position" to 75)) as HaEntity.Cover
        assertEquals(75, cover.currentPosition)
    }

    @Test
    fun `BinarySensor deviceClass from attributes`() {
        val sensor = entity("binary_sensor.door", attributes = mapOf("device_class" to "door")) as HaEntity.BinarySensor
        assertEquals("door", sensor.deviceClass)
    }

    @Test
    fun `Script isRunning true when state on`() {
        val script = entity("script.morning_routine", state = "on") as HaEntity.Script
        assertTrue(script.isRunning)
    }

    @Test
    fun `Climate targetTemperature from attributes`() {
        val climate = entity("climate.hvac", attributes = mapOf("temperature" to 22.0)) as HaEntity.Climate
        assertEquals(22.0, climate.targetTemperature)
    }

    @Test
    fun `Climate hvacMode equals state`() {
        val climate = entity("climate.hvac", state = "heat") as HaEntity.Climate
        assertEquals("heat", climate.hvacMode)
    }

    @Test
    fun `MediaPlayer volumeLevel from attributes`() {
        val player = entity("media_player.tv", attributes = mapOf("volume_level" to 0.5)) as HaEntity.MediaPlayer
        assertEquals(0.5, player.volumeLevel)
    }

    @Test
    fun `MediaPlayer mediaArtist from attributes`() {
        val player = entity("media_player.tv", attributes = mapOf("media_artist" to "Pink Floyd")) as HaEntity.MediaPlayer
        assertEquals("Pink Floyd", player.mediaArtist)
    }

    @Test
    fun `InputSelect options from attributes`() {
        val select = entity("input_select.mode", attributes = mapOf("options" to listOf("a", "b", "c"))) as HaEntity.InputSelect
        assertEquals(listOf("a", "b", "c"), select.options)
    }
}
