package com.backpapp.hanative

import com.backpapp.hanative.di.dataModule
import com.backpapp.hanative.di.domainModule
import com.backpapp.hanative.di.presentationModule
import org.koin.core.context.startKoin

private var koinInitialized = false

fun initKoin() {
    if (!koinInitialized) {
        koinInitialized = true
        startKoin {
            modules(dataModule, domainModule, presentationModule)
        }
    }
}
