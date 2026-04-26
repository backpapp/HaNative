package com.backpapp.hanative.platform

expect class AppLifecycleObserver() {
    fun onForeground(callback: () -> Unit)
}
