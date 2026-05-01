package com.backpapp.hanative.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.backpapp.hanative.data.local.HaSettingsKeys
import com.backpapp.hanative.domain.repository.ActiveDashboardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ActiveDashboardRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : ActiveDashboardRepository {

    override fun observeActiveDashboardId(): Flow<String?> =
        dataStore.data.map { it[HaSettingsKeys.ACTIVE_DASHBOARD_ID] }

    override suspend fun setActiveDashboardId(dashboardId: String?): Result<Unit> = try {
        dataStore.edit { prefs ->
            if (dashboardId == null) prefs.remove(HaSettingsKeys.ACTIVE_DASHBOARD_ID)
            else prefs[HaSettingsKeys.ACTIVE_DASHBOARD_ID] = dashboardId
        }
        Result.success(Unit)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
