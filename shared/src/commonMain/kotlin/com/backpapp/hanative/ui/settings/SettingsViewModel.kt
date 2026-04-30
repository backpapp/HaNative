package com.backpapp.hanative.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.data.remote.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _disconnected = Channel<Unit>(Channel.BUFFERED)
    val disconnected: Flow<Unit> = _disconnected.receiveAsFlow()

    fun disconnect() {
        viewModelScope.launch {
            try {
                sessionRepository.logout()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Logout is best-effort. Still navigate user back to onboarding.
            }
            _disconnected.send(Unit)
        }
    }
}
