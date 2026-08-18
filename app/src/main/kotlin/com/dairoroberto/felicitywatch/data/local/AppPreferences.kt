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

    /** Avisar (vibración + sonido) cuando se detecta que un equipo del
     * inventario se encendió o apagó. Apagado por defecto: el aviso depende
     * de una detección heurística y de cuándo Felicity publica el dato, así
     * que conviene que el usuario lo active a conciencia. */
    val applianceAlertsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_APPLIANCE_ALERTS_ENABLED] ?: false }

    suspend fun setApplianceAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APPLIANCE_ALERTS_ENABLED] = enabled }
    }

    /** Salto mínimo de consumo (W) para considerar que algo se encendió o
     * apagó. Configurable porque el valor útil depende de la casa: subirlo
     * evita avisos por equipos chicos y por el compresor de la nevera. */
    val applianceAlertThresholdWatts: Flow<Int> = context.dataStore.data
        .map { it[KEY_APPLIANCE_ALERT_THRESHOLD_WATTS] ?: DEFAULT_APPLIANCE_ALERT_THRESHOLD_WATTS }

    suspend fun setApplianceAlertThresholdWatts(watts: Int) {
        context.dataStore.edit { it[KEY_APPLIANCE_ALERT_THRESHOLD_WATTS] = watts }
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

        /** Presets ofrecidos en Ajustes; fuera de estos el usuario escribe el
         * intervalo que quiera (ver MIN/MAX). */
        val POLLING_INTERVAL_PRESETS = listOf(5, 10, 15, 30)

        /** Mínimo de 5 s: por debajo se consulta a Felicity varias veces por
         * cada dato nuevo que el inversor publica, gastando batería y datos
         * sin ganar información. Máximo de 1 hora. */
        const val MIN_POLLING_INTERVAL_SECONDS = 5
        const val MAX_POLLING_INTERVAL_SECONDS = 3_600

        private val KEY_APPLIANCE_ALERTS_ENABLED = booleanPreferencesKey("appliance_alerts_enabled")
        private val KEY_APPLIANCE_ALERT_THRESHOLD_WATTS =
            intPreferencesKey("appliance_alert_threshold_watts")

        /** Mismo umbral que usa la bitácora de Actividad, para que lo que
         * avisa y lo que se lista coincidan mientras no se cambie. */
        const val DEFAULT_APPLIANCE_ALERT_THRESHOLD_WATTS = 400

        /** Presets ofrecidos en Ajustes; fuera de estos el usuario escribe el
         * valor que quiera (ver MIN/MAX). */
        val APPLIANCE_ALERT_THRESHOLD_PRESETS = listOf(400, 500, 800)

        const val MIN_APPLIANCE_ALERT_THRESHOLD_WATTS = 50
        const val MAX_APPLIANCE_ALERT_THRESHOLD_WATTS = 10_000
    }
}
