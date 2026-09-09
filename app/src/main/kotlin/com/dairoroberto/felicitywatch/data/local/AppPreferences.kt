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


    /** Avisar cuando el voltaje cae por debajo de [lowVoltageThreshold].
     * Apagado por defecto: el umbral útil depende de la instalación, y sin
     * configurarlo un valor genérico avisaría de más o de menos. */
    val lowVoltageAlertEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_LOW_VOLTAGE_ALERT_ENABLED] ?: false }

    suspend fun setLowVoltageAlertEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LOW_VOLTAGE_ALERT_ENABLED] = enabled }
    }

    /** Voltaje por debajo del cual se considera bajo, en voltios. */
    val lowVoltageThreshold: Flow<Int> = context.dataStore.data
        .map { it[KEY_LOW_VOLTAGE_THRESHOLD] ?: DEFAULT_LOW_VOLTAGE_THRESHOLD }

    suspend fun setLowVoltageThreshold(volts: Int) {
        context.dataStore.edit { it[KEY_LOW_VOLTAGE_THRESHOLD] = volts }
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

    /** Franja horaria configurable del reporte "Franja horaria" (ej. consumo
     * nocturno 10pm-8am) — hora de inicio, 0-23. */
    val nightWindowStartHour: Flow<Int> = context.dataStore.data
        .map { it[KEY_NIGHT_WINDOW_START_HOUR] ?: DEFAULT_NIGHT_WINDOW_START_HOUR }

    suspend fun setNightWindowStartHour(hour: Int) {
        context.dataStore.edit { it[KEY_NIGHT_WINDOW_START_HOUR] = hour }
    }

    /** Hora de fin de la franja, 0-23 — puede ser menor que la de inicio
     * (cruza medianoche, ej. 22 → 8) o mayor (no cruza, ej. 9 → 17). */
    val nightWindowEndHour: Flow<Int> = context.dataStore.data
        .map { it[KEY_NIGHT_WINDOW_END_HOUR] ?: DEFAULT_NIGHT_WINDOW_END_HOUR }

    suspend fun setNightWindowEndHour(hour: Int) {
        context.dataStore.edit { it[KEY_NIGHT_WINDOW_END_HOUR] = hour }
    }

    /** Horario en que se espera generación solar real, para la alerta
     * "Generación PV perdida" — fuera de esta franja, PV en 0 es la noche
     * normal y no dispara nada. Configurable porque la hora real de
     * amanecer/anochecer depende de la instalación (sombras del terreno,
     * orientación de los paneles), y un valor fijo demasiado temprano
     * dispararía falsos positivos cada mañana antes de que el sol de verdad
     * llegue a los paneles. */
    val pvAlertWindowStartHour: Flow<Int> = context.dataStore.data
        .map { it[KEY_PV_ALERT_WINDOW_START_HOUR] ?: DEFAULT_PV_ALERT_WINDOW_START_HOUR }

    suspend fun setPvAlertWindowStartHour(hour: Int) {
        context.dataStore.edit { it[KEY_PV_ALERT_WINDOW_START_HOUR] = hour }
    }

    val pvAlertWindowEndHour: Flow<Int> = context.dataStore.data
        .map { it[KEY_PV_ALERT_WINDOW_END_HOUR] ?: DEFAULT_PV_ALERT_WINDOW_END_HOUR }

    suspend fun setPvAlertWindowEndHour(hour: Int) {
        context.dataStore.edit { it[KEY_PV_ALERT_WINDOW_END_HOUR] = hour }
    }

    companion object {
        private val KEY_LAST_READING_MILLIS = longPreferencesKey("last_reading_epoch_millis")
        private val KEY_LAST_GRID_STATE = stringPreferencesKey("last_grid_state")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        private val KEY_POLLING_INTERVAL_SECONDS = intPreferencesKey("polling_interval_seconds")
        const val DEFAULT_POLLING_INTERVAL_SECONDS = 30

        /** Presets ofrecidos en Ajustes; fuera de estos el usuario escribe el
         * intervalo que quiera (ver MIN/MAX). */
        // Cuatro presets + "Personalizado" caben en dos filas sin que el
        // ultimo chip quede solo en una linea. Se quito el de 5 s: por debajo
        // de la cadencia del inversor las consultas extra devuelven el mismo
        // dato (ver el aviso en Ajustes).
        val POLLING_INTERVAL_PRESETS = listOf(10, 30, 60)

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

        private val KEY_LOW_VOLTAGE_ALERT_ENABLED = booleanPreferencesKey("low_voltage_alert_enabled")
        private val KEY_LOW_VOLTAGE_THRESHOLD = intPreferencesKey("low_voltage_threshold")

        /** 100 V como punto de partida para una red de 110-120 V: por debajo
         * de eso los equipos ya sufren. El usuario lo ajusta a su instalación,
         * que puede ser de 220 V o tener un banco de otro voltaje nominal. */
        const val DEFAULT_LOW_VOLTAGE_THRESHOLD = 100
        // Tres presets + "Otro" caben en una sola linea. Con cuatro, el chip de
        // "Otro" saltaba de renglon y quedaba solo. Para 220 V esta el valor
        // personalizado, que muestra el numero en el propio chip.
        val LOW_VOLTAGE_THRESHOLD_PRESETS = listOf(100, 105, 110)
        const val MIN_LOW_VOLTAGE_THRESHOLD = 10
        const val MAX_LOW_VOLTAGE_THRESHOLD = 500

        private val KEY_NIGHT_WINDOW_START_HOUR = intPreferencesKey("night_window_start_hour")
        private val KEY_NIGHT_WINDOW_END_HOUR = intPreferencesKey("night_window_end_hour")
        const val DEFAULT_NIGHT_WINDOW_START_HOUR = 22
        const val DEFAULT_NIGHT_WINDOW_END_HOUR = 8

        private val KEY_PV_ALERT_WINDOW_START_HOUR = intPreferencesKey("pv_alert_window_start_hour")
        private val KEY_PV_ALERT_WINDOW_END_HOUR = intPreferencesKey("pv_alert_window_end_hour")
        const val DEFAULT_PV_ALERT_WINDOW_START_HOUR = 7
        const val DEFAULT_PV_ALERT_WINDOW_END_HOUR = 18
    }
}
