package com.backpapp.hanative.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(OnboardingRoute::class, OnboardingRoute.serializer())
            subclass(DashboardRoute::class, DashboardRoute.serializer())
        }
    }
}

@Composable
fun HaNativeNavHost(modifier: Modifier = Modifier.fillMaxSize()) {
    val backStack = rememberNavBackStack(navConfig, OnboardingRoute)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<OnboardingRoute> { Box(Modifier.fillMaxSize()) }
            entry<DashboardRoute> { Box(Modifier.fillMaxSize()) }
        }
    )
}
