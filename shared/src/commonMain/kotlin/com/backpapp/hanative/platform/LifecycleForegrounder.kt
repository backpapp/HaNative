package com.backpapp.hanative.platform

// Test-friendly seam over `AppLifecycleObserver`. Production code receives the platform
// `AppLifecycleObserver` (which implements this interface); tests inject a no-op fake
// to avoid needing Robolectric / a UIApplication for unit tests of consumers that only
// care about the foreground signal as a callback.
interface LifecycleForegrounder {
    fun onForeground(callback: () -> Unit)
}
