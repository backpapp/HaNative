package com.backpapp.hanative.di

import com.backpapp.hanative.domain.usecase.AddCardUseCase
import com.backpapp.hanative.domain.usecase.CallServiceUseCase
import com.backpapp.hanative.domain.usecase.DeleteDashboardUseCase
import com.backpapp.hanative.domain.usecase.GetDashboardsUseCase
import com.backpapp.hanative.domain.usecase.GetSortedEntitiesUseCase
import com.backpapp.hanative.domain.usecase.ObserveEntityStateUseCase
import com.backpapp.hanative.domain.usecase.RemoveCardUseCase
import com.backpapp.hanative.domain.usecase.ReorderCardsUseCase
import com.backpapp.hanative.domain.usecase.SaveDashboardUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { ObserveEntityStateUseCase(get()) }
    factory { CallServiceUseCase(get()) }
    factory { GetDashboardsUseCase(get()) }
    factory { SaveDashboardUseCase(get()) }
    factory { DeleteDashboardUseCase(get()) }
    factory { AddCardUseCase(get()) }
    factory { RemoveCardUseCase(get()) }
    factory { ReorderCardsUseCase(get()) }
    factory { GetSortedEntitiesUseCase(get()) }
}
