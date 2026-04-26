// HA Android upstream reference snapshot — captured 2026-04-26
// Source: home-assistant/android — common/src/main/kotlin/io/homeassistant/companion/android/common/data/integration/Entity.kt
// License: Apache 2.0
// Purpose: Reference for diff watcher (Story 1.5). KMP port in shared/src/commonMain/.../domain/model/HaEntity.kt

package io.homeassistant.companion.android.common.data.integration

import java.util.Calendar

data class Entity<T>(
    val entityId: String,
    val state: String,
    val attributes: T,
    val lastChanged: Calendar,
    val lastUpdated: Calendar,
    val context: Map<String, Any?>?
)

// Extension functions omitted for brevity — see source repo for full implementation.
// KMP port replaces Calendar with kotlinx.datetime.Instant and adds domain-specific sealed subtypes.
