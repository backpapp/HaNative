package com.backpapp.hanative.di

import com.backpapp.hanative.ui.StartupViewModel
import com.backpapp.hanative.ui.auth.AuthViewModel
import com.backpapp.hanative.ui.components.EntityCardViewModel
import com.backpapp.hanative.ui.components.EntityPickerViewModel
import com.backpapp.hanative.ui.dashboard.DashboardViewModel
import com.backpapp.hanative.ui.onboarding.OnboardingViewModel
import com.backpapp.hanative.ui.settings.SettingsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val presentationModule = module {
    viewModel { OnboardingViewModel(get(), get()) }
    viewModel { AuthViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { StartupViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { EntityPickerViewModel(get()) }
    viewModel { (entityId: String) -> EntityCardViewModel(entityId, get(), get()) }
    viewModel {
        DashboardViewModel(
            getDashboards = get(),
            saveDashboard = get(),
            addCard = get(),
            removeCard = get(),
            reorderCards = get(),
            renameDashboard = get(),
            deleteDashboard = get(),
            getActiveDashboardId = get(),
            setActiveDashboardId = get(),
            idGenerator = get(),
            observeConnectionState = get(),
            observeLastWebSocketMessage = get(),
            dashboardChrome = get(),
        )
    }
}
