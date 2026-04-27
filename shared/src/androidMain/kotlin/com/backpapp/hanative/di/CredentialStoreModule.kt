package com.backpapp.hanative.di

import com.backpapp.hanative.platform.AndroidCredentialStore
import com.backpapp.hanative.platform.CredentialStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun credentialStoreModule(): Module = module {
    single<CredentialStore> { AndroidCredentialStore(androidContext()) }
}
