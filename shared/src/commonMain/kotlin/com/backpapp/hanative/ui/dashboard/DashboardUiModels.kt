package com.backpapp.hanative.ui.dashboard

sealed class DashboardUiState {
    data object Loading : DashboardUiState()
    data class Empty(
        val pickerVisible: Boolean = false,
        val switcher: DashboardSwitcherUi = DashboardSwitcherUi(),
        val indicator: StaleIndicatorUi = StaleIndicatorUi(StaleIndicatorKind.Connected),
    ) : DashboardUiState()
    data class Success(
        val dashboardName: String,
        val activeDashboardId: String? = null,
        val cards: List<DashboardCardUi>,
        val isStale: Boolean,
        val pickerVisible: Boolean = false,
        val switcher: DashboardSwitcherUi = DashboardSwitcherUi(),
        val indicator: StaleIndicatorUi = StaleIndicatorUi(StaleIndicatorKind.Connected),
    ) : DashboardUiState()
}

data class StaleIndicatorUi(
    val kind: StaleIndicatorKind,
    // Used only when kind is Stale or Reconnecting. ms-since-epoch of the last successful
    // WebSocket message receipt; null if app has never connected this session (cold launch
    // with HA unreachable — shows "Last updated --" fallback).
    val lastMessageEpochMs: Long? = null,
)

sealed class StaleIndicatorKind {
    data object Connected : StaleIndicatorKind()
    data object Stale : StaleIndicatorKind()
    data object Reconnecting : StaleIndicatorKind()
    // HA rejected the stored credential. Distinct from Stale so the UI can prompt re-auth
    // instead of showing "Reconnecting…" indefinitely.
    data object InvalidAuth : StaleIndicatorKind()
}

data class DashboardCardUi(
    val cardId: String,
    val entityId: String,
)

data class DashboardSummaryUi(
    val id: String,
    val name: String,
    val cardCount: Int,
    // True for the in-memory pending dashboard between ConfirmCreate and first AddCard.
    // UI uses this to enable Delete unconditionally (D3=C — pending discard bypasses canDelete + dialog).
    val isPending: Boolean = false,
)

data class DashboardSwitcherUi(
    val visible: Boolean = false,
    val dashboards: List<DashboardSummaryUi> = emptyList(),
    val activeDashboardId: String? = null,
    val creating: Boolean = false,
    val pendingNewName: String = "",
    val renamingId: String? = null,
    val pendingRenameText: String = "",
    val pendingDeleteId: String? = null,
    val canDelete: Boolean = false,
)

sealed class DashboardIntent {
    data class AddCard(val entityId: String) : DashboardIntent()
    data class RemoveCard(val cardId: String) : DashboardIntent()
    data class Reorder(val orderedCardIds: List<String>) : DashboardIntent()
    data object OpenPicker : DashboardIntent()
    data object DismissPicker : DashboardIntent()

    // Story 4.7 additions
    data object OpenSwitcher : DashboardIntent()
    data object DismissSwitcher : DashboardIntent()
    data class SelectDashboard(val dashboardId: String) : DashboardIntent()
    data object BeginCreateDashboard : DashboardIntent()
    data object CancelCreateDashboard : DashboardIntent()
    data class UpdateNewDashboardName(val text: String) : DashboardIntent()
    data class ConfirmCreateDashboard(val name: String) : DashboardIntent()
    data class BeginRenameDashboard(val dashboardId: String) : DashboardIntent()
    data object CancelRenameDashboard : DashboardIntent()
    data class UpdateRenameText(val text: String) : DashboardIntent()
    data class ConfirmRename(val dashboardId: String, val name: String) : DashboardIntent()
    data class RequestDeleteDashboard(val dashboardId: String) : DashboardIntent()
    data object CancelDeleteDashboard : DashboardIntent()
    data class ConfirmDeleteDashboard(val dashboardId: String) : DashboardIntent()
}
