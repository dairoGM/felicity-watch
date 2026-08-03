package com.dairoroberto.felicitywatch.ui.devices

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.util.Locale

private fun formatFlowValue(watts: Int?): String {
    if (watts == null) return "—"
    val absWatts = kotlin.math.abs(watts)
    return if (absWatts >= 1000) {
        String.format(Locale("es", "ES"), "%.2f kW", absWatts / 1000.0)
    } else {
        "$absWatts W"
    }
}

/**
 * Diagrama de flujo FV/Red/Batería/Carga calcado del de la app oficial de
 * Felicity: 4 nodos en esquinas + línea central animada (puntos que se
 * desplazan) indicando dirección/intensidad del flujo de energía.
 */
@Composable
fun FlowDiagram(
    pvWatts: Int?,
    gridWatts: Int?,
    loadWatts: Int?,
    batteryWatts: Int?,
    socPercent: Int?,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current
    val charging = (batteryWatts ?: 0) > 0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FlowNode(
                icon = Icons.Default.SolarPower,
                label = "FV",
                value = formatFlowValue(pvWatts),
                iconColor = colors.accent
            )
            FlowNode(
                icon = Icons.Default.ElectricBolt,
                label = "Red",
                value = formatFlowValue(gridWatts),
                iconColor = colors.textMid,
                alignEnd = true
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .padding(vertical = 4.dp)
        ) {
            FlowLinesCanvas(
                activeTop = (pvWatts ?: 0) > 0 || (gridWatts ?: 0) > 0,
                activeBottom = (loadWatts ?: 0) > 0 || (batteryWatts ?: 0) != 0,
                lineColor = colors.accent,
                dotColor = colors.accent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FlowNode(
                icon = Icons.Default.BatteryChargingFull,
                label = if (charging) "Cargando" else "Descargando",
                value = formatFlowValue(batteryWatts),
                iconColor = colors.green,
                subtitle = socPercent?.let { "$it %" }
            )
            FlowNode(
                icon = Icons.Default.Home,
                label = "Carga de respaldo",
                value = formatFlowValue(loadWatts),
                iconColor = MaterialTheme.colorScheme.secondary,
                alignEnd = true
            )
        }
    }
}

@Composable
private fun FlowNode(
    icon: ImageVector,
    label: String,
    value: String,
    iconColor: Color,
    subtitle: String? = null,
    alignEnd: Boolean = false
) {
    val colors = LocalFelicityColors.current
    Row(verticalAlignment = Alignment.Top) {
        if (!alignEnd) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(28.dp))
            }
        }
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMid)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.green)
            }
        }
        if (alignEnd) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * Líneas del diagrama con puntos animados desplazándose a lo largo del
 * trazo — mismo efecto visual del "gif" de la web de Felicity, logrado con
 * una animación infinita de fase de dash en vez de una imagen real.
 */
@Composable
private fun FlowLinesCanvas(
    activeTop: Boolean,
    activeBottom: Boolean,
    lineColor: Color,
    dotColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flow")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
        val leftX = size.width * 0.12f
        val rightX = size.width * 0.88f
        val centerX = size.width / 2f
        val topY = 0f
        val midY = size.height / 2f
        val bottomY = size.height

        val staticStroke = Stroke(width = 3f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), phase)

        // FV --- centro (arriba izquierda)
        drawLine(color = lineColor.copy(alpha = 0.25f), start = Offset(leftX, topY), end = Offset(leftX, midY), strokeWidth = 3f)
        drawLine(color = lineColor.copy(alpha = 0.25f), start = Offset(leftX, midY), end = Offset(centerX, midY), strokeWidth = 3f)
        // Red --- centro (arriba derecha)
        drawLine(color = lineColor.copy(alpha = 0.25f), start = Offset(rightX, topY), end = Offset(rightX, midY), strokeWidth = 3f)
        drawLine(color = lineColor.copy(alpha = 0.25f), start = Offset(rightX, midY), end = Offset(centerX, midY), strokeWidth = 3f)
        // Batería --- centro (abajo izquierda)
        drawLine(color = lineColor.copy(alpha = 0.25f), start = Offset(leftX, bottomY), end = Offset(leftX, midY), strokeWidth = 3f)
        // Carga --- centro (abajo derecha)
        drawLine(color = lineColor.copy(alpha = 0.25f), start = Offset(rightX, bottomY), end = Offset(rightX, midY), strokeWidth = 3f)
        drawLine(color = lineColor.copy(alpha = 0.25f), start = Offset(centerX, midY), end = Offset(rightX, midY), strokeWidth = 3f)
        drawLine(color = lineColor.copy(alpha = 0.25f), start = Offset(leftX, midY), end = Offset(centerX, midY), strokeWidth = 3f)

        if (activeTop) {
            drawLine(color = dotColor, start = Offset(leftX, topY), end = Offset(leftX, midY), strokeWidth = 3f, pathEffect = dashEffect)
            drawLine(color = dotColor, start = Offset(leftX, midY), end = Offset(centerX, midY), strokeWidth = 3f, pathEffect = dashEffect)
        }
        if (activeBottom) {
            drawLine(color = dotColor, start = Offset(centerX, midY), end = Offset(rightX, midY), strokeWidth = 3f, pathEffect = dashEffect)
            drawLine(color = dotColor, start = Offset(rightX, midY), end = Offset(rightX, bottomY), strokeWidth = 3f, pathEffect = dashEffect)
        }

        drawCircle(color = lineColor, radius = 5f, center = Offset(centerX, midY))
    }
}
