package com.backpapp.hanative

import com.backpapp.hanative.di.dataModule
import com.backpapp.hanative.di.domainModule
import com.backpapp.hanative.di.presentationModule
import com.backpapp.hanative.platform.OAuthCallbackBus
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

private var koinInitialized = false
private var koinApp: KoinApplication? = null

fun initKoin() {
    if (!koinInitialized) {
        koinInitialized = true
        koinApp = startKoin {
            modules(dataModule, domainModule, presentationModule)
        }
    }
}

// GlobalContext is JVM-only in koin-core 4.x — expose Koin instance directly for iosMain
val appKoin: Koin get() = koinApp?.koin ?: error("Koin not initialized — call initKoin() first")

/**
 * Story 3.5 — Swift-side hook for `onOpenURL` to forward OAuth callback codes
 * into the shared [OAuthCallbackBus]. Called from `iOSApp.swift`.
 *
 * Never log [code].
 */
fun handleOAuthCallback(code: String?) {
    appKoin.get<OAuthCallbackBus>().emit(code)
}
