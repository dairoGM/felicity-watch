package com.dairoroberto.felicitywatch.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "felicity_watch_prefs")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    val lastReadingEpochMillis: Flow<Long?> = context.dataStore.data.map { it[KEY_LAST_READING_MILLIS] }
    val lastGridStateName: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_GRID_STATE] }

    /** Preferencia de tema: null = seguir al sistema, true = oscuro forzado, false = claro forzado. */
    val darkModeEnabled: Flow<Boolean?> = context.dataStore.data.map { it[KEY_DARK_MODE] }

    /** Cada cuántos segundos se consulta a Felicity (Panel/servicio). Editable en Ajustes. */
    val pollingIntervalSeconds: Flow<Int> = context.dataStore.data.map { it[KEY_POLLING_INTERVAL_SECONDS] ?: DEFAULT_POLLING_INTERVAL_SECONDS }

    suspend fun setPollingIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[KEY_POLLING_INTERVAL_SECONDS] = seconds }
    }

    suspend fun setLastReadingNow(epochMillis: Long) {
        context.dataStore.edit { it[KEY_LAST_READING_MILLIS] = epochMillis }
    }

    suspend fun setLastGridState(name: String) {
        context.dataStore.edit { it[KEY_LAST_GRID_STATE] = name }
    }

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DARK_MODE] = enabled }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_LAST_READING_MILLIS = longPreferencesKey("last_reading_epoch_millis")
        private val KEY_LAST_GRID_STATE = stringPreferencesKey("last_grid_state")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        private val KEY_POLLING_INTERVAL_SECONDS = intPreferencesKey("polling_interval_seconds")
        const val DEFAULT_POLLING_INTERVAL_SECONDS = 30
    }
}
