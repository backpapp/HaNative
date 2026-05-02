package com.backpapp.hanative.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals

class StaleStateIndicatorTest {

    @Test
    fun formatStaleAgoSecondsBoundary() {
        assertEquals("0s", formatStaleAgo(0L))
        assertEquals("1s", formatStaleAgo(1L))
        assertEquals("59s", formatStaleAgo(59L))
    }

    @Test
    fun formatStaleAgoMinutesBoundary() {
        assertEquals("1m", formatStaleAgo(60L))
        assertEquals("1m", formatStaleAgo(119L))
        assertEquals("2m", formatStaleAgo(120L))
        assertEquals("59m", formatStaleAgo(3599L))
    }

    @Test
    fun formatStaleAgoHoursBoundary() {
        assertEquals("1h", formatStaleAgo(3600L))
        assertEquals("23h", formatStaleAgo(86399L))
    }

    @Test
    fun formatStaleAgoDaysBoundary() {
        assertEquals("1d", formatStaleAgo(86400L))
        assertEquals("7d", formatStaleAgo(7L * 86400L))
        assertEquals("365d", formatStaleAgo(365L * 86400L))
    }
}
