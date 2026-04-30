package com.backpapp.hanative.data.remote

import com.backpapp.hanative.domain.repository.HaWebSocketClient
import kotlinx.coroutines.CancellationException

/**
 * Story 3.5 — startup-route decision + logout teardown.
 * Token + URL are validated as a pair: a valid session needs both.
 */
class SessionRepository(
    private val authRepository: AuthenticationRepositoryImpl,
    private val urlRepository: HaUrlRepository,
    private val serverManager: ServerManager,
    private val webSocketClient: HaWebSocketClient,
) {
    suspend fun hasValidSession(): Boolean {
        val token = authRepository.getToken()
        val url = urlRepository.getUrl()
        return !token.isNullOrBlank() && !url.isNullOrBlank()
    }

    /**
     * Best-effort logout: every step runs even if a previous one fails so we
     * never leave partial credential state behind.
     */
    suspend fun logout() {
        var firstError: Throwable? = null

        suspend fun runSafely(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (firstError == null) firstError = e
            }
        }

        runSafely { webSocketClient.disconnect() }
        runSafely { serverManager.disconnect() }
        runSafely { authRepository.clearToken() }
        runSafely { urlRepository.clearUrl() }

        firstError?.let { throw it }
    }
}
