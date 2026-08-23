package com.dairoroberto.felicitywatch.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.domain.model.ComparisonOperator

@Database(
    entities = [
        PowerReadingEntity::class,
        AlertRuleEntity::class,
        AlertEventEntity::class,
        PushNotificationEntity::class,
        ApplianceEntity::class,
        DismissedApplianceEventEntity::class,
        ImportedBackupEntity::class,
        ConfirmedApplianceEventEntity::class
    ],
    version = 15,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertRuleDao(): AlertRuleDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun powerReadingDao(): PowerReadingDao
    abstract fun pushNotificationDao(): PushNotificationDao
    abstract fun applianceDao(): ApplianceDao
    abstract fun dismissedApplianceEventDao(): DismissedApplianceEventDao
    abstract fun importedBackupDao(): ImportedBackupDao
    abstract fun confirmedApplianceEventDao(): ConfirmedApplianceEventDao

    companion object {
        const val DATABASE_NAME = "felicity_watch.db"

        /** Seed data calcada del prototipo validado (guía sección 3.2). */
        fun defaultAlertRules(): List<AlertRuleEntity> = listOf(
            AlertRuleEntity(
                type = AlertRuleType.GRID_OFFLINE,
                enabled = true,
                thresholdValue = 1.0,
                comparisonOperator = null,
                debounceSeconds = 5,
                channelVoiceEnabled = true,
                channelPushEnabled = true,
                channelWhatsappEnabled = true,
                messageTemplate = "Se ha perdido la corriente eléctrica de la calle"
            ),
            AlertRuleEntity(
                type = AlertRuleType.GRID_ONLINE,
                enabled = true,
                thresholdValue = 1.0,
                comparisonOperator = null,
                debounceSeconds = 5,
                channelVoiceEnabled = true,
                channelPushEnabled = true,
                channelWhatsappEnabled = true,
                messageTemplate = "Ha vuelto la corriente eléctrica de la calle"
            ),
            AlertRuleEntity(
                type = AlertRuleType.BATTERY_SOC_LOW,
                enabled = true,
                thresholdValue = 20.0,
                comparisonOperator = ComparisonOperator.LTE,
                debounceSeconds = 60,
                channelVoiceEnabled = false,
                channelPushEnabled = true,
                channelWhatsappEnabled = false,
                messageTemplate = "La batería está baja"
            ),
            AlertRuleEntity(
                type = AlertRuleType.BATTERY_SOC_HIGH,
                enabled = false,
                thresholdValue = 100.0,
                comparisonOperator = ComparisonOperator.GTE,
                debounceSeconds = 60,
                channelVoiceEnabled = false,
                channelPushEnabled = false,
                channelWhatsappEnabled = false,
                messageTemplate = "La batería está llena"
            ),
            // Inversor de 8k: 7kW es un margen de seguridad antes del límite
            // real del equipo, no un umbral arbitrario.
            AlertRuleEntity(
                type = AlertRuleType.LOAD_HIGH,
                enabled = true,
                thresholdValue = 7000.0,
                comparisonOperator = ComparisonOperator.GTE,
                debounceSeconds = 30,
                channelVoiceEnabled = true,
                channelPushEnabled = true,
                channelWhatsappEnabled = true,
                // El canal de voz no lee este texto (usa el tono de aviso
                // intenso) — este mensaje es el que ven push y WhatsApp.
                messageTemplate = "Consumo alto: se superó el umbral de potencia configurado"
            ),
            // Mismo umbral de horas que pone en rojo el anillo "AUTONOMÍA"
            // del Panel (ver estimateBatteryRuntimeHours) — solo aplica sin
            // corriente de red.
            AlertRuleEntity(
                type = AlertRuleType.BATTERY_AUTONOMY_LOW,
                enabled = true,
                thresholdValue = 2.0,
                comparisonOperator = ComparisonOperator.LTE,
                debounceSeconds = 30,
                channelVoiceEnabled = true,
                channelPushEnabled = true,
                channelWhatsappEnabled = true,
                // El canal de voz no lee este texto (usa el tono de aviso
                // intenso) — este mensaje es el que ven push y WhatsApp.
                messageTemplate = "Autonomía de batería baja: queda poco tiempo sin corriente de red"
            )
        )
    }
}
