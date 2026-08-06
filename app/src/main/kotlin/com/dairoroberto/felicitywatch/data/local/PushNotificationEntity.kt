package com.dairoroberto.felicitywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import java.time.Instant

@Entity(tableName = "push_notifications")
data class PushNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val receivedAt: Instant = Instant.now(),
    /** Tipo real de la regla que disparó este push — null para pushes de
     * pruebas manuales ("Probar" en Ajustes) o notificaciones guardadas
     * antes de este campo. Reemplaza la heurística de detectar "vuelto la
     * corriente" por palabras clave en el texto (frágil: dejaba de
     * funcionar si el usuario personalizaba el mensaje de la regla en
     * Ajustes > Alertas, ya que el texto ya no contenía esas palabras). */
    val ruleType: AlertRuleType? = null
)
