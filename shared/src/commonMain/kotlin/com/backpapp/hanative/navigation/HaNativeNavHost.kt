package com.backpapp.hanative.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.backpapp.hanative.ui.LocalWindowSizeClass
import com.backpapp.hanative.ui.WindowSizeClass
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

private val navItems = listOf("Dashboard", "Rooms", "Settings")

@Composable
fun HaNativeNavHost(modifier: Modifier = Modifier.fillMaxSize()) {
    val windowSizeClass = LocalWindowSizeClass.current
    val backStack = rememberNavBackStack(navConfig, OnboardingRoute)

    val navContent = @Composable { contentModifier: Modifier ->
        NavDisplay(
            backStack = backStack,
            modifier = contentModifier,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<OnboardingRoute> { Box(Modifier.fillMaxSize()) }
                entry<DashboardRoute> { Box(Modifier.fillMaxSize()) }
            },
        )
    }

    if (windowSizeClass == WindowSizeClass.EXPANDED) {
        ExpandedLayout(modifier = modifier, navContent = navContent)
    } else {
        CompactLayout(modifier = modifier, navContent = navContent)
    }
}

@Composable
private fun CompactLayout(
    modifier: Modifier = Modifier,
    navContent: @Composable (Modifier) -> Unit,
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = { Box(Modifier.width(24.dp).padding(4.dp)) },
                        label = { Text(label) },
                    )
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
    navContent: @Composable (Modifier) -> Unit,
) {
    Row(modifier = modifier) {
        // Growth hook: NavigationRail placeholder — not implemented V1
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
        ) {
            navItems.forEach { label ->
                Box(Modifier.width(80.dp).padding(vertical = 8.dp, horizontal = 16.dp)) {
                    Text(label)
                }
            }
        }
        navContent(Modifier.weight(1f).fillMaxHeight())
    }
}
