package com.backpapp.hanative.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On-disk representation of a Home Assistant credential, keyed by `accessToken`.
 *
 * Long-lived tokens (entered manually via the Settings → Connect token field) carry only
 * `accessToken` — `refreshToken` and `expiresAtEpochMs` are null and refresh is skipped.
 *
 * OAuth (Indieauth) tokens carry the full triple: HA's access tokens expire in ~30 min, so
 * `refreshToken` lets [AuthenticationRepositoryImpl] silently mint a new one before expiry.
 */
@Serializable
internal data class StoredCredential(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_at_epoch_ms") val expiresAtEpochMs: Long? = null,
)
