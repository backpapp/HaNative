package com.backpapp.hanative.di

import com.backpapp.hanative.platform.IosServerDiscovery
import com.backpapp.hanative.platform.ServerDiscovery
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun serverDiscoveryModule(): Module = module {
    single<ServerDiscovery> { IosServerDiscovery() }
}
