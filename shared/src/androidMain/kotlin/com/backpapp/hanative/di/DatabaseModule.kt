package com.backpapp.hanative.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.backpapp.hanative.Entity_state
import com.backpapp.hanative.HaNativeDatabase
import com.backpapp.hanative.data.local.adapter.InstantColumnAdapter
import org.koin.dsl.module

actual fun databaseModule() = module {
    single {
        AndroidSqliteDriver(
            schema = HaNativeDatabase.Schema,
            context = get(),
            name = "ha_native.db",
        )
    }
    single {
        HaNativeDatabase(
            driver = get(),
            entity_stateAdapter = Entity_state.Adapter(
                last_updatedAdapter = InstantColumnAdapter,
            ),
        )
    }
}
