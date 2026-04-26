package com.backpapp.hanative.data.remote

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapAnySerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trips string value`() {
        val map = mapOf("key" to "value")
        val encoded = json.encodeToString(MapAnySerializer, map)
        val decoded = json.decodeFromString(MapAnySerializer, encoded)
        assertEquals("value", decoded["key"])
    }

    @Test
    fun `round-trips integer value`() {
        val map: Map<String, Any?> = mapOf("count" to 42L)
        val encoded = json.encodeToString(MapAnySerializer, map)
        val decoded = json.decodeFromString(MapAnySerializer, encoded)
        assertEquals(42L, (decoded["count"] as Number).toLong())
    }

    @Test
    fun `round-trips double value`() {
        val map: Map<String, Any?> = mapOf("temp" to 21.5)
        val encoded = json.encodeToString(MapAnySerializer, map)
        val decoded = json.decodeFromString(MapAnySerializer, encoded)
        assertEquals(21.5, (decoded["temp"] as Number).toDouble(), 0.001)
    }

    @Test
    fun `round-trips boolean value`() {
        val map: Map<String, Any?> = mapOf("active" to true)
        val encoded = json.encodeToString(MapAnySerializer, map)
        val decoded = json.decodeFromString(MapAnySerializer, encoded)
        assertEquals(true, decoded["active"])
    }

    @Test
    fun `round-trips null value`() {
        val map: Map<String, Any?> = mapOf("nothing" to null)
        val encoded = json.encodeToString(MapAnySerializer, map)
        val decoded = json.decodeFromString(MapAnySerializer, encoded)
        assertNull(decoded["nothing"])
    }

    @Test
    fun `round-trips list value`() {
        val map: Map<String, Any?> = mapOf("rgb" to listOf(255, 128, 0))
        val encoded = json.encodeToString(MapAnySerializer, map)
        val decoded = json.decodeFromString(MapAnySerializer, encoded)
        assertIs<List<*>>(decoded["rgb"])
    }

    @Test
    fun `round-trips nested map`() {
        val map: Map<String, Any?> = mapOf("nested" to mapOf("inner" to "value"))
        val encoded = json.encodeToString(MapAnySerializer, map)
        val decoded = json.decodeFromString(MapAnySerializer, encoded)
        assertIs<Map<*, *>>(decoded["nested"])
    }

    @Test
    fun `deserializes raw json string`() {
        val rawJson = """{"brightness":200,"color_temp":300,"friendly_name":"Living Room Light"}"""
        val decoded = json.decodeFromString(MapAnySerializer, rawJson)
        assertEquals("Living Room Light", decoded["friendly_name"])
        assertEquals(200L, (decoded["brightness"] as Number).toLong())
    }

    @Test
    fun `handles empty map`() {
        val map: Map<String, Any?> = emptyMap()
        val encoded = json.encodeToString(MapAnySerializer, map)
        val decoded = json.decodeFromString(MapAnySerializer, encoded)
        assertTrue(decoded.isEmpty())
    }
}
