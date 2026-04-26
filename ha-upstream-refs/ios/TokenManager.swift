// HA iOS upstream reference snapshot — captured 2026-04-26
// Source: home-assistant/iOS — Sources/Shared/API/Authentication/TokenManager.swift
// License: Apache 2.0
// Purpose: Reference for diff watcher (Story 1.5). Refresh dedup pattern ported to
//          shared/src/commonMain/.../data/remote/AuthenticationRepositoryImpl.kt

// Key pattern ported to Kotlin:
//   Swift: DispatchQueue + NSLock to serialize concurrent token refresh requests
//   KMP port: Kotlin Mutex + Deferred<String> to collapse concurrent getValidToken() calls
//
//   Original Swift pattern:
//     private var tokenPromise: Promise<String>?
//     func getValidToken() -> Promise<String> {
//         if let existing = tokenPromise { return existing }
//         let promise = fetchToken()
//         tokenPromise = promise
//         return promise.ensure { self.tokenPromise = nil }
//     }
//
//   KMP adaptation in AuthenticationRepositoryImpl.kt uses Mutex.withLock + CompletableDeferred.
