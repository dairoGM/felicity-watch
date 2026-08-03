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
        PushNotificationEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertRuleDao(): AlertRuleDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun powerReadingDao(): PowerReadingDao
    abstract fun pushNotificationDao(): PushNotificationDao

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
            )
        )
    }
}
