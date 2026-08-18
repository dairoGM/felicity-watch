package com.dairoroberto.felicitywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Evento de actividad de equipos que el usuario descartó de la bitácora.
 *
 * Los eventos NO se guardan: se recalculan al vuelo desde el historial de
 * lecturas cada vez que se abre la pestaña. Por eso "borrar" un evento no
 * puede ser un DELETE — al siguiente recálculo volvería a aparecer. En su
 * lugar se registra aquí el instante del evento descartado, y el detector
 * lo filtra.
 *
 * El timestamp del evento es la clave: dos eventos distintos no pueden
 * ocurrir en el mismo milisegundo, porque provienen de lecturas
 * consecutivas del historial.
 */
@Entity(tableName = "dismissed_appliance_events")
data class DismissedApplianceEventEntity(
    @PrimaryKey val eventEpochMillis: Long
)
