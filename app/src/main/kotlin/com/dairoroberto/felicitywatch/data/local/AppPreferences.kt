package com.dairoroberto.felicitywatch.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
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

    suspend fun setLastReadingNow(epochMillis: Long) {
        context.dataStore.edit { it[KEY_LAST_READING_MILLIS] = epochMillis }
    }

    suspend fun setLastGridState(name: String) {
        context.dataStore.edit { it[KEY_LAST_GRID_STATE] = name }
    }

    companion object {
        private val KEY_LAST_READING_MILLIS = longPreferencesKey("last_reading_epoch_millis")
        private val KEY_LAST_GRID_STATE = stringPreferencesKey("last_grid_state")
    }
}
