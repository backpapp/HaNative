package com.backpapp.hanative.ui.components

// UI-layer models for EntityCard. Composables consume these only — never
// `HaEntity` or any other `domain/model/` type. ViewModel maps domain → UI.

import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.datetime.Instant

enum class EntityCardSize { Standard, Compact }

enum class EntityCardPreviewState { Default, Active, Stale, Optimistic, Error }

sealed class EntityCardUiState {
    abstract val entityId: String
    abstract val title: String
    abstract val icon: ImageVector
    abstract val isStale: Boolean
    abstract val lastChanged: Instant?
    abstract val rejectionCounter: Long

    data class Toggle(
        override val entityId: String,
        override val title: String,
        override val icon: ImageVector,
        override val isStale: Boolean,
        override val lastChanged: Instant?,
        override val rejectionCounter: Long = 0L,
        val isOn: Boolean,
        val isInteractable: Boolean,
    ) : EntityCardUiState()

    data class Stepper(
        override val entityId: String,
        override val title: String,
        override val icon: ImageVector,
        override val isStale: Boolean,
        override val lastChanged: Instant?,
        override val rejectionCounter: Long = 0L,
        val currentLabel: String,
        val formattedTemp: String,
        val hasTarget: Boolean,
        val isInteractable: Boolean,
    ) : EntityCardUiState()

    data class Trigger(
        override val entityId: String,
        override val title: String,
        override val icon: ImageVector,
        override val isStale: Boolean,
        override val lastChanged: Instant?,
        override val rejectionCounter: Long = 0L,
        val subtitle: String,
        val triggerCounter: Long = 0L,
        val isInteractable: Boolean,
    ) : EntityCardUiState()

    data class Media(
        override val entityId: String,
        override val title: String,
        override val icon: ImageVector,
        override val isStale: Boolean,
        override val lastChanged: Instant?,
        override val rejectionCounter: Long = 0L,
        val subtitle: String,
        val isPlaying: Boolean,
        val isInteractable: Boolean,
    ) : EntityCardUiState()

    data class Unknown(
        override val entityId: String,
        override val title: String,
        override val icon: ImageVector,
        override val isStale: Boolean,
        override val lastChanged: Instant?,
        override val rejectionCounter: Long = 0L,
        val subtitle: String,
    ) : EntityCardUiState()

    data class ReadOnly(
        override val entityId: String,
        override val title: String,
        override val icon: ImageVector,
        override val isStale: Boolean,
        override val lastChanged: Instant?,
        override val rejectionCounter: Long = 0L,
        val label: String,
    ) : EntityCardUiState()
}

sealed class EntityCardIntent {
    data object Toggle : EntityCardIntent()
    data class StepTemp(val direction: Int) : EntityCardIntent()
    data object Trigger : EntityCardIntent()
    data object PlayPause : EntityCardIntent()
}
