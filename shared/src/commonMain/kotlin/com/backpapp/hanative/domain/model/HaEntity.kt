package com.backpapp.hanative.domain.model

import kotlinx.datetime.Instant

sealed class HaEntity {
    abstract val entityId: String
    abstract val state: String
    abstract val attributes: Map<String, Any?>
    abstract val lastChanged: Instant
    abstract val lastUpdated: Instant

    data class Light(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val isOn: Boolean get() = state == "on"
        val brightness: Int? get() = (attributes["brightness"] as? Number)?.toInt()
        val colorTemp: Int? get() = (attributes["color_temp"] as? Number)?.toInt()
        val rgbColor: List<Int>? get() {
            @Suppress("UNCHECKED_CAST")
            return (attributes["rgb_color"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toInt() }
        }
    }

    data class Switch(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val isOn: Boolean get() = state == "on"
    }

    data class Climate(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val currentTemperature: Double? get() = (attributes["current_temperature"] as? Number)?.toDouble()
        val targetTemperature: Double? get() = (attributes["temperature"] as? Number)?.toDouble()
        val hvacMode: String get() = state
    }

    data class Cover(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val isOpen: Boolean get() = state == "open"
        val currentPosition: Int? get() = (attributes["current_position"] as? Number)?.toInt()
    }

    data class MediaPlayer(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val isPlaying: Boolean get() = state == "playing"
        val mediaTitle: String? get() = attributes["media_title"] as? String
        val mediaArtist: String? get() = attributes["media_artist"] as? String
        val volumeLevel: Double? get() = (attributes["volume_level"] as? Number)?.toDouble()
    }

    data class Sensor(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val unit: String? get() = attributes["unit_of_measurement"] as? String
        val deviceClass: String? get() = attributes["device_class"] as? String
    }

    data class BinarySensor(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val isOn: Boolean get() = state == "on"
        val deviceClass: String? get() = attributes["device_class"] as? String
    }

    data class InputBoolean(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val isOn: Boolean get() = state == "on"
    }

    data class InputSelect(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val options: List<String> get() {
            @Suppress("UNCHECKED_CAST")
            return (attributes["options"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        }
    }

    data class Script(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity() {
        val isRunning: Boolean get() = state == "on"
    }

    data class Scene(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
    ) : HaEntity()

    data class Unknown(
        override val entityId: String,
        override val state: String,
        override val attributes: Map<String, Any?>,
        override val lastChanged: Instant,
        override val lastUpdated: Instant,
        val domain: String,
    ) : HaEntity()
}

fun HaEntity(
    entityId: String,
    state: String,
    attributes: Map<String, Any?>,
    lastChanged: Instant,
    lastUpdated: Instant,
): HaEntity {
    val domain = entityId.substringBefore(".")
    return when (domain) {
        "light" -> HaEntity.Light(entityId, state, attributes, lastChanged, lastUpdated)
        "switch" -> HaEntity.Switch(entityId, state, attributes, lastChanged, lastUpdated)
        "climate" -> HaEntity.Climate(entityId, state, attributes, lastChanged, lastUpdated)
        "cover" -> HaEntity.Cover(entityId, state, attributes, lastChanged, lastUpdated)
        "media_player" -> HaEntity.MediaPlayer(entityId, state, attributes, lastChanged, lastUpdated)
        "sensor" -> HaEntity.Sensor(entityId, state, attributes, lastChanged, lastUpdated)
        "binary_sensor" -> HaEntity.BinarySensor(entityId, state, attributes, lastChanged, lastUpdated)
        "input_boolean" -> HaEntity.InputBoolean(entityId, state, attributes, lastChanged, lastUpdated)
        "input_select" -> HaEntity.InputSelect(entityId, state, attributes, lastChanged, lastUpdated)
        "script" -> HaEntity.Script(entityId, state, attributes, lastChanged, lastUpdated)
        "scene" -> HaEntity.Scene(entityId, state, attributes, lastChanged, lastUpdated)
        else -> HaEntity.Unknown(entityId, state, attributes, lastChanged, lastUpdated, domain)
    }
}
