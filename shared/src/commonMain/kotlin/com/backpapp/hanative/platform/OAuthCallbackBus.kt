package com.backpapp.hanative.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Story 3.5 — bridges OAuth deep-link callbacks from platform code into AuthViewModel.
 *
 * Replay = 1 lets a late subscriber (ViewModel constructed AFTER MainActivity.onNewIntent)
 * still receive the buffered code. Process-death scenario: see story Dev Notes.
 */
class OAuthCallbackBus {
    private val _codes = MutableSharedFlow<String?>(replay = 1, extraBufferCapacity = 1)
    val codes: SharedFlow<String?> = _codes.asSharedFlow()

    fun emit(code: String?) {
        _codes.tryEmit(code)
    }

    fun resetReplay() {
        _codes.resetReplayCache()
    }
}
