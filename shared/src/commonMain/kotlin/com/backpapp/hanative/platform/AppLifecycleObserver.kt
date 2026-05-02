package com.backpapp.hanative.platform

expect class AppLifecycleObserver() : LifecycleForegrounder {
    override fun onForeground(callback: () -> Unit)
}
