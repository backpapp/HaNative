package com.backpapp.hanative.di

import com.backpapp.hanative.platform.HapticEngine
import com.backpapp.hanative.platform.IosHapticEngine
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun hapticEngineModule(): Module = module {
    single<HapticEngine> { IosHapticEngine() }
}
