package com.backpapp.hanative

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    HaNativePlaceholderScreen()
}
