package com.backpapp.hanative.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.backpapp.hanative.navigation.HaNativeNavHost
import com.backpapp.hanative.platform.HapticEngine
import com.backpapp.hanative.platform.HapticPattern
import com.backpapp.hanative.platform.LocalHapticEngine
import com.backpapp.hanative.ui.LocalWindowSizeClass
import com.backpapp.hanative.ui.WindowSizeClass
import com.backpapp.hanative.ui.theme.HaNativeTheme

private object PreviewHapticEngine : HapticEngine {
    override fun fire(pattern: HapticPattern) = Unit
}

@Preview(name = "COMPACT — phone", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun NavHostCompactPreview() {
    HaNativeTheme {
        CompositionLocalProvider(
            LocalWindowSizeClass provides WindowSizeClass.COMPACT,
            LocalHapticEngine provides PreviewHapticEngine,
        ) {
            HaNativeNavHost()
        }
    }
}

@Preview(name = "EXPANDED — tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun NavHostExpandedPreview() {
    HaNativeTheme {
        CompositionLocalProvider(
            LocalWindowSizeClass provides WindowSizeClass.EXPANDED,
            LocalHapticEngine provides PreviewHapticEngine,
        ) {
            HaNativeNavHost()
        }
    }
}
