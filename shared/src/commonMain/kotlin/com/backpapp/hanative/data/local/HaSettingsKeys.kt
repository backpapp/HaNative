package com.backpapp.hanative.data.local

import androidx.datastore.preferences.core.stringPreferencesKey

object HaSettingsKeys {
    val HA_URL = stringPreferencesKey("ha_url")
    val ACTIVE_DASHBOARD_ID = stringPreferencesKey("active_dashboard_id")
}
