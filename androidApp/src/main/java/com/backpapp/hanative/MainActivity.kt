package com.backpapp.hanative

import android.content.Intent
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
import com.backpapp.hanative.platform.OAuthCallbackBus
import com.backpapp.hanative.ui.LocalWindowSizeClass
import com.backpapp.hanative.ui.WindowSizeClass
import com.backpapp.hanative.ui.theme.HaNativeTheme
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    private val oauthCallbackBus: OAuthCallbackBus by lazy {
        GlobalContext.get().get()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initial intent may carry OAuth deep link (cold-start case)
        handleOAuthIntent(intent)

        setContent {
            val m3SizeClass = calculateWindowSizeClass(this)
            val windowSizeClass = remember(m3SizeClass.widthSizeClass) {
                when (m3SizeClass.widthSizeClass) {
                    WindowWidthSizeClass.Compact -> WindowSizeClass.COMPACT
                    WindowWidthSizeClass.Medium -> WindowSizeClass.MEDIUM
                    WindowWidthSizeClass.Expanded -> WindowSizeClass.EXPANDED
                    else -> WindowSizeClass.COMPACT
                }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask launch mode delivers OAuth callback re-entries here.
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    /**
     * Story 3.5 — extract `code` from `hanative://auth-callback?code=...`
     * and forward to AuthViewModel via [OAuthCallbackBus]. Never log the code.
     */
    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "hanative" || data.host != "auth-callback") return
        val code = data.getQueryParameter("code")
        oauthCallbackBus.emit(code)
    }
}
