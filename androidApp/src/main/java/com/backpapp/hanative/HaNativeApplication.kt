package com.backpapp.hanative

import android.app.Application
import com.backpapp.hanative.di.dataModule
import com.backpapp.hanative.di.domainModule
import com.backpapp.hanative.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class HaNativeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@HaNativeApplication)
                modules(dataModule, domainModule, presentationModule)
            }
        }
    }
}
