package com.backpapp.hanative.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class HumanizeDomainTest {
    @Test
    fun titleCasesSingleWord() {
        assertEquals("Light", humanizeDomain("light"))
        assertEquals("Switch", humanizeDomain("switch"))
    }

    @Test
    fun splitsAndTitleCasesUnderscoredDomain() {
        assertEquals("Media Player", humanizeDomain("media_player"))
        assertEquals("Input Boolean", humanizeDomain("input_boolean"))
        assertEquals("Binary Sensor", humanizeDomain("binary_sensor"))
        assertEquals("Input Select", humanizeDomain("input_select"))
    }

    @Test
    fun emptyStringReturnsEmpty() {
        assertEquals("", humanizeDomain(""))
    }

    @Test
    fun ignoresEmptySegmentsFromConsecutiveUnderscores() {
        assertEquals("A B", humanizeDomain("a__b"))
    }
}

class PickerDomainsListTest {
    @Test
    fun containsAllElevenSupportedDomains() {
        assertEquals(11, PICKER_DOMAINS.size)
        assertEquals(
            listOf(
                "light", "switch", "input_boolean", "climate", "cover",
                "media_player", "script", "scene", "sensor", "binary_sensor", "input_select",
            ),
            PICKER_DOMAINS,
        )
    }
}
