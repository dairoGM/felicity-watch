package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.ApplianceEntity
import com.google.gson.annotations.SerializedName

/**
 * Formato del archivo de respaldo del inventario de equipos.
 *
 * Se usa JSON con Gson (ya presente en el proyecto por Retrofit) en vez de
 * exportar la base de datos completa: un .db binario ata el archivo a la
 * versión exacta del esquema de Room, mientras que un JSON con [formatVersion]
 * se puede leer desde versiones futuras de la app aunque el esquema cambie.
 *
 * NO se exportan los ids: al importar se insertan como equipos nuevos. Copiar
 * los ids del teléfono de origen chocaría con los que ya existan en el
 * destino.
 */
data class ApplianceBackup(
    /** Versión del FORMATO del archivo, no de la app ni de la base. Permite
     * que una versión futura sepa leer respaldos viejos. */
    @SerializedName("formatVersion") val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** Identificador único de este respaldo — es lo que permite detectar que
     * un archivo ya se importó antes y no volver a insertarlo.
     *
     * Nullable a propósito: Gson NO aplica los valores por defecto de Kotlin
     * cuando el campo falta en el JSON, así que un archivo ajeno o corrupto
     * llegaría aquí con null pese a que el tipo diga lo contrario. Declararlo
     * nullable obliga a validarlo antes de usarlo. */
    @SerializedName("backupId") val backupId: String?,
    /** Cuándo se generó (epoch millis), para mostrarlo antes de importar. */
    @SerializedName("exportedAtEpochMillis") val exportedAtEpochMillis: Long = 0,
    @SerializedName("appliances") val appliances: List<BackupAppliance>?
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        /** Nombre sugerido del archivo al exportar. */
        const val FILE_PREFIX = "felicity-inventario"
    }
}

/**
 * Un equipo dentro del respaldo.
 *
 * Incluye el consumo aprendido ([confirmedWatts], [confirmationCount]): es
 * dato ganado con el uso real y perderlo al cambiar de teléfono obligaría a
 * empezar el aprendizaje de cero.
 */
data class BackupAppliance(
    @SerializedName("name") val name: String,
    @SerializedName("watts") val watts: Int,
    @SerializedName("minWatts") val minWatts: Int,
    @SerializedName("toleranceWatts") val toleranceWatts: Int,
    @SerializedName("room") val room: String,
    @SerializedName("similarGroup") val similarGroup: String,
    @SerializedName("confirmedWatts") val confirmedWatts: Int?,
    @SerializedName("confirmedAtEpochMillis") val confirmedAtEpochMillis: Long?,
    @SerializedName("confirmationCount") val confirmationCount: Int
)

fun ApplianceEntity.toBackup(): BackupAppliance = BackupAppliance(
    name = name,
    watts = watts,
    minWatts = minWatts,
    toleranceWatts = toleranceWatts,
    room = room,
    similarGroup = similarGroup,
    confirmedWatts = confirmedWatts,
    confirmedAtEpochMillis = confirmedAtEpochMillis,
    confirmationCount = confirmationCount
)

/** Convierte a entidad con id = 0 para que Room le asigne uno nuevo. */
fun BackupAppliance.toEntity(): ApplianceEntity = ApplianceEntity(
    id = 0,
    name = name,
    watts = watts,
    // Un respaldo manipulado a mano podría traer minWatts > watts; se
    // normaliza en vez de dejar un rango invertido que rompería las
    // coincidencias.
    minWatts = minOf(minWatts, watts),
    toleranceWatts = toleranceWatts.coerceAtLeast(0),
    room = room.trim(),
    similarGroup = similarGroup.trim(),
    confirmedWatts = confirmedWatts,
    confirmedAtEpochMillis = confirmedAtEpochMillis,
    confirmationCount = confirmationCount.coerceAtLeast(0)
)

/** Resultado de intentar importar un respaldo. */
sealed interface ImportResult {
    data class Success(val imported: Int, val skippedDuplicates: Int) : ImportResult
    /** El archivo ya se importó antes en este teléfono. */
    data object AlreadyImported : ImportResult
    data class Invalid(val reason: String) : ImportResult
}
