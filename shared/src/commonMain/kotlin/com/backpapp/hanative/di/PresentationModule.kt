package com.backpapp.hanative.di

import com.backpapp.hanative.ui.StartupViewModel
import com.backpapp.hanative.ui.auth.AuthViewModel
import com.backpapp.hanative.ui.onboarding.OnboardingViewModel
import com.backpapp.hanative.ui.settings.SettingsViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val presentationModule = module {
    viewModel { OnboardingViewModel(get(), get()) }
    viewModel { AuthViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { StartupViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
}
