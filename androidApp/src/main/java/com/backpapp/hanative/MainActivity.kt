package com.backpapp.hanative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.backpapp.hanative.navigation.HaNativeNavHost
import com.backpapp.hanative.ui.LocalWindowSizeClass
import com.backpapp.hanative.ui.WindowSizeClass

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val configuration = LocalConfiguration.current
            val windowSizeClass = remember(configuration.screenWidthDp) {
                when {
                    configuration.screenWidthDp < 600 -> WindowSizeClass.COMPACT
                    configuration.screenWidthDp < 840 -> WindowSizeClass.MEDIUM
                    else -> WindowSizeClass.EXPANDED
                }
            }
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                HaNativeNavHost()
            }
        }
    }
}
