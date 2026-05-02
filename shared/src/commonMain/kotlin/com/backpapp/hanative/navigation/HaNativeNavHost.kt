package com.backpapp.hanative.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.backpapp.hanative.ui.StartupRoute
import com.backpapp.hanative.ui.StartupViewModel
import com.backpapp.hanative.ui.auth.AuthScreen
import com.backpapp.hanative.ui.dashboard.DashboardScreen
import com.backpapp.hanative.ui.onboarding.OnboardingScreen
import com.backpapp.hanative.ui.settings.SettingsScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.viewmodel.koinViewModel

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(OnboardingRoute::class, OnboardingRoute.serializer())
            subclass(AuthRoute::class, AuthRoute.serializer())
            subclass(DashboardRoute::class, DashboardRoute.serializer())
            subclass(SettingsRoute::class, SettingsRoute.serializer())
        }
    }
}

@Composable
fun HaNativeNavHost(modifier: Modifier = Modifier.fillMaxSize()) {
    val startupViewModel: StartupViewModel = koinViewModel()
    val startupRoute by startupViewModel.route.collectAsStateWithLifecycle()

    when (startupRoute) {
        StartupRoute.Loading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        StartupRoute.Onboarding -> NavHostContent(modifier, OnboardingRoute)
        StartupRoute.Dashboard -> NavHostContent(modifier, DashboardRoute)
    }
}

@Composable
private fun NavHostContent(
    modifier: Modifier,
    initialRoute: NavKey,
) {
    val backStack = rememberNavBackStack(navConfig, initialRoute)

    NavDisplay(
        backStack = backStack,
        // Insets pushed in here because Scaffold no longer wraps the nav surface; without
        // this the dashboard title sat under the iOS status bar / Android system bars.
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<OnboardingRoute> {
                OnboardingScreen(
                    onNavigateToAuth = { backStack.add(AuthRoute) },
                )
            }
            entry<AuthRoute> {
                AuthScreen(
                    onNavigateToDashboard = {
                        backStack.clear()
                        backStack.add(DashboardRoute)
                    },
                )
            }
            entry<DashboardRoute> {
                DashboardScreen(
                    onNavigateToSettings = {
                        if (backStack.lastOrNull() !is SettingsRoute) {
                            backStack.add(SettingsRoute)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            entry<SettingsRoute> {
                SettingsScreen(
                    onLoggedOut = {
                        backStack.clear()
                        backStack.add(OnboardingRoute)
                    },
                )
            }
        },
    )
}
