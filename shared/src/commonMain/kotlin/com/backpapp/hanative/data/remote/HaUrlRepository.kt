package com.backpapp.hanative.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.backpapp.hanative.data.local.HaSettingsKeys
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

private const val TEST_URL_TIMEOUT_MS = 10_000L

class HaUrlRepository(
    private val httpClient: HttpClient,
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun testUrl(url: String): Result<Unit> {
        return try {
            withTimeout(TEST_URL_TIMEOUT_MS) {
                val normalized = url.trimEnd('/')
                val response = httpClient.get("$normalized/api/")
                val status = response.status.value
                if (status !in 200..299 && status != 401) {
                    throw IllegalStateException("Unexpected response: ${response.status}")
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun saveUrl(url: String): Result<Unit> {
        return try {
            dataStore.edit { prefs ->
                prefs[HaSettingsKeys.HA_URL] = url
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun getUrl(): String? = dataStore.data.first()[HaSettingsKeys.HA_URL]

    suspend fun clearUrl(): Result<Unit> {
        return try {
            dataStore.edit { prefs -> prefs.remove(HaSettingsKeys.HA_URL) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
