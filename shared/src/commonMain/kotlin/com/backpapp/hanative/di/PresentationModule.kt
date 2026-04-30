package com.backpapp.hanative.di

import com.backpapp.hanative.ui.onboarding.OnboardingViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val presentationModule = module {
    viewModel { OnboardingViewModel(get(), get()) }
}
