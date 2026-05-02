package com.backpapp.hanative.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.data.remote.ServerManager
import com.backpapp.hanative.data.remote.SessionRepository
import com.backpapp.hanative.domain.usecase.ObserveConnectionStateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val authInvalid: Boolean = false,
)

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    observeConnectionState: ObserveConnectionStateUseCase,
) : ViewModel() {

    private val _disconnected = Channel<Unit>(Channel.BUFFERED)
    val disconnected: Flow<Unit> = _disconnected.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = observeConnectionState()
        .map { SettingsUiState(authInvalid = it == ServerManager.ConnectionState.InvalidAuth) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
            initialValue = SettingsUiState(),
        )

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
