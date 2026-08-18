package com.dairoroberto.felicitywatch.ui.appliances

import androidx.compose.ui.graphics.Color

/**
 * Color por local, para identificar de un vistazo a qué habitación pertenece
 * cada equipo.
 *
 * El color se DERIVA del nombre del local (hash estable), no se guarda en la
 * base: así no hace falta migración, ni que el usuario elija colores, y el
 * mismo local mantiene su color entre pantallas y entre reinicios de la app.
 *
 * Contrapartida honesta: dos locales distintos pueden caer en el mismo color
 * si su hash coincide módulo el tamaño de la paleta. Con la cantidad de
 * habitaciones de una casa es poco probable, y el nombre siempre está escrito
 * al lado, así que el color acompaña a la etiqueta en vez de sustituirla.
 *
 * La paleta se eligió con tonos separados entre sí y que funcionan sobre fondo
 * claro y oscuro, ya que la app soporta ambos temas.
 */
private val ROOM_PALETTE = listOf(
    Color(0xFF4FC3F7), // azul cielo
    Color(0xFFFFB74D), // ámbar
    Color(0xFF81C784), // verde
    Color(0xFFBA68C8), // violeta
    Color(0xFFE57373), // rojo suave
    Color(0xFF4DB6AC), // turquesa
    Color(0xFFFFD54F), // amarillo
    Color(0xFF9575CD), // lavanda
    Color(0xFFF06292), // rosa
    Color(0xFFA1887F)  // marrón
)

/** Color de los equipos sin local asignado: gris neutro, deliberadamente
 * apagado para que no compita con los locales reales. */
private val UNASSIGNED_ROOM_COLOR = Color(0xFF90A4AE)

/**
 * Color estable para un local. Se normaliza el nombre (minúsculas, sin
 * espacios sobrantes) para que "Sala" y " sala " compartan color en vez de
 * verse como dos locales distintos.
 */
fun roomColor(room: String): Color {
    val normalized = room.trim().lowercase()
    if (normalized.isEmpty()) return UNASSIGNED_ROOM_COLOR

    // hashCode() de String es estable entre ejecuciones (está especificado en
    // la API de Java), así que el color no cambia al reiniciar la app.
    val index = (normalized.hashCode().toUInt() % ROOM_PALETTE.size.toUInt()).toInt()
    return ROOM_PALETTE[index]
}
