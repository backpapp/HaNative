# Story 3.1: CredentialStore & DataStore expect/actual

Status: review

## Story

As a developer,
I want secure credential storage and lightweight settings persistence wired up,
So that auth tokens never touch unencrypted storage and onboarding screens can persist and retrieve connection config.

## Acceptance Criteria

1. `platform/CredentialStore.kt` (`commonMain`) declares `expect interface CredentialStore` with `suspend fun saveToken(token: String)`, `suspend fun getToken(): String?`, `suspend fun clear()`
2. `AndroidCredentialStore.kt` (`androidMain`) stores the token in `EncryptedSharedPreferences` (Android Keystore-backed) — not in plain `SharedPreferences`
3. `IosCredentialStore.kt` (`iosMain`) stores the token in `Security.framework` Keychain via `SecItemAdd`/`SecItemCopyMatching`
4. DataStore (KMP) is configured separately for HA instance URL and user settings — URL is not a secret and must not be stored in `CredentialStore`
5. Auth token and HA URL do not appear in any log output, crash report, or analytics payload — confirmed by code review
6. `CredentialStore` is registered in `DataModule` (Koin)

## Tasks / Subtasks

- [x] Task 1: Add `security-crypto` dependency to version catalog and `shared/build.gradle.kts` androidMain (AC: 2)
- [x] Task 2: Write failing unit tests for `CredentialStore` interface contract (RED phase) (AC: 1)
- [x] Task 3: Convert `CredentialStore.kt` from plain `interface` to `expect interface` (AC: 1)
- [x] Task 4: Create `AndroidCredentialStore.kt` in `androidMain` using `EncryptedSharedPreferences` (AC: 2)
- [x] Task 5: Create `IosCredentialStore.kt` in `iosMain` using Security.framework Keychain (AC: 3)
- [x] Task 6: Create `HaSettingsKeys.kt` in `commonMain` and configure DataStore module (AC: 4)
- [x] Task 7: Register `CredentialStore` and `DataStore<Preferences>` in `DataModule` (Koin) (AC: 6)
- [x] Task 8: Audit all files for token/URL log leakage — confirm AC 5 by code review (AC: 5)
- [x] Task 9: Build `:shared:testDebugUnitTest :androidApp:assembleDebug` — BUILD SUCCESSFUL (AC: all)

## Dev Notes

### Architecture Context
- `CredentialStore.kt` already exists in `commonMain/platform/` as a plain `interface` — must be promoted to `expect interface` (no logic change, just keyword addition)
- `DataModule.kt` uses `expect fun hapticEngineModule(): Module` pattern — mirror with `expect fun credentialStoreModule(): Module` and `expect fun settingsDataStoreModule(): Module`
- `datastore-preferences-core:1.1.2` already declared in catalog + `commonMain` deps — no new DataStore dep needed
- `security-crypto:1.0.0` (or equivalent) needed in `androidMain` for `EncryptedSharedPreferences` — minSdk 24 satisfies API 23 requirement

### CredentialStore expect/actual Pattern
```
commonMain: expect interface CredentialStore { ... }
androidMain: actual class AndroidCredentialStore(context: Context) : CredentialStore { ... }
iosMain:     actual class IosCredentialStore : CredentialStore { ... }
```
Registered in Koin as `single<CredentialStore> { AndroidCredentialStore(androidContext()) }` (Android) and `single<CredentialStore> { IosCredentialStore() }` (iOS).

### Android: EncryptedSharedPreferences
```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
val prefs = EncryptedSharedPreferences.create(
    context,
    "ha_credentials",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```
Token key: `"ha_auth_token"`. Do NOT log token value at any point.

### iOS: Security.framework Keychain
Use `SecItemAdd` with `kSecClassGenericPassword`, `kSecAttrAccount = "ha_auth_token"`, `kSecAttrService = "com.backpapp.hanative"`.
Use `SecItemCopyMatching` with `kSecReturnData = true` to retrieve.
Use `SecItemDelete` for `clear()`.
Token value must NOT appear in `print()` / `NSLog()` output.

### DataStore: KMP Settings
- Factory: `PreferenceDataStoreFactory.createWithPath { producePath() }`
- Android path: `context.filesDir.resolve("ha_settings.preferences_pb").absolutePath.toPath()`
- iOS path: `(NSHomeDirectory() + "/Library/Application Support/ha_settings.preferences_pb").toPath()`
- Keys file `HaSettingsKeys.kt` in `commonMain/data/local/` with `val HA_URL = stringPreferencesKey("ha_url")`
- DataStore instance is **not** an expect/actual — created via `expect fun settingsDataStoreModule(): Module` in Koin

### No-Logging Constraint (AC 5)
- `AndroidCredentialStore`: no `Log.d/e/w/i` calls that include token value
- `IosCredentialStore`: no `print()` calls that include token value
- `KtorHaWebSocketClient` (existing): check that Bearer header value is not logged — do not modify, just audit
- DataStore: `ha_url` preference value must not appear in log calls

### Koin Registration Pattern
```kotlin
// commonMain DataModule.kt
val dataModule = module {
    includes(hapticEngineModule(), credentialStoreModule(), settingsDataStoreModule())
}
expect fun credentialStoreModule(): Module
expect fun settingsDataStoreModule(): Module
```

## Dev Agent Record

### Debug Log

| # | Issue | Resolution |
|---|-------|------------|
| 1 | `expect interface CredentialStore` — `actual class AndroidCredentialStore` rejected by compiler | Added `actual interface CredentialStore` in androidMain + iosMain; concrete classes implement that interface without `actual` keyword |
| 2 | `MasterKey` unresolved with `security-crypto:1.0.0` | `MasterKey.Builder` was added in 1.1.0-alpha01; upgraded catalog to `1.1.0-alpha06` |
| 3 | `coroutines-test` missing — `runTest` unresolved in commonTest | Added `kotlinx-coroutines-test` to catalog and `commonTest.dependencies` |

### Completion Notes

Story 3.1 complete. Key deliverables:
- `CredentialStore.kt` (commonMain) promoted to `expect interface` with 3 suspend methods
- `actual interface CredentialStore` declared in androidMain + iosMain (required for expect/actual interface pattern)
- `AndroidCredentialStore` stores token in `EncryptedSharedPreferences` with `MasterKey.Builder` (AES256_GCM Keystore key, AES256_SIV key encryption, AES256_GCM value encryption)
- `IosCredentialStore` uses Security.framework Keychain: `SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete` via `kSecClassGenericPassword`, service `com.backpapp.hanative`, account `ha_auth_token`
- `HaSettingsKeys.kt` (commonMain) defines `HA_URL = stringPreferencesKey("ha_url")`
- `CredentialStoreModule` + `SettingsDataStoreModule` (expect/actual, Android + iOS) registered via `DataModule.includes()`
- `DataStore<Preferences>` path: Android = `filesDir/ha_settings.preferences_pb`; iOS = `NSHomeDirectory()/Library/Application Support/ha_settings.preferences_pb`
- Log audit: only `println` in codebase is `id=$id` (integer) in `KtorHaWebSocketClient` — no token/URL leakage
- `CredentialStoreTest` (4 tests, commonTest): saveToken, getToken null, clear, replace — all pass
- `BUILD SUCCESSFUL` for `:shared:testDebugUnitTest :androidApp:assembleDebug`

## File List

- `gradle/libs.versions.toml` — added `security-crypto = "1.1.0-alpha06"`, `security-crypto` library alias, `kotlinx-coroutines-test` library alias
- `shared/build.gradle.kts` — added `libs.security.crypto` to androidMain, `libs.kotlinx.coroutines.test` to commonTest
- `shared/src/commonMain/kotlin/com/backpapp/hanative/platform/CredentialStore.kt` — promoted from `interface` to `expect interface`
- `shared/src/commonMain/kotlin/com/backpapp/hanative/di/DataModule.kt` — added `credentialStoreModule()`, `settingsDataStoreModule()` expect declarations + includes
- `shared/src/commonMain/kotlin/com/backpapp/hanative/data/local/HaSettingsKeys.kt` — new, `HA_URL` preferences key
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/CredentialStore.kt` — new, `actual interface CredentialStore`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/platform/AndroidCredentialStore.kt` — new, `EncryptedSharedPreferences` implementation
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/CredentialStoreModule.kt` — new, `actual fun credentialStoreModule()` with `AndroidCredentialStore`
- `shared/src/androidMain/kotlin/com/backpapp/hanative/di/SettingsDataStoreModule.kt` — new, `actual fun settingsDataStoreModule()` with Android path
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/CredentialStore.kt` — new, `actual interface CredentialStore`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/platform/IosCredentialStore.kt` — new, Security.framework Keychain implementation
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/CredentialStoreModule.kt` — new, `actual fun credentialStoreModule()` with `IosCredentialStore`
- `shared/src/iosMain/kotlin/com/backpapp/hanative/di/SettingsDataStoreModule.kt` — new, `actual fun settingsDataStoreModule()` with iOS path
- `shared/src/commonTest/kotlin/com/backpapp/hanative/platform/CredentialStoreTest.kt` — new, 4 contract tests via `FakeCredentialStore`

## Change Log

| Date | Change |
|------|--------|
| 2026-04-28 | Story 3.1 complete — CredentialStore expect/actual (Keystore/Keychain), DataStore settings, 4 tests pass, BUILD SUCCESSFUL |
