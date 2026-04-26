// HA Android upstream reference snapshot — captured 2026-04-26
// Source: home-assistant/android — common/src/main/kotlin/io/homeassistant/companion/android/common/data/websocket/impl/WebSocketCoreImpl.kt
// License: Apache 2.0
// Purpose: Reference for diff watcher (Story 1.5). KMP port in shared/src/commonMain/.../data/remote/KtorHaWebSocketClient.kt

// Key patterns ported to KMP:
//   - Auth handshake: auth_required → auth → auth_ok/auth_invalid
//   - subscribe_events for state_changed
//   - call_service with service_data
//   - get_states for initial state load
//   - OkHttp WebSocket replaced with Ktor DefaultClientWebSocketSession

// Original uses OkHttp WebSocket; KMP port uses Ktor (ktor-client-websockets).
// Message correlation via incrementing ID map (pendingResults: Map<Int, Channel<ResultDto>>).
