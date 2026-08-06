package com.dairoroberto.felicitywatch.data.local

import androidx.room.TypeConverter
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.domain.model.ComparisonOperator
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromAlertRuleType(value: AlertRuleType): String = value.name

    @TypeConverter
    fun toAlertRuleType(value: String): AlertRuleType = AlertRuleType.valueOf(value)

    /** Sobrecarga nullable — PushNotificationEntity.ruleType es null para
     * pushes de prueba manual o notificaciones guardadas antes de este
     * campo, a diferencia de AlertRuleEntity/AlertEventEntity que siempre
     * tienen un tipo de regla concreto. */
    @TypeConverter
    fun fromNullableAlertRuleType(value: AlertRuleType?): String? = value?.name

    @TypeConverter
    fun toNullableAlertRuleType(value: String?): AlertRuleType? = value?.let { AlertRuleType.valueOf(it) }

    @TypeConverter
    fun fromComparisonOperator(value: ComparisonOperator?): String? = value?.name

    @TypeConverter
    fun toComparisonOperator(value: String?): ComparisonOperator? = value?.let { ComparisonOperator.valueOf(it) }
}
