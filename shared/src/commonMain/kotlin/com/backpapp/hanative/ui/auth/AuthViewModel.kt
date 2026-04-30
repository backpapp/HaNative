package com.backpapp.hanative.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.data.remote.AuthenticationRepositoryImpl
import com.backpapp.hanative.data.remote.HaUrlRepository
import com.backpapp.hanative.data.remote.ServerManager
import com.backpapp.hanative.data.remote.entities.AuthTokenResponseDto
import com.backpapp.hanative.platform.OAuthCallbackBus
import com.backpapp.hanative.platform.OAuthLauncher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

private const val GENERIC_ERROR = "Couldn't sign in — check the token and try again"
private const val EMPTY_TOKEN_ERROR = "Token can't be empty"
private const val OAUTH_CANCELLED = "OAuth cancelled or failed"
private const val OAUTH_TOKEN_EXCHANGE_FAILED = "Couldn't sign in — try again"
private const val MISSING_URL = "Home Assistant URL missing — restart onboarding"

// Indieauth-style HA OAuth: client_id MUST be a valid URL HA can fetch.
// Page at this URL declares the redirect_uri via <link rel="redirect_uri">.
// Source: docs/index.html in this repo, served via GitHub Pages.
internal const val CLIENT_ID = "https://backpapp.github.io/HaNative/"
internal const val REDIRECT_URI = "hanative://auth-callback"

class AuthViewModel(
    private val authRepository: AuthenticationRepositoryImpl,
    private val urlRepository: HaUrlRepository,
    private val serverManager: ServerManager,
    private val oauthLauncher: OAuthLauncher,
    private val oauthCallbackBus: OAuthCallbackBus,
    private val httpClient: HttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Prevents concurrent token-exchange coroutines when bus delivers multiple emissions.
    private val oauthExchangeMutex = Mutex()

    init {
        viewModelScope.launch {
            oauthCallbackBus.codes.collect { code -> onOAuthCallback(code) }
        }
    }

    /**
     * Long-lived access token path (AC2).
     *
     * Optimistic: we save the token, fire `serverManager.connect()`, and signal Success
     * immediately. Token validity is confirmed asynchronously by the WebSocket handshake.
     * Bad tokens surface as ConnectionError on Dashboard, not here.
     */
    fun submitLongLivedToken(token: String) {
        if (_uiState.value is AuthUiState.Loading) return
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            _uiState.value = AuthUiState.Error(EMPTY_TOKEN_ERROR)
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val url = urlRepository.getUrl()
                if (url.isNullOrBlank()) {
                    _uiState.value = AuthUiState.Error(MISSING_URL)
                    return@launch
                }
                authRepository.saveToken(trimmed)
                serverManager.initialize(lanUrl = url)
                viewModelScope.launch { serverManager.connect() }
                _uiState.value = AuthUiState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.value = AuthUiState.Error(GENERIC_ERROR)
            }
        }
    }

    /**
     * OAuth2 (Indieauth-style) authorize-code path (AC3).
     * Opens system browser to HA `/auth/authorize`. Result returned via deep-link
     * → [OAuthCallbackBus] → [onOAuthCallback].
     */
    fun startOAuthFlow() {
        if (_uiState.value is AuthUiState.Loading) return
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val haUrl = urlRepository.getUrl()
                if (haUrl.isNullOrBlank()) {
                    _uiState.value = AuthUiState.Error(MISSING_URL)
                    return@launch
                }
                val authorizeUrl = URLBuilder("${haUrl.trimEnd('/')}/auth/authorize").apply {
                    parameters.append("client_id", CLIENT_ID)
                    parameters.append("redirect_uri", REDIRECT_URI)
                    parameters.append("response_type", "code")
                }.buildString()
                oauthLauncher.launch(authorizeUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.value = AuthUiState.Error(GENERIC_ERROR)
            }
        }
    }

    private fun onOAuthCallback(code: String?) {
        if (_uiState.value !is AuthUiState.Loading) return
        if (code.isNullOrBlank()) {
            _uiState.value = AuthUiState.Error(OAUTH_CANCELLED)
            oauthCallbackBus.resetReplay()
            return
        }
        viewModelScope.launch {
            if (!oauthExchangeMutex.tryLock()) return@launch
            try {
                val haUrl = urlRepository.getUrl()
                if (haUrl.isNullOrBlank()) {
                    _uiState.value = AuthUiState.Error(MISSING_URL)
                    return@launch
                }
                val response: HttpResponse = httpClient.submitForm(
                    url = "${haUrl.trimEnd('/')}/auth/token",
                    formParameters = Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("client_id", CLIENT_ID)
                        append("redirect_uri", REDIRECT_URI)
                    },
                )
                if (response.status.value !in 200..299) {
                    _uiState.value = AuthUiState.Error(OAUTH_TOKEN_EXCHANGE_FAILED)
                    return@launch
                }
                val body = response.body<AuthTokenResponseDto>()
                authRepository.saveToken(body.accessToken)
                serverManager.initialize(lanUrl = haUrl)
                viewModelScope.launch { serverManager.connect() }
                _uiState.value = AuthUiState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.value = AuthUiState.Error(OAUTH_TOKEN_EXCHANGE_FAILED)
            } finally {
                oauthExchangeMutex.unlock()
                oauthCallbackBus.resetReplay()
            }
        }
    }

    fun onNavigationConsumed() {
        if (_uiState.value is AuthUiState.Success) {
            _uiState.value = AuthUiState.Idle
        }
    }
}
