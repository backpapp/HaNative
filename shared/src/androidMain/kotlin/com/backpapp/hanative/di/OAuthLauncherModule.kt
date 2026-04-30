package com.backpapp.hanative.di

import com.backpapp.hanative.platform.AndroidOAuthLauncher
import com.backpapp.hanative.platform.OAuthLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun oauthLauncherModule(): Module = module {
    single<OAuthLauncher> { AndroidOAuthLauncher(androidContext()) }
}
