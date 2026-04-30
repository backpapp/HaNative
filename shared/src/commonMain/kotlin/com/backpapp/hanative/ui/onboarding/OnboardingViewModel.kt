package com.backpapp.hanative.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.data.remote.HaUrlRepository
import com.backpapp.hanative.domain.model.HaServerInfo
import com.backpapp.hanative.platform.ServerDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OnboardingUiState {
    data object Idle : OnboardingUiState()
    data object Loading : OnboardingUiState()
    data class Success(val url: String) : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
}

private const val GENERIC_ERROR = "Can't reach this address — check the URL and try again"

class OnboardingViewModel(
    private val urlRepository: HaUrlRepository,
    private val serverDiscovery: ServerDiscovery,
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _discoveredServers = MutableStateFlow<List<HaServerInfo>>(emptyList())
    val discoveredServers: StateFlow<List<HaServerInfo>> = _discoveredServers.asStateFlow()

    init {
        viewModelScope.launch {
            serverDiscovery.startDiscovery().collect { servers ->
                _discoveredServers.value = servers
            }
        }
    }

    fun testUrl(rawInput: String) {
        if (_uiState.value is OnboardingUiState.Loading) return
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        _uiState.value = OnboardingUiState.Loading
        viewModelScope.launch {
            val candidates = candidateUrls(trimmed)
            val successUrl = candidates.firstOrNull { url ->
                urlRepository.testUrl(url).isSuccess
            }
            if (successUrl == null) {
                _uiState.value = OnboardingUiState.Error(GENERIC_ERROR)
                return@launch
            }
            urlRepository.saveUrl(successUrl).fold(
                onSuccess = { _uiState.value = OnboardingUiState.Success(successUrl) },
                onFailure = { _uiState.value = OnboardingUiState.Error(GENERIC_ERROR) },
            )
        }
    }

    fun onNavigationConsumed() {
        if (_uiState.value is OnboardingUiState.Success) {
            _uiState.value = OnboardingUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        serverDiscovery.stopDiscovery()
    }
}

private fun candidateUrls(input: String): List<String> {
    val lower = input.lowercase()
    return when {
        lower.startsWith("http://") || lower.startsWith("https://") -> listOf(input)
        else -> listOf("https://$input", "http://$input")
    }
}
