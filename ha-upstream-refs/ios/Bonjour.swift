// HA iOS upstream reference snapshot — captured 2026-04-26
// Source: home-assistant/iOS — Sources/App/Onboarding/API/Bonjour.swift
// License: Apache 2.0
// Purpose: Reference for diff watcher (Story 1.5). mDNS discovery pattern ported to
//          shared/src/iosMain/.../platform/IosServerDiscovery.kt (Story 3.2)

// Key pattern:
//   Uses NSNetServiceBrowser to discover _home-assistant._tcp services.
//   Emits HaServerInfo(name, host, port) for each resolved service.
//   Story 3.2 ports this to Kotlin: NSNetServiceBrowser → IosServerDiscovery actual class.
