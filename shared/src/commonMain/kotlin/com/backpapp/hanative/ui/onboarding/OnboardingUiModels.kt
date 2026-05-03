package com.backpapp.hanative.ui.onboarding

// UI-layer models for Onboarding. Composables consume these only — never
// `HaServerInfo` or any other `domain/model/` type. ViewModel maps domain → UI.

data class OnboardingUiState(
    val phase: Phase = Phase.Idle,
    val errorMessage: String? = null,
    val pendingNavigationUrl: String? = null,
    val discoveredServers: List<DiscoveredServerUi> = emptyList(),
) {
    sealed class Phase {
        data object Idle : Phase()
        data object Loading : Phase()
        data object Error : Phase()
        data class Success(val url: String) : Phase()
    }

    val isLoading: Boolean get() = phase is Phase.Loading
}

data class DiscoveredServerUi(
    val name: String,
    val hostPortLabel: String,
    val urlInput: String,
)

sealed class OnboardingIntent {
    data class TestUrl(val rawInput: String) : OnboardingIntent()
    data object NavigationConsumed : OnboardingIntent()
}
