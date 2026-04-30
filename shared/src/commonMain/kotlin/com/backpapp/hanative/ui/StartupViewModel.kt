package com.backpapp.hanative.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.data.remote.HaUrlRepository
import com.backpapp.hanative.data.remote.ServerManager
import com.backpapp.hanative.data.remote.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class StartupRoute {
    data object Loading : StartupRoute()
    data object Onboarding : StartupRoute()
    data object Dashboard : StartupRoute()
}

/**
 * Decides initial nav root on app launch (FR3).
 * Stored token + URL → Dashboard, else → Onboarding.
 */
class StartupViewModel(
    private val sessionRepository: SessionRepository,
    private val serverManager: ServerManager,
    private val urlRepository: HaUrlRepository,
) : ViewModel() {

    private val _route = MutableStateFlow<StartupRoute>(StartupRoute.Loading)
    val route: StateFlow<StartupRoute> = _route.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                if (sessionRepository.hasValidSession()) {
                    val url = urlRepository.getUrl()
                        ?: error("hasValidSession returned true but URL null")
                    serverManager.initialize(lanUrl = url)
                    viewModelScope.launch { serverManager.connect() }
                    _route.value = StartupRoute.Dashboard
                } else {
                    _route.value = StartupRoute.Onboarding
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _route.value = StartupRoute.Onboarding
            }
        }
    }
}
