package com.backpapp.hanative.platform

expect interface CredentialStore {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun clear()
}
