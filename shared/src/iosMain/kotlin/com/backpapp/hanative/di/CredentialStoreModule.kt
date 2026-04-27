package com.backpapp.hanative.di

import com.backpapp.hanative.platform.CredentialStore
import com.backpapp.hanative.platform.IosCredentialStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun credentialStoreModule(): Module = module {
    single<CredentialStore> { IosCredentialStore() }
}
