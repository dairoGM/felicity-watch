package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.ApplianceEntity

/** Uso estimado de un equipo en el periodo analizado. */
data class ApplianceUsage(
    val appliance: ApplianceEntity,
    /** Minutos que el equipo estuvo encendido, estimados a partir de los
     * pares encendido→apagado detectados. */
    val minutesOn: Int,
    /** Energía estimada en kWh: watts del equipo × tiempo encendido. */
    val estimatedKwh: Double,
    /** Cuántas veces se detectó que se encendió. */
    val timesOn: Int
)

/** Consumo estimado agregado por local/habitación. */
data class RoomConsumption(
    val room: String,
    val appliances: List<ApplianceUsage>
) {
    val totalKwh: Double get() = appliances.sumOf { it.estimatedKwh }
    val totalMinutes: Int get() = appliances.sumOf { it.minutesOn }
    val displayName: String get() = room.ifBlank { "Sin local asignado" }
}

/**
 * Estadística de consumo por local, estimada a partir de los eventos de
 * encendido/apagado detectados.
 *
 * ESTO ES UNA ESTIMACIÓN, no una medición. La cadena de supuestos es larga
 * y conviene tenerla presente:
 *
 * 1. Los eventos vienen de escalones de consumo, que ya son heurísticos.
 * 2. Cada escalón se atribuye al equipo del catálogo más parecido, y si
 *    dos equipos tienen consumo similar la atribución puede ser errónea.
 * 3. El tiempo encendido se estima emparejando un encendido con el
 *    siguiente apagado del MISMO equipo. Si un apagado no se detectó (por
 *    ejemplo porque coincidió con otro encendido y se cancelaron en el
 *    consumo total), el par queda abierto y ese tiempo no se cuenta.
 * 4. La energía asume consumo constante mientras estuvo encendido, lo cual
 *    es falso en equipos inverter.
 *
 * Por eso la UI debe presentar estos números como aproximados y nunca
 * como una factura. Sirven para comparar locales entre sí ("el cuarto
 * consume mucho más que la sala"), no para conocer el consumo exacto.
 */
object RoomConsumptionStats {

    fun compute(
        events: List<ApplianceEvent>,
        appliances: List<ApplianceEntity>,
        /** Fin del periodo analizado, para cerrar los encendidos que siguen
         * abiertos. Si no se pasa, se usa el último evento conocido. */
        periodEndEpochMillis: Long? = null
    ): List<RoomConsumption> {
        if (appliances.isEmpty()) return emptyList()

        // Los eventos llegan más reciente primero (ver detector); para
        // emparejar encendidos con apagados hay que recorrerlos en orden
        // cronológico real.
        val chronological = events.sortedBy { it.epochMillis }

        val minutesByAppliance = mutableMapOf<Long, Int>()
        val timesOnByAppliance = mutableMapOf<Long, Int>()
        // Encendidos aún sin su apagado correspondiente, por equipo.
        val pendingOn = mutableMapOf<Long, Long>()

        chronological.forEach { event ->
            val id = event.matchedAppliance?.id ?: return@forEach
            if (event.turnedOn) {
                // Si ya había un encendido abierto para este equipo, se
                // descarta el anterior: no se detectó su apagado y contar
                // el tiempo hasta ahora inflaría el total sin fundamento.
                pendingOn[id] = event.epochMillis
                timesOnByAppliance[id] = (timesOnByAppliance[id] ?: 0) + 1
            } else {
                val startedAt = pendingOn.remove(id) ?: return@forEach
                val minutes = ((event.epochMillis - startedAt) / 60_000L).toInt()
                if (minutes > 0) {
                    minutesByAppliance[id] = (minutesByAppliance[id] ?: 0) + minutes
                }
            }
        }

        // Encendidos que quedaron ABIERTOS: el equipo sigue encendido, o su
        // apagado nunca se detectó. Antes se descartaban por completo, y por
        // eso un equipo encendido hace horas aportaba 0 minutos y la
        // estadística por local se veía vacía o en cero. Se cuenta el tiempo
        // hasta el fin del periodo, que es la mejor estimación disponible.
        val periodEnd = periodEndEpochMillis ?: chronological.lastOrNull()?.epochMillis
        if (periodEnd != null) {
            pendingOn.forEach { (id, startedAt) ->
                val minutes = ((periodEnd - startedAt) / 60_000L).toInt()
                if (minutes > 0) {
                    minutesByAppliance[id] = (minutesByAppliance[id] ?: 0) + minutes
                }
            }
        }

        val usageByAppliance = appliances.mapNotNull { appliance ->
            val minutes = minutesByAppliance[appliance.id] ?: 0
            val times = timesOnByAppliance[appliance.id] ?: 0
            if (minutes == 0 && times == 0) return@mapNotNull null

            // Se usa el consumo medido si existe (más fiable que la
            // etiqueta); si el equipo tiene rango, el punto medio como
            // aproximación razonable de su consumo promedio.
            val referenceWatts = appliance.confirmedWatts
                ?: if (appliance.hasRange) (appliance.minWatts + appliance.watts) / 2 else appliance.watts

            ApplianceUsage(
                appliance = appliance,
                minutesOn = minutes,
                estimatedKwh = referenceWatts * (minutes / 60.0) / 1000.0,
                timesOn = times
            )
        }

        return usageByAppliance
            .groupBy { it.appliance.room }
            .map { (room, usages) -> RoomConsumption(room, usages.sortedByDescending { it.estimatedKwh }) }
            .sortedWith(
                // Locales con más consumo primero; "sin asignar" al final
                // aunque tenga consumo alto, porque no es un local real.
                compareBy({ it.room.isBlank() }, { -it.totalKwh })
            )
    }
}
