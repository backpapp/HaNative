package com.backpapp.hanative.ui.dashboard

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardChrome {
    private val _activeDashboardName = MutableStateFlow<String?>(null)
    val activeDashboardName: StateFlow<String?> = _activeDashboardName.asStateFlow()

    private val _openSwitcherSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openSwitcherSignals: SharedFlow<Unit> = _openSwitcherSignals.asSharedFlow()

    fun setActiveDashboardName(name: String?) {
        _activeDashboardName.value = name
    }

    fun requestOpenSwitcher() {
        _openSwitcherSignals.tryEmit(Unit)
    }
}
