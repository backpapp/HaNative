package com.backpapp.hanative.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CredentialStoreTest {

    private val store: CredentialStore = FakeCredentialStore()

    @Test
    fun saveToken_storesToken() = runTest {
        store.saveToken("test-token-abc")
        assertEquals("test-token-abc", store.getToken())
    }

    @Test
    fun getToken_returnsNullWhenEmpty() = runTest {
        assertNull(store.getToken())
    }

    @Test
    fun clear_removesStoredToken() = runTest {
        store.saveToken("test-token-abc")
        store.clear()
        assertNull(store.getToken())
    }

    @Test
    fun saveToken_replacesExistingToken() = runTest {
        store.saveToken("first-token")
        store.saveToken("second-token")
        assertEquals("second-token", store.getToken())
    }
}

private class FakeCredentialStore : CredentialStore {
    private var token: String? = null
    override suspend fun saveToken(token: String) { this.token = token }
    override suspend fun getToken(): String? = token
    override suspend fun clear() { token = null }
}
