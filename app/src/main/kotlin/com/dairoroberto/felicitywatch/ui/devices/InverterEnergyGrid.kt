package com.dairoroberto.felicitywatch.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.util.Locale

private fun formatKwh(value: Double?): String =
    value?.let { String.format(Locale("es", "ES"), "%.1f", it) } ?: "—"

/** Grid 2x2 de energía del día — calcado de la sección "Energía" de la app
 * oficial de Felicity (Generada por Día / Inyectada / de Entrada / de Carga). */
@Composable
fun InverterEnergyGrid(inverter: InverterReading?, modifier: Modifier = Modifier) {
    val colors = LocalFelicityColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "ENERGÍA DE HOY",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EnergyStatCard(
                icon = Icons.Default.SolarPower,
                label = "Generada por día",
                value = formatKwh(inverter?.pvEnergyTodayKwh),
                iconColor = colors.accent,
                modifier = Modifier.weight(1f)
            )
            EnergyStatCard(
                icon = Icons.Default.ElectricBolt,
                label = "Inyectada",
                value = formatKwh(inverter?.gridFeedEnergyTodayKwh),
                iconColor = colors.textMid,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EnergyStatCard(
                icon = Icons.Default.ShoppingCart,
                label = "De entrada",
                value = formatKwh(inverter?.gridInputEnergyTodayKwh),
                iconColor = colors.textMid,
                modifier = Modifier.weight(1f)
            )
            EnergyStatCard(
                icon = Icons.Default.Home,
                label = "De carga",
                value = formatKwh(inverter?.loadEnergyTodayKwh),
                iconColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EnergyStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(colors.hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(" kWh", style = MaterialTheme.typography.labelSmall, color = colors.textMid)
            }
        }
    }
}
