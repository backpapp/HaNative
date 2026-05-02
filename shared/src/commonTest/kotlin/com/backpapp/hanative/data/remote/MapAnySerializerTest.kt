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

    // Story 4.8 AC14 — survive unexpected wire shapes (mixed primitives + nested
    // arrays + unknown keys) without throwing. Non-standard types fall through to
    // toString() per MapAnySerializer's known-limitation contract.
    @Test
    fun mapAnySerializerSurvivesUnexpectedJsonShapes() {
        val rawJson = """
            {
              "primitive_string": "hello",
              "primitive_int": 7,
              "primitive_bool": false,
              "primitive_null": null,
              "nested_array": [1, "two", true, null, [9, 10]],
              "deeply_nested": {"a": {"b": {"c": [1, 2]}}},
              "unknown_marker": "preserved",
              "mixed": [1, "x", {"k": "v"}]
            }
        """.trimIndent()
        val decoded = json.decodeFromString(MapAnySerializer, rawJson)
        assertEquals("hello", decoded["primitive_string"])
        assertEquals(false, decoded["primitive_bool"])
        assertNull(decoded["primitive_null"])
        assertIs<List<*>>(decoded["nested_array"])
        assertIs<Map<*, *>>(decoded["deeply_nested"])
        assertEquals("preserved", decoded["unknown_marker"])
        assertIs<List<*>>(decoded["mixed"])
    }
}
