package com.floatercapture.data.db

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_DOWNLOAD_NOTIFY = booleanPreferencesKey("download_notify")
        val KEY_MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch")

        private const val DEFAULT_WIFI_ONLY = false
        private const val DEFAULT_DOWNLOAD_NOTIFY = true
        private const val DEFAULT_MAX_CONCURRENT = 3
        private const val DEFAULT_DARK_MODE = false
        private const val DEFAULT_FIRST_LAUNCH = true
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WIFI_ONLY] ?: DEFAULT_WIFI_ONLY
    }

    val downloadNotify: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_NOTIFY] ?: DEFAULT_DOWNLOAD_NOTIFY
    }

    val maxConcurrent: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAX_CONCURRENT] ?: DEFAULT_MAX_CONCURRENT
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: DEFAULT_DARK_MODE
    }

    val firstLaunch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH] ?: DEFAULT_FIRST_LAUNCH
    }

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WIFI_ONLY] = value
        }
    }

    suspend fun setDownloadNotify(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_NOTIFY] = value
        }
    }

    suspend fun setMaxConcurrent(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MAX_CONCURRENT] = value
        }
    }

    suspend fun setDarkMode(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = value
        }
    }

    suspend fun setFirstLaunch(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH] = value
        }
    }
}
