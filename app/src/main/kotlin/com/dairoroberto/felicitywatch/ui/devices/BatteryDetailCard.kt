package com.dairoroberto.felicitywatch.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.util.Locale

/** Barra de SOC con UN SOLO color sólido que varía según el % (rojo en 0%,
 * verde en 100%, interpolando por el medio) — antes usaba un degradado
 * horizontal fijo rojo→verde estirado sobre el relleno, así que aunque la
 * batería estuviera al 100% el tramo izquierdo del relleno seguía
 * mostrándose rojo/ámbar en vez de verde uniforme. */
@Composable
fun BatteryDetailCard(battery: BatteryReading?, modifier: Modifier = Modifier) {
    val colors = LocalFelicityColors.current
    val soc = battery?.socPercent
    val charging = (battery?.current ?: 0.0) > 0
    val socColor = soc?.let { socColorFor(it, colors) } ?: colors.textLow

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.hairline),
            contentAlignment = Alignment.Center
        ) {
            if (soc != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (soc / 100f).coerceIn(0f, 1f))
                        .height(48.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(10.dp))
                        .background(socColor)
                )
            }
            // Texto centrado en TODA la barra (no solo en el relleno), en
            // blanco con sombra para mantener contraste tanto sobre el
            // degradado como sobre el fondo vacío (colors.hairline) — antes
            // usaba colors.textLow, ilegible sobre el tramo rojo del SOC bajo.
            Text(
                "${soc ?: "—"} %",
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 6f)
                ),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (charging) "Cargando" else "Descargando",
                style = MaterialTheme.typography.bodyMedium,
                color = if (charging) colors.green else colors.textMid,
                fontWeight = FontWeight.Medium
            )
            Text(
                battery?.current?.let { String.format(Locale("es", "ES"), "%.1f A", kotlin.math.abs(it)) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMid
            )
        }

        HeaderLabel("DATOS DE LA BATERÍA")
        DetailGrid(
            rows = listOf(
                "Capacidad" to (battery?.capacityAh?.let { "${it.toInt()} Ah" } ?: "—"),
                "Tipo" to (battery?.batteryType ?: "—"),
                "Voltaje" to (battery?.voltage?.let { String.format(Locale("es", "ES"), "%.2f V", it) } ?: "—"),
                "Estado de salud" to (battery?.healthPercent?.let { "$it %" } ?: "—"),
                "Energía restante" to (battery?.remainingEnergyKwh?.let { String.format(Locale("es", "ES"), "%.2f kWh", it) } ?: "—")
            )
        )

        HeaderLabel("LÍMITES DEL BMS")
        DetailGrid(
            rows = listOf(
                "Límite corriente carga" to (battery?.chargeCurrentLimitA?.let { "${it.toInt()} A" } ?: "—"),
                "Límite corriente descarga" to (battery?.dischargeCurrentLimitA?.let { "${it.toInt()} A" } ?: "—"),
                "Límite voltaje carga" to (battery?.chargeVoltageLimitV?.let { String.format(Locale("es", "ES"), "%.1f V", it) } ?: "—"),
                "Límite voltaje descarga" to (battery?.dischargeVoltageLimitV?.let { String.format(Locale("es", "ES"), "%.1f V", it) } ?: "—")
            )
        )
    }
}

/** Interpola linealmente rojo→ámbar (0-50%) y ámbar→verde (50-100%) — un
 * solo color sólido para el % actual, no un degradado fijo sobre la barra. */
private fun socColorFor(soc: Int, colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors): Color {
    val amber = Color(0xFFF59E0B)
    val fraction = (soc / 100f).coerceIn(0f, 1f)
    return if (fraction <= 0.5f) {
        lerp(colors.error, amber, fraction / 0.5f)
    } else {
        lerp(amber, colors.green, (fraction - 0.5f) / 0.5f)
    }
}

private fun lerp(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = 1f
    )
}

@Composable
private fun HeaderLabel(text: String) {
    val colors = LocalFelicityColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textLow,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun DetailGrid(rows: List<Pair<String, String>>) {
    val colors = LocalFelicityColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface2)
            .padding(vertical = 4.dp)
    ) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMid, modifier = Modifier.width(180.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}
