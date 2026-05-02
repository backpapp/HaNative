package com.backpapp.hanative.di

import com.backpapp.hanative.domain.usecase.AddCardUseCase
import com.backpapp.hanative.domain.usecase.CallServiceUseCase
import com.backpapp.hanative.domain.usecase.DeleteDashboardUseCase
import com.backpapp.hanative.domain.usecase.GetActiveDashboardIdUseCase
import com.backpapp.hanative.domain.usecase.GetDashboardsUseCase
import com.backpapp.hanative.domain.usecase.GetSortedEntitiesUseCase
import com.backpapp.hanative.domain.usecase.ObserveConnectionStateUseCase
import com.backpapp.hanative.domain.usecase.ObserveEntityStateUseCase
import com.backpapp.hanative.domain.usecase.ObserveLastWebSocketMessageUseCase
import com.backpapp.hanative.domain.usecase.RemoveCardUseCase
import com.backpapp.hanative.domain.usecase.RenameDashboardUseCase
import com.backpapp.hanative.domain.usecase.ReorderCardsUseCase
import com.backpapp.hanative.domain.usecase.SaveDashboardUseCase
import com.backpapp.hanative.domain.usecase.SetActiveDashboardIdUseCase
import com.backpapp.hanative.domain.util.IdGenerator
import com.backpapp.hanative.domain.util.UuidIdGenerator
import org.koin.dsl.module

val domainModule = module {
    factory { ObserveEntityStateUseCase(get()) }
    factory { CallServiceUseCase(get()) }
    factory { GetDashboardsUseCase(get()) }
    factory { SaveDashboardUseCase(get()) }
    factory { DeleteDashboardUseCase(get()) }
    factory { RenameDashboardUseCase(get()) }
    factory { GetActiveDashboardIdUseCase(get()) }
    factory { SetActiveDashboardIdUseCase(get()) }
    factory { AddCardUseCase(get()) }
    factory { RemoveCardUseCase(get()) }
    factory { ReorderCardsUseCase(get()) }
    factory { GetSortedEntitiesUseCase(get()) }
    factory {
        ObserveConnectionStateUseCase(
            get<com.backpapp.hanative.data.remote.ServerManager>().connectionState,
        )
    }
    factory {
        ObserveLastWebSocketMessageUseCase(
            get<com.backpapp.hanative.domain.repository.HaWebSocketClient>().lastMessageEpochMs,
        )
    }
    factory<IdGenerator> { UuidIdGenerator() }
}
