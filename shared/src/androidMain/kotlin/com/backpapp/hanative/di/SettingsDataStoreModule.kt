package com.backpapp.hanative.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun settingsDataStoreModule(): Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath {
            androidContext()
                .filesDir
                .resolve("ha_settings.preferences_pb")
                .absolutePath
                .toPath()
        }
    }
}
