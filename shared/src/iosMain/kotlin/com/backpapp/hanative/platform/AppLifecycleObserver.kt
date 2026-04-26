package com.backpapp.hanative.platform

// Story 3.2 provides full implementation using NSNotificationCenter didBecomeActiveNotification.
actual class AppLifecycleObserver actual constructor() {
    actual fun onForeground(callback: () -> Unit) {
        // TODO Story 3.2: wire NSNotificationCenter.defaultCenter().addObserverForName(
        //   UIApplicationDidBecomeActiveNotification, ...)
    }
}
