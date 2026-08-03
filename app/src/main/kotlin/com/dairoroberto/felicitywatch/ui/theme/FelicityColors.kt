package com.dairoroberto.felicitywatch.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores "semánticos" que sí cambian entre modo claro/oscuro, para las
 * pantallas que necesitan más matices que los que expone ColorScheme de
 * Material 3 directamente (superficie secundaria, textos atenuados, verde
 * de éxito, etc). Se acceden vía [LocalFelicityColors] en vez de importar
 * las constantes fijas de Color.kt, que son solo la paleta oscura.
 */
data class FelicitySemanticColors(
    val surface2: Color,
    val hairline: Color,
    val textMid: Color,
    val textLow: Color,
    val green: Color,
    val greenDim: Color,
    val tealDim: Color,
    val dangerBg: Color,
    /** Acento principal de marca (Teal), correcto para el tema activo — usar
     * esto en vez del color fijo `Teal` de Color.kt, que es solo la variante
     * oscura y quedaba mal en modo claro (mismo tono sobre fondo blanco, sin
     * suficiente contraste, y sin relación con el resto de acentos). */
    val accent: Color,
    /** Rojo de error/peligro correcto para el tema activo — reemplaza el uso
     * directo de DangerBorder/ChartRed (fijos, pensados solo para fondo
     * oscuro) en textos e indicadores de "sin corriente"/"offline". */
    val error: Color,
    /** Serie "Carga" en gráficos multilínea (morado) — antes era un
     * Color(0xFF...) suelto en ReportScreen sin variante clara/oscura. */
    val chargeAccent: Color
)

val DarkFelicityColors = FelicitySemanticColors(
    surface2 = PanelSurface2,
    hairline = Hairline,
    textMid = TextMid,
    textLow = TextLow,
    green = Green,
    greenDim = GreenDim,
    tealDim = TealDim,
    dangerBg = DangerBg,
    accent = Teal,
    error = DarkError,
    chargeAccent = ChargeAccent
)

val LightFelicityColors = FelicitySemanticColors(
    surface2 = LightSurface2,
    hairline = LightHairline,
    textMid = LightTextMid,
    textLow = LightTextLow,
    green = LightGreen,
    greenDim = LightGreenDim,
    tealDim = LightAccentDim,
    dangerBg = LightDangerBg,
    accent = LightAccent,
    error = LightError,
    chargeAccent = LightChargeAccent
)

val LocalFelicityColors = staticCompositionLocalOf { DarkFelicityColors }
