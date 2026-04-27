package com.backpapp.hanative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.backpapp.hanative.navigation.HaNativeNavHost
import com.backpapp.hanative.platform.HapticEngine
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.LocalWindowSizeClass
import com.backpapp.hanative.ui.WindowSizeClass
import com.backpapp.hanative.ui.theme.HaNativeTheme
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val m3SizeClass = calculateWindowSizeClass(this)
            val windowSizeClass = when (m3SizeClass.widthSizeClass) {
                WindowWidthSizeClass.Compact -> WindowSizeClass.COMPACT
                WindowWidthSizeClass.Medium -> WindowSizeClass.MEDIUM
                WindowWidthSizeClass.Expanded -> WindowSizeClass.EXPANDED
                else -> WindowSizeClass.COMPACT
            }
            val hapticEngine = remember { GlobalContext.get().get<HapticEngine>() }
            HaNativeTheme {
                CompositionLocalProvider(
                    LocalWindowSizeClass provides windowSizeClass,
                    LocalHapticEngine provides hapticEngine,
                ) {
                    HaNativeNavHost()
                }
            }
        }
    }
}
