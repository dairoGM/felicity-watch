package com.dairoroberto.felicitywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.domain.model.ComparisonOperator

@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AlertRuleType,
    val enabled: Boolean,
    val thresholdValue: Double?,
    val comparisonOperator: ComparisonOperator?,
    val debounceSeconds: Int,
    val channelVoiceEnabled: Boolean,
    val channelPushEnabled: Boolean,
    val channelWhatsappEnabled: Boolean,
    val messageTemplate: String
)
