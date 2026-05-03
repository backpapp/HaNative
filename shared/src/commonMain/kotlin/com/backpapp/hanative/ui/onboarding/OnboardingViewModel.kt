package com.backpapp.hanative.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.data.remote.HaUrlRepository
import com.backpapp.hanative.domain.model.HaServerInfo
import com.backpapp.hanative.platform.ServerDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val GENERIC_ERROR = "Can't reach this address — check the URL and try again"

class OnboardingViewModel(
    private val urlRepository: HaUrlRepository,
    private val serverDiscovery: ServerDiscovery,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serverDiscovery.startDiscovery().collect { servers ->
                _uiState.update { it.copy(discoveredServers = servers.map(::toUi)) }
            }
        }
    }

    fun testUrl(rawInput: String) {
        if (_uiState.value.phase is OnboardingUiState.Phase.Loading) return
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        _uiState.update {
            it.copy(phase = OnboardingUiState.Phase.Loading, errorMessage = null)
        }
        viewModelScope.launch {
            val candidates = candidateUrls(trimmed)
            val successUrl = candidates.firstOrNull { url ->
                urlRepository.testUrl(url).isSuccess
            }
            if (successUrl == null) {
                _uiState.update {
                    it.copy(phase = OnboardingUiState.Phase.Error, errorMessage = GENERIC_ERROR)
                }
                return@launch
            }
            urlRepository.saveUrl(successUrl).fold(
                onSuccess = {
                    _uiState.update { current ->
                        current.copy(
                            phase = OnboardingUiState.Phase.Success(successUrl),
                            errorMessage = null,
                            pendingNavigationUrl = successUrl,
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            phase = OnboardingUiState.Phase.Error,
                            errorMessage = GENERIC_ERROR,
                        )
                    }
                },
            )
        }
    }

    fun onNavigationConsumed() {
        if (_uiState.value.phase is OnboardingUiState.Phase.Success) {
            _uiState.update {
                it.copy(phase = OnboardingUiState.Phase.Idle, pendingNavigationUrl = null)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        serverDiscovery.stopDiscovery()
    }
}

private fun toUi(server: HaServerInfo): DiscoveredServerUi {
    val bracketedHost = if (server.host.contains(':')) "[${server.host}]" else server.host
    return DiscoveredServerUi(
        name = server.name,
        hostPortLabel = "${server.host}:${server.port}",
        urlInput = "$bracketedHost:${server.port}",
    )
}

private fun candidateUrls(input: String): List<String> {
    val lower = input.lowercase()
    return when {
        lower.startsWith("http://") || lower.startsWith("https://") -> listOf(input)
        else -> listOf("https://$input", "http://$input")
    }
}
