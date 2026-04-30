package com.backpapp.hanative.platform

/**
 * Launches an OAuth authorize URL in the platform's system browser.
 *
 * Callback is delivered asynchronously via deep-link to [OAuthCallbackBus] —
 * this launcher is fire-and-forget. Implementations differ per platform:
 *  - Android: `Intent.ACTION_VIEW` → default browser / Chrome Custom Tabs
 *  - iOS: `UIApplication.openURL` → Safari
 *
 * NEVER use a WebView — defeats HA SSO + biometric session.
 */
interface OAuthLauncher {
    fun launch(authorizeUrl: String)
}
