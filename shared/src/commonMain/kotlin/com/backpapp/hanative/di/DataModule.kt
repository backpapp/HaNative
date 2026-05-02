package com.backpapp.hanative.di

import com.backpapp.hanative.data.remote.AuthenticationRepositoryImpl
import com.backpapp.hanative.data.remote.HaReconnectManager
import com.backpapp.hanative.data.remote.HaUrlRepository
import com.backpapp.hanative.data.remote.KtorHaWebSocketClient
import com.backpapp.hanative.data.remote.ServerManager
import com.backpapp.hanative.data.remote.SessionRepository
import com.backpapp.hanative.data.repository.ActiveDashboardRepositoryImpl
import com.backpapp.hanative.data.repository.DashboardRepositoryImpl
import com.backpapp.hanative.data.repository.EntityRepositoryImpl
import com.backpapp.hanative.domain.repository.ActiveDashboardRepository
import com.backpapp.hanative.domain.repository.DashboardRepository
import com.backpapp.hanative.domain.repository.EntityRepository
import com.backpapp.hanative.domain.repository.HaWebSocketClient
import com.backpapp.hanative.platform.AppLifecycleObserver
import com.backpapp.hanative.platform.OAuthCallbackBus
import com.backpapp.hanative.ui.dashboard.DashboardChrome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.bind
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
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    single { HaUrlRepository(get(), get()) }
    single { KtorHaWebSocketClient(get()) }
    single<HaWebSocketClient> { get<KtorHaWebSocketClient>() }
    single { AuthenticationRepositoryImpl(get()) }
    single { HaReconnectManager() }
    single {
        ServerManager(
            webSocketClient = get(),
            authRepository = get(),
            lifecycleObserver = get<AppLifecycleObserver>(),
            reconnectManager = get(),
            entityRepository = get(),
            scope = get(),
        )
    }
    single { OAuthCallbackBus() }
    single { DashboardChrome() }
    single { SessionRepository(get(), get(), get(), get()) }
    single { EntityRepositoryImpl(get(), get(), get()) } bind EntityRepository::class
    single { DashboardRepositoryImpl(get()) } bind DashboardRepository::class
    single { ActiveDashboardRepositoryImpl(get()) } bind ActiveDashboardRepository::class
}

expect fun hapticEngineModule(): Module
expect fun credentialStoreModule(): Module
expect fun settingsDataStoreModule(): Module
expect fun serverDiscoveryModule(): Module
expect fun appLifecycleObserverModule(): Module
expect fun httpClientModule(): Module
expect fun oauthLauncherModule(): Module
