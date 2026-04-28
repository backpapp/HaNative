package com.backpapp.hanative.di

import com.backpapp.hanative.platform.AppLifecycleObserver
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun appLifecycleObserverModule(): Module = module {
    single { AppLifecycleObserver() }
}
