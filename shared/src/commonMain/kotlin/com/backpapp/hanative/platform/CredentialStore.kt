package com.backpapp.hanative.platform

interface CredentialStore {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun clear()
}
