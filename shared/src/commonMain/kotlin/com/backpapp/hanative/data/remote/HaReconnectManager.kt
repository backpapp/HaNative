package com.backpapp.hanative.data.remote

import kotlin.concurrent.Volatile
import kotlinx.coroutines.delay

class HaReconnectManager {
    @Volatile private var backoffMs = 1_000L

    fun nextDelayMs(): Long = backoffMs

    fun advanceBackoff() {
        backoffMs = minOf(backoffMs * 2, 30_000L)
    }

    fun reset() {
        backoffMs = 1_000L
    }

    suspend fun waitThenAttempt(attempt: suspend () -> Unit) {
        delay(nextDelayMs())
        advanceBackoff()
        attempt()
    }
}
