package com.backpapp.hanative.data.remote

import com.backpapp.hanative.platform.CredentialStore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

// Refresh access tokens this many ms before their reported expiry, so a request that
// races the boundary still arrives on a valid token. HA access tokens are 30 min; 60s
// of slack is plenty without burning refreshes unnecessarily.
private const val REFRESH_BUFFER_MS: Long = 60_000L

@OptIn(ExperimentalTime::class)
class AuthenticationRepositoryImpl(
    private val credentialStore: CredentialStore,
    private val refreshClient: OAuthRefreshClient? = null,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns a usable access token, refreshing first if the stored OAuth token is
     * within [REFRESH_BUFFER_MS] of expiry. Long-lived tokens (no refresh_token, no
     * expires_at) are returned as-is — they don't expire on the server side.
     *
     * Throws if no token is stored or refresh fails. Callers (ServerManager.attemptConnect)
     * wrap this in runCatching so a transient refresh failure surfaces as a normal connect
     * failure and falls into the standard reconnect-with-backoff loop.
     */
    suspend fun getValidToken(): String = mutex.withLock {
        val stored = readCredential()
            ?: throw IllegalStateException("No access token stored. Complete onboarding first.")
        if (!shouldRefresh(stored)) return stored.accessToken
        val refreshClient = refreshClient
            ?: return stored.accessToken // Long-lived path with no refresh client wired.
        val refreshToken = stored.refreshToken
            ?: return stored.accessToken
        val response = refreshClient.refresh(refreshToken)
        val refreshed = StoredCredential(
            accessToken = response.accessToken,
            // HA does not rotate refresh tokens on /auth/token grant_type=refresh_token,
            // so reuse the existing one — preserves longevity across many refresh cycles.
            refreshToken = refreshToken,
            expiresAtEpochMs = response.expiresIn?.let {
                Clock.System.now().toEpochMilliseconds() + it * 1000L
            },
        )
        writeCredential(refreshed)
        return refreshed.accessToken
    }

    suspend fun getToken(): String? = mutex.withLock { readCredential()?.accessToken }

    /**
     * Persist a long-lived access token (manual entry path). No refresh, no expiry —
     * HA-side rotation requires the user to re-add the token.
     */
    suspend fun saveToken(token: String) {
        mutex.withLock {
            writeCredential(StoredCredential(accessToken = token))
        }
    }

    /**
     * Persist an OAuth token triple. `expiresInSeconds` is converted to absolute
     * `expiresAtEpochMs` here so subsequent reads can compare against wall-clock time
     * directly without re-deriving from a moving target.
     */
    suspend fun saveOauthTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long?) {
        mutex.withLock {
            val expiresAt = expiresInSeconds?.let {
                Clock.System.now().toEpochMilliseconds() + it * 1000L
            }
            writeCredential(
                StoredCredential(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtEpochMs = expiresAt,
                ),
            )
        }
    }

    suspend fun clearToken() {
        mutex.withLock { credentialStore.clear() }
    }

    private suspend fun readCredential(): StoredCredential? {
        val raw = credentialStore.getToken() ?: return null
        // Migration: pre-OAuth-refresh installs stored a bare access token string.
        // Detect by attempting JSON decode; on failure, treat the raw string as a
        // long-lived access token so the user keeps their session.
        return runCatching { json.decodeFromString(StoredCredential.serializer(), raw) }
            .getOrElse { StoredCredential(accessToken = raw) }
    }

    private suspend fun writeCredential(credential: StoredCredential) {
        credentialStore.saveToken(json.encodeToString(StoredCredential.serializer(), credential))
    }

    @OptIn(ExperimentalTime::class)
    private fun shouldRefresh(stored: StoredCredential): Boolean {
        val expiresAt = stored.expiresAtEpochMs ?: return false
        return Clock.System.now().toEpochMilliseconds() >= expiresAt - REFRESH_BUFFER_MS
    }
}
