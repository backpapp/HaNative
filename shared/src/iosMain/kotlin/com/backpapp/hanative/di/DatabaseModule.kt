package com.backpapp.hanative.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.backpapp.hanative.Entity_state
import com.backpapp.hanative.HaNativeDatabase
import com.backpapp.hanative.data.local.adapter.InstantColumnAdapter
import org.koin.dsl.module

actual fun databaseModule() = module {
    single<SqlDriver> {
        NativeSqliteDriver(
            schema = HaNativeDatabase.Schema,
            name = "ha_native.db",
        )
    }
    single {
        HaNativeDatabase(
            driver = get(),
            entity_stateAdapter = Entity_state.Adapter(
                last_changedAdapter = InstantColumnAdapter,
                last_updatedAdapter = InstantColumnAdapter,
            ),
        )
    }
}
