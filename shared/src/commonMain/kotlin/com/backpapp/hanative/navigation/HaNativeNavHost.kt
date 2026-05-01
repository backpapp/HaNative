package com.backpapp.hanative.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.backpapp.hanative.ui.LocalWindowSizeClass
import com.backpapp.hanative.ui.StartupRoute
import com.backpapp.hanative.ui.StartupViewModel
import com.backpapp.hanative.ui.WindowSizeClass
import com.backpapp.hanative.ui.auth.AuthScreen
import com.backpapp.hanative.ui.dashboard.DashboardChrome
import com.backpapp.hanative.ui.dashboard.DashboardScreen
import com.backpapp.hanative.ui.onboarding.OnboardingScreen
import com.backpapp.hanative.ui.settings.SettingsScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.koinInject
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
    val windowSizeClass = LocalWindowSizeClass.current
    val startupViewModel: StartupViewModel = koinViewModel()
    val startupRoute by startupViewModel.route.collectAsStateWithLifecycle()

    when (startupRoute) {
        StartupRoute.Loading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        StartupRoute.Onboarding -> NavHostContent(modifier, windowSizeClass, OnboardingRoute)
        StartupRoute.Dashboard -> NavHostContent(modifier, windowSizeClass, DashboardRoute)
    }
}

@Composable
private fun NavHostContent(
    modifier: Modifier,
    windowSizeClass: WindowSizeClass,
    initialRoute: NavKey,
) {
    val backStack = rememberNavBackStack(navConfig, initialRoute)
    val current = backStack.lastOrNull()
    val showNavBar = current !is OnboardingRoute && current !is AuthRoute

    val dashboardChrome: DashboardChrome = koinInject()
    val activeDashboardName by dashboardChrome.activeDashboardName.collectAsStateWithLifecycle()
    val navItems = remember(activeDashboardName) {
        listOf(activeDashboardName ?: "Dashboard", "Rooms", "Settings")
    }

    val navContent = @Composable { contentModifier: Modifier ->
        NavDisplay(
            backStack = backStack,
            modifier = contentModifier,
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
                entry<DashboardRoute> { DashboardScreen(modifier = Modifier.fillMaxSize()) }
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

    val selectedIndex by remember {
        derivedStateOf {
            when (backStack.lastOrNull()) {
                is SettingsRoute -> 2
                else -> 0
            }
        }
    }

    val handleNavItemClick: (Int) -> Unit = { index ->
        when (index) {
            0 -> {
                if (selectedIndex == 0) dashboardChrome.requestOpenSwitcher()
            }
            2 -> if (backStack.lastOrNull() !is SettingsRoute) backStack.add(SettingsRoute)
        }
    }

    if (windowSizeClass == WindowSizeClass.EXPANDED) {
        ExpandedLayout(
            modifier = modifier,
            showNavBar = showNavBar,
            navItems = navItems,
            onNavItemClick = handleNavItemClick,
            navContent = navContent,
        )
    } else {
        CompactLayout(
            modifier = modifier,
            showNavBar = showNavBar,
            selectedIndex = selectedIndex,
            navItems = navItems,
            onNavItemClick = handleNavItemClick,
            navContent = navContent,
        )
    }
}

@Composable
private fun CompactLayout(
    modifier: Modifier = Modifier,
    showNavBar: Boolean = true,
    selectedIndex: Int = 0,
    navItems: List<String>,
    onNavItemClick: (Int) -> Unit = {},
    navContent: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showNavBar) {
                NavigationBar {
                    navItems.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { onNavItemClick(index) },
                            icon = { Box(Modifier.width(24.dp).padding(4.dp)) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        navContent(Modifier.fillMaxSize().padding(innerPadding))
    }
}

@Composable
private fun ExpandedLayout(
    modifier: Modifier = Modifier,
    showNavBar: Boolean = true,
    navItems: List<String>,
    onNavItemClick: (Int) -> Unit = {},
    navContent: @Composable (Modifier) -> Unit,
) {
    Row(modifier = modifier) {
        if (showNavBar) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
                navItems.forEachIndexed { index, label ->
                    Box(
                        Modifier
                            .width(80.dp)
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                            .clickable { onNavItemClick(index) },
                    ) {
                        Text(label)
                    }
                }
            }
        }
        navContent(Modifier.weight(1f).fillMaxHeight())
    }
}
