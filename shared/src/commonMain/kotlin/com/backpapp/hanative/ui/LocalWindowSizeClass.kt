package com.backpapp.hanative.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

val LocalWindowSizeClass: ProvidableCompositionLocal<WindowSizeClass> =
    staticCompositionLocalOf { WindowSizeClass.COMPACT }
