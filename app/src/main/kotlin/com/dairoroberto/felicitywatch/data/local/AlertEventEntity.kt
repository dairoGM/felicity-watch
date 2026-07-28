package com.dairoroberto.felicitywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import java.time.Instant

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleType: AlertRuleType,
    val triggeredAt: Instant,
    val message: String,
    val voiceSent: Boolean,
    val pushSent: Boolean,
    val whatsappSent: Boolean,
    val whatsappError: String?
)
