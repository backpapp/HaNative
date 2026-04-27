package com.backpapp.hanative.di

import org.koin.core.module.Module
import org.koin.dsl.module

val dataModule = module {
    includes(hapticEngineModule())
}

expect fun hapticEngineModule(): Module
