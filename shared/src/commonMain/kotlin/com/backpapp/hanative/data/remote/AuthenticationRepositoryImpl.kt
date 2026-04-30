package com.backpapp.hanative.data.remote

import com.backpapp.hanative.platform.CredentialStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Story 3.5 adds HttpClient here for OAuth token refresh endpoint calls.
class AuthenticationRepositoryImpl(
    private val credentialStore: CredentialStore,
) {
    private val mutex = Mutex()

    suspend fun getValidToken(): String = mutex.withLock {
        credentialStore.getToken()
            ?: throw IllegalStateException("No access token stored. Complete onboarding first.")
    }

    suspend fun getToken(): String? = mutex.withLock { credentialStore.getToken() }

    suspend fun saveToken(token: String) {
        mutex.withLock { credentialStore.saveToken(token) }
    }

    suspend fun clearToken() {
        mutex.withLock { credentialStore.clear() }
    }
}
