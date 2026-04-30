package com.backpapp.hanative.di

import com.backpapp.hanative.domain.usecase.CallServiceUseCase
import com.backpapp.hanative.domain.usecase.ObserveEntityStateUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { ObserveEntityStateUseCase(get()) }
    factory { CallServiceUseCase(get()) }
}
