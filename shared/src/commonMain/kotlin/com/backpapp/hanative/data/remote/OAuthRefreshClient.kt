package com.backpapp.hanative.data.remote

import com.backpapp.hanative.data.remote.entities.AuthTokenResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters

/**
 * Thin HA `/auth/token` refresh-token client. Indirected behind an interface so
 * [AuthenticationRepositoryImpl] tests can construct the repo without a live HttpClient
 * (default `null` ⇒ refresh is disabled, which matches the long-lived-token code path).
 */
interface OAuthRefreshClient {
    /**
     * Exchange a refresh token for a new access token. Throws on non-2xx so callers can
     * differentiate "refresh failed once, retry later" from "refresh token revoked, must
     * re-onboard" via the surrounding catch.
     */
    suspend fun refresh(refreshToken: String): AuthTokenResponseDto
}

class HaOAuthRefreshClient(
    private val httpClient: HttpClient,
    private val urlRepository: HaUrlRepository,
    private val clientId: String,
) : OAuthRefreshClient {

    override suspend fun refresh(refreshToken: String): AuthTokenResponseDto {
        val haUrl = urlRepository.getUrl()
            ?: throw IllegalStateException("HA URL missing — cannot refresh token")
        val response: HttpResponse = httpClient.submitForm(
            url = "${haUrl.trimEnd('/')}/auth/token",
            formParameters = Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", clientId)
            },
        )
        if (response.status.value !in 200..299) {
            throw IllegalStateException("Refresh failed: HTTP ${response.status.value}")
        }
        return response.body()
    }
}
