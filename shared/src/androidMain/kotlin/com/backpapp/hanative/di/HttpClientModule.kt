package com.backpapp.hanative.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun httpClientModule(): Module = module {
    single {
        HttpClient(CIO) {
            install(WebSockets)
        }
    }
}
