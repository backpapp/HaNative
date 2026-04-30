package com.backpapp.hanative.di

import com.backpapp.hanative.data.remote.AuthenticationRepositoryImpl
import com.backpapp.hanative.data.remote.HaReconnectManager
import com.backpapp.hanative.data.remote.HaUrlRepository
import com.backpapp.hanative.data.remote.KtorHaWebSocketClient
import com.backpapp.hanative.data.remote.ServerManager
import com.backpapp.hanative.domain.repository.HaWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.koin.core.module.Module
import org.koin.dsl.module

val dataModule = module {
    includes(
        hapticEngineModule(),
        credentialStoreModule(),
        settingsDataStoreModule(),
        serverDiscoveryModule(),
        appLifecycleObserverModule(),
        httpClientModule(),
        serverManagerModule(),
    )
}

fun serverManagerModule(): Module = module {
    single<CoroutineScope> { MainScope() }
    single { HaUrlRepository(get(), get()) }
    single { KtorHaWebSocketClient(get()) }
    single<HaWebSocketClient> { get<KtorHaWebSocketClient>() }
    single { AuthenticationRepositoryImpl(get()) }
    single { HaReconnectManager() }
    single {
        ServerManager(
            webSocketClient = get(),
            authRepository = get(),
            lifecycleObserver = get(),
            reconnectManager = get(),
            scope = get(),
        )
    }
}

expect fun hapticEngineModule(): Module
expect fun credentialStoreModule(): Module
expect fun settingsDataStoreModule(): Module
expect fun serverDiscoveryModule(): Module
expect fun appLifecycleObserverModule(): Module
expect fun httpClientModule(): Module
