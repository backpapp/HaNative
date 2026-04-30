package com.backpapp.hanative.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute : NavKey

@Serializable
data object AuthRoute : NavKey

@Serializable
data object DashboardRoute : NavKey

@Serializable
data object SettingsRoute : NavKey
