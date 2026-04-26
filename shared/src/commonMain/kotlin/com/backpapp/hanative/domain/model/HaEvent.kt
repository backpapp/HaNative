package com.backpapp.hanative.domain.model

import kotlinx.datetime.Instant

sealed class HaEvent {
    data class StateChanged(
        val entityId: String,
        val state: String,
        val attributes: Map<String, Any?>,
        val lastChanged: Instant,
        val lastUpdated: Instant,
    ) : HaEvent()

    object ConnectionLost : HaEvent()

    data class ConnectionError(val cause: Throwable) : HaEvent()
}
