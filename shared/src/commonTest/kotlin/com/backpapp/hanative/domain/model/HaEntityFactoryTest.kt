package com.backpapp.hanative.domain.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// HaEntity(...) is the factory that EntityRepositoryImpl.toDomain() delegates to.
// Unknown / unrecognized domains fall through to HaEntity.Unknown, satisfying NFR10
// (data layer never crashes the dashboard on unknown HA entity domain).
class HaEntityFactoryTest {

    @Test
    fun unknownDomainMapsToHaEntityUnknown() {
        val ts = Instant.fromEpochSeconds(0)
        val entity = HaEntity(
            entityId = "lawnmower.backyard",
            state = "mowing",
            attributes = mapOf("battery" to 80),
            lastChanged = ts,
            lastUpdated = ts,
        )
        assertIs<HaEntity.Unknown>(entity)
        assertEquals("lawnmower", entity.domain)
        assertEquals("lawnmower.backyard", entity.entityId)
        assertEquals("mowing", entity.state)
    }

    @Test
    fun unknownDomainPreservesAttributesShape() {
        val ts = Instant.fromEpochSeconds(0)
        val entity = HaEntity(
            entityId = "vacuum.dyson",
            state = "docked",
            attributes = mapOf(
                "fan_speed" to "high",
                "battery_level" to 95,
                "nested_list" to listOf(1, 2, 3),
            ),
            lastChanged = ts,
            lastUpdated = ts,
        )
        assertIs<HaEntity.Unknown>(entity)
        assertEquals("vacuum", entity.domain)
        assertEquals("high", entity.attributes["fan_speed"])
        assertEquals(listOf(1, 2, 3), entity.attributes["nested_list"])
    }
}
