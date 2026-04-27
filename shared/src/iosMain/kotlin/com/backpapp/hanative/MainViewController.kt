package com.backpapp.hanative

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.backpapp.hanative.platform.HapticEngine
import com.backpapp.hanative.platform.LocalHapticEngine
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val hapticEngine = remember { appKoin.get<HapticEngine>() }
    CompositionLocalProvider(LocalHapticEngine provides hapticEngine) {
        HaNativePlaceholderScreen()
    }
}
