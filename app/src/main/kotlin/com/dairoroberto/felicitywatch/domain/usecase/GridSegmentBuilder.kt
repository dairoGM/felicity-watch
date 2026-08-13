package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.ui.components.GridSegment

/**
 * Construye tramos continuos de con/sin corriente a partir de lecturas
 * crudas — cada lectura representa el estado hasta la siguiente (o hasta
 * [nowEpochMillis] para la última), agrupando lecturas consecutivas con el
 * mismo estado en un solo tramo. Sin acotar por ningún rango de fecha: un
 * corte que cruza la medianoche queda como UN solo segmento continuo, no
 * partido en dos — eso es justamente lo que antes daba horas mal contadas
 * cuando la vista se limitaba a "un solo día" (ReportScreen, pestaña
 * Corriente).
 */
fun buildGridSegments(
    readings: List<PowerReadingEntity>,
    nowEpochMillis: Long
): List<GridSegment> {
    val sorted = readings.filter { it.gridPowerWatts != null }.sortedBy { it.timestampEpochMillis }
    if (sorted.size < 2) return emptyList()

    val rawSegments = sorted.mapIndexed { index, reading ->
        val online = (reading.gridPowerWatts ?: 0) >= 1
        val endMillis = if (index < sorted.size - 1) sorted[index + 1].timestampEpochMillis else nowEpochMillis
        GridSegment(reading.timestampEpochMillis, endMillis, online)
    }

    val merged = mutableListOf<GridSegment>()
    rawSegments.forEach { segment ->
        val last = merged.lastOrNull()
        if (last != null && last.online == segment.online) {
            merged[merged.size - 1] = last.copy(endEpochMillis = segment.endEpochMillis)
        } else {
            merged += segment
        }
    }
    return merged
}
