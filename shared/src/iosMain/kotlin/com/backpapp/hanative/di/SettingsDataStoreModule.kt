package com.backpapp.hanative.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSHomeDirectory

actual fun settingsDataStoreModule(): Module = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath {
            (NSHomeDirectory() + "/Library/Application Support/ha_settings.preferences_pb")
                .toPath()
        }
    }
}
