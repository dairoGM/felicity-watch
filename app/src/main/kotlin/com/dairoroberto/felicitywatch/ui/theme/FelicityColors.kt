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
    val dangerBg: Color
)

val DarkFelicityColors = FelicitySemanticColors(
    surface2 = PanelSurface2,
    hairline = Hairline,
    textMid = TextMid,
    textLow = TextLow,
    green = Green,
    greenDim = GreenDim,
    tealDim = TealDim,
    dangerBg = DangerBg
)

val LightFelicityColors = FelicitySemanticColors(
    surface2 = LightSurface2,
    hairline = LightHairline,
    textMid = LightTextMid,
    textLow = LightTextLow,
    green = Green,
    greenDim = LightGreenDim,
    tealDim = LightTealDim,
    dangerBg = LightDangerBg
)

val LocalFelicityColors = staticCompositionLocalOf { DarkFelicityColors }
