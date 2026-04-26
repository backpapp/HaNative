package com.backpapp.hanative.platform

// Story 3.2 provides full implementation using ProcessLifecycleOwner ON_START.
actual class AppLifecycleObserver actual constructor() {
    actual fun onForeground(callback: () -> Unit) {
        // TODO Story 3.2: wire ProcessLifecycleOwner.get().lifecycle.addObserver(...)
    }
}
