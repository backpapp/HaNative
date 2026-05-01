package com.backpapp.hanative.ui.components

// UI-layer models for EntityPicker. Composables consume these only — never
// `HaEntity` or any other `domain/model/` type. ViewModel maps domain → UI.

data class EntityRowUi(
    val entityId: String,
    val domain: String,
    val name: String,
    val stateLabel: String,
)

sealed class EntityPickerUiState {
    object Loading : EntityPickerUiState()
    data class Loaded(val rows: List<EntityRowUi>) : EntityPickerUiState()
    data class EmptyDomain(val domain: String) : EntityPickerUiState()
    object EmptyAll : EntityPickerUiState()
}
