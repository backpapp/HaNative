package com.backpapp.hanative.platform

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE = "ha_credentials"
private const val KEY_AUTH_TOKEN = "ha_auth_token"

class AndroidCredentialStore(private val context: Context) : CredentialStore {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun saveToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    override suspend fun getToken(): String? =
        prefs.getString(KEY_AUTH_TOKEN, null)

    override suspend fun clear() {
        prefs.edit().remove(KEY_AUTH_TOKEN).apply()
    }
}
