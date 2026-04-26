# HA Android WS Message Type Snapshots

Captured 2026-04-26 from:
home-assistant/android — common/src/main/kotlin/io/homeassistant/companion/android/common/data/websocket/impl/entities/

License: Apache 2.0

KMP ports in: shared/src/commonMain/kotlin/com/backpapp/hanative/data/remote/entities/

## Files watched

| HA Android source | KMP port |
|---|---|
| `AuthWebSocketResponse.kt` | `AuthDto.kt` |
| `EventResponse.kt` | `EventDto.kt` |
| `GetStatesResponse.kt` | `ResultDto.kt` (result field) |
| `EntityResponse.kt` | `EntityStateDto.kt` |
| `ServiceCallResponse.kt` | `ResultDto.kt` |
| `SubscribeEventsResponse.kt` | `ResultDto.kt` |

## Notes

HA Android entities/ folder contains message types for the WebSocket protocol.
Ported verbatim (pure Kotlin, zero platform deps). KMP port uses @Serializable data classes
with kotlinx.serialization instead of Gson/Moshi.
