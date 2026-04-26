// HA iOS upstream reference snapshot — captured 2026-04-26
// Source: home-assistant/iOS — Sources/Shared/API/Models/WebSocketMessage.swift
// License: Apache 2.0
// Purpose: Reference for diff watcher (Story 1.5). Protocol message shapes ported to
//          shared/src/commonMain/.../data/remote/entities/

// Key message types:
//   - AuthRequired, AuthMessage, AuthOK, AuthInvalid
//   - SubscribeEventsMessage, EventMessage
//   - CallServiceMessage, GetStatesMessage
//   - ResultMessage (success/failure wrapper)
//
// KMP port uses @Serializable data classes with kotlinx.serialization.
// Swift Codable replaced with kotlinx.serialization @SerialName annotations.
