package com.dairoroberto.felicitywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Equipo que el usuario confirmó para un evento de actividad concreto.
 *
 * Hace falta persistirlo por el mismo motivo que los descartados: los eventos
 * se RECALCULAN desde el historial de lecturas cada vez que se abre la
 * pestaña, y su equipo se atribuye por cercanía de consumo. Sin este registro,
 * confirmar "fue el microondas" no cambiaba nada — al siguiente recálculo la
 * lista volvía a proponer el split, porque su consumo declarado seguía siendo
 * el más cercano al salto detectado.
 *
 * Con esto, el detector prefiere la confirmación del usuario por encima de su
 * propia heurística, que es el orden correcto: el usuario sabe qué encendió.
 *
 * La clave es el instante del evento — dos eventos no pueden ocurrir en el
 * mismo milisegundo, porque vienen de lecturas consecutivas del historial.
 */
@Entity(tableName = "confirmed_appliance_events")
data class ConfirmedApplianceEventEntity(
    @PrimaryKey val eventEpochMillis: Long,
    /** Id del equipo confirmado. Si el equipo se borra del inventario, la
     * atribución deja de resolverse y el evento vuelve a la heurística. */
    val applianceId: Long,
    val confirmedAtEpochMillis: Long
)
