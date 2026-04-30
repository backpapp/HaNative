package com.backpapp.hanative

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.backpapp.hanative.navigation.HaNativeNavHost
import com.backpapp.hanative.platform.HapticEngine
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.LocalWindowSizeClass
import com.backpapp.hanative.ui.WindowSizeClass
import com.backpapp.hanative.ui.theme.HaNativeTheme
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val hapticEngine = remember { appKoin.get<HapticEngine>() }
    HaNativeTheme {
        CompositionLocalProvider(
            LocalWindowSizeClass provides WindowSizeClass.COMPACT,
            LocalHapticEngine provides hapticEngine,
        ) {
            HaNativeNavHost()
        }
    }
}
