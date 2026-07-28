package com.dairoroberto.felicitywatch.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dairoroberto.felicitywatch.R

@OptIn(ExperimentalTextApi::class)
private fun spaceGrotesk(weight: Int) = Font(
    resId = R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

@OptIn(ExperimentalTextApi::class)
private fun jetBrainsMono(weight: Int) = Font(
    resId = R.font.jetbrains_mono,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val SpaceGroteskFamily = FontFamily(spaceGrotesk(500), spaceGrotesk(600), spaceGrotesk(700))
val JetBrainsMonoFamily = FontFamily(jetBrainsMono(400), jetBrainsMono(500), jetBrainsMono(600))

val FelicityTypography = Typography(
    titleLarge = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight(600), fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight(600), fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = SpaceGroteskFamily, fontWeight = FontWeight(600), fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.5.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight(500), fontSize = 10.sp)
)

val MonoValueStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight(500), fontSize = 17.sp)
