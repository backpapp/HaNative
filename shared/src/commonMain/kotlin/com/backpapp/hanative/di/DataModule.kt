package com.backpapp.hanative.di

import com.backpapp.hanative.data.remote.AuthenticationRepositoryImpl
import com.backpapp.hanative.data.remote.HaReconnectManager
import com.backpapp.hanative.data.remote.HaUrlRepository
import com.backpapp.hanative.data.remote.KtorHaWebSocketClient
import com.backpapp.hanative.data.remote.ServerManager
import com.backpapp.hanative.data.remote.SessionRepository
import com.backpapp.hanative.data.repository.EntityRepositoryImpl
import com.backpapp.hanative.domain.repository.EntityRepository
import com.backpapp.hanative.domain.repository.HaWebSocketClient
import com.backpapp.hanative.platform.OAuthCallbackBus
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
        oauthLauncherModule(),
        databaseModule(),
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
    single { OAuthCallbackBus() }
    single { SessionRepository(get(), get(), get(), get()) }
    single { EntityRepositoryImpl(get(), get(), get()) }
    single<EntityRepository> { get<EntityRepositoryImpl>() }
}

expect fun hapticEngineModule(): Module
expect fun credentialStoreModule(): Module
expect fun settingsDataStoreModule(): Module
expect fun serverDiscoveryModule(): Module
expect fun appLifecycleObserverModule(): Module
expect fun httpClientModule(): Module
expect fun oauthLauncherModule(): Module
