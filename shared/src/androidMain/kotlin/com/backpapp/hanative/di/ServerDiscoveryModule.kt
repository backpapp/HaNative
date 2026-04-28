package com.backpapp.hanative.di

import com.backpapp.hanative.platform.AndroidServerDiscovery
import com.backpapp.hanative.platform.ServerDiscovery
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun serverDiscoveryModule(): Module = module {
    single<ServerDiscovery> { AndroidServerDiscovery(androidContext()) }
}
