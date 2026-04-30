package com.backpapp.hanative.data.local

import com.backpapp.hanative.data.local.adapter.InstantColumnAdapter
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class InstantColumnAdapterTest {

    @Test
    fun `encode then decode round-trips Instant`() {
        val instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val encoded = InstantColumnAdapter.encode(instant)
        val decoded = InstantColumnAdapter.decode(encoded)
        assertEquals(instant, decoded)
    }

    @Test
    fun `encode returns epoch millis`() {
        val millis = 1_700_000_000_000L
        val instant = Instant.fromEpochMilliseconds(millis)
        assertEquals(millis, InstantColumnAdapter.encode(instant))
    }

    @Test
    fun `decode returns correct Instant from epoch millis`() {
        val millis = 1_000_000_000_000L
        val decoded = InstantColumnAdapter.decode(millis)
        assertEquals(Instant.fromEpochMilliseconds(millis), decoded)
    }

    @Test
    fun `encode decode zero epoch`() {
        val instant = Instant.fromEpochMilliseconds(0L)
        assertEquals(instant, InstantColumnAdapter.decode(InstantColumnAdapter.encode(instant)))
    }
}
