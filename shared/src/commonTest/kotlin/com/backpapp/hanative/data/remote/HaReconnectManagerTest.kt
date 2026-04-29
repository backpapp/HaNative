package com.backpapp.hanative.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class HaReconnectManagerTest {
    private val manager = HaReconnectManager()

    @Test
    fun `backoff sequence matches spec`() {
        val delays = (1..6).map {
            val d = manager.nextDelayMs()
            manager.advanceBackoff()
            d
        }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L), delays)
    }

    @Test
    fun `caps at 30000ms`() {
        repeat(10) { manager.advanceBackoff() }
        assertEquals(30_000L, manager.nextDelayMs())
    }

    @Test
    fun `reset restores 1000ms`() {
        manager.advanceBackoff()
        manager.advanceBackoff()
        manager.reset()
        assertEquals(1_000L, manager.nextDelayMs())
    }
}
