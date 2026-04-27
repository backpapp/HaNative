package com.backpapp.hanative.platform

actual interface CredentialStore {
    actual suspend fun saveToken(token: String)
    actual suspend fun getToken(): String?
    actual suspend fun clear()
}
