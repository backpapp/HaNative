package com.backpapp.hanative.di

import com.backpapp.hanative.platform.AndroidHapticEngine
import com.backpapp.hanative.platform.HapticEngine
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun hapticEngineModule(): Module = module {
    single<HapticEngine> { AndroidHapticEngine(androidContext()) }
}
