package com.backpapp.hanative.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.backpapp.hanative.Entity_state
import com.backpapp.hanative.HaNativeDatabase
import com.backpapp.hanative.data.local.adapter.InstantColumnAdapter
import org.koin.dsl.module

actual fun databaseModule() = module {
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = HaNativeDatabase.Schema,
            context = get(),
            name = "ha_native.db",
        ).also { driver ->
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        }
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
