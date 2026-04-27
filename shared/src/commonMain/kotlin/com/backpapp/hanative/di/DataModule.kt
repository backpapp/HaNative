package com.backpapp.hanative.di

import org.koin.core.module.Module
import org.koin.dsl.module

val dataModule = module {
    includes(hapticEngineModule(), credentialStoreModule(), settingsDataStoreModule())
}

expect fun hapticEngineModule(): Module
expect fun credentialStoreModule(): Module
expect fun settingsDataStoreModule(): Module
