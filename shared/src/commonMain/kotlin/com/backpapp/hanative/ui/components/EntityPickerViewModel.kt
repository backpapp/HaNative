package com.backpapp.hanative.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backpapp.hanative.domain.model.HaEntity
import com.backpapp.hanative.domain.usecase.GetSortedEntitiesUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val LOAD_DEADLINE_MS = 800L

class EntityPickerViewModel(
    getSortedEntities: GetSortedEntitiesUseCase,
) : ViewModel() {

    private val _selectedDomain = MutableStateFlow<String?>(null)
    val selectedDomain: StateFlow<String?> = _selectedDomain.asStateFlow()

    private val _loadDeadlineHit = MutableStateFlow(false)

    val state: StateFlow<EntityPickerUiState> = combine(
        getSortedEntities(),
        _selectedDomain,
        _loadDeadlineHit,
    ) { entities, domain, deadlineHit ->
        derive(entities, domain, deadlineHit)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = EntityPickerUiState.Loading,
    )

    init {
        viewModelScope.launch {
            delay(LOAD_DEADLINE_MS)
            _loadDeadlineHit.value = true
        }
    }

    fun onDomainSelected(domain: String?) {
        _selectedDomain.value = domain
    }

    private fun derive(
        entities: List<HaEntity>,
        domain: String?,
        deadlineHit: Boolean,
    ): EntityPickerUiState {
        if (entities.isEmpty()) {
            return if (!deadlineHit) EntityPickerUiState.Loading else EntityPickerUiState.EmptyAll
        }
        val mapped = entities
            .filter { domain == null || domainOf(it) == domain }
            .map { it.toRowUi() }
        return if (mapped.isEmpty() && domain != null) {
            EntityPickerUiState.EmptyDomain(domain)
        } else {
            EntityPickerUiState.Loaded(mapped)
        }
    }
}

private fun domainOf(entity: HaEntity): String = entity.entityId.substringBefore('.')

private fun HaEntity.toRowUi(): EntityRowUi = EntityRowUi(
    entityId = entityId,
    domain = domainOf(this),
    name = friendlyName(this, entityId),
    stateLabel = stateLabel(state),
)
