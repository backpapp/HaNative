// HA Android upstream reference snapshot — captured 2026-04-26
// Source: home-assistant/android — common/src/main/kotlin/io/homeassistant/companion/android/common/data/authentication/impl/AuthenticationRepositoryImpl.kt
// License: Apache 2.0
// Purpose: Reference for diff watcher (Story 1.5). KMP port in shared/src/commonMain/.../data/remote/AuthenticationRepositoryImpl.kt

// Key patterns ported to KMP:
//   - Token storage via platform CredentialStore (replaces Android SharedPreferences)
//   - Retrofit HTTP calls replaced with Ktor HttpClient
//   - Mutex + Deferred token refresh deduplication (also ported from iOS TokenManager)
