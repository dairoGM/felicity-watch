package com.dairoroberto.felicitywatch.ui.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.domain.usecase.BillingEstimate
import com.dairoroberto.felicitywatch.domain.usecase.DailyBilling
import com.dairoroberto.felicitywatch.domain.usecase.ElectricityTariff
import com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Estimación de la factura eléctrica y del ahorro que aporta el sistema
 * solar — rediseñada como UN SOLO resumen inmediato en vez de varias
 * tarjetas que había que comparar entre sí: el número que más importa
 * ("cuánto llevo consumido este mes y cuánto me toca pagar") está arriba de
 * todo, sin necesidad de bajar ni de restar nada a mano.
 */
@Composable
fun BillingScreen(viewModel: BillingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalFelicityColors.current
    val estimate = state.estimate

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MonthFilterRow(
                months = state.availableMonths,
                selection = state.selection,
                onSelect = { viewModel.selectMonth(it) },
                onSelectRange = { from, to -> viewModel.selectRange(from, to) }
            )
        }

        if (estimate == null || estimate.totalKwh <= 0.0) {
            item { EmptyBillingCard() }
            return@LazyColumn
        }

        // 1. TODO lo importante en un solo vistazo: total del mes, cuánto se
        // paga, cuánto se ahorra, y qué porcentaje cubre el sistema. Antes
        // esto eran 3 tarjetas separadas (a pagar / ahorro / reparto) que
        // había que leer una por una para armar el panorama completo.
        item {
            MonthSummaryCard(
                estimate = estimate,
                projectedCost = state.projectedMonthEndCost,
                projectedKwh = state.projectedMonthEndKwh
            )
        }

        // 2. Consumo día por día — lista directa (no hay que tocar barras
        // para leer el valor exacto de cada día).
        item { DailyBreakdownCard(estimate = estimate) }

        // 3. Desglose por tramos de la tarifa — detalle de apoyo, no es lo
        // primero que hay que mirar.
        item { TariffBreakdownCard(estimate = estimate) }

        if (state.historyIncomplete) {
            item { IncompleteHistoryNote() }
        }

        item { MethodNote() }
    }
}

/* ------------------------------------------------------------------ */
/* Filtro de meses                                                     */
/* ------------------------------------------------------------------ */

@Composable
private fun MonthFilterRow(
    months: List<YearMonth>,
    selection: MonthRange,
    onSelect: (YearMonth) -> Unit,
    onSelectRange: (YearMonth, YearMonth) -> Unit
) {
    val colors = LocalFelicityColors.current
    // Primer toque elige un mes; el segundo, si es distinto, forma el rango.
    // Es más directo que dos selectores separados de "desde" y "hasta".
    var rangeAnchor by remember { mutableStateOf<YearMonth?>(null) }

    Column {
        Text(
            "PERIODO",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textLow
        )
        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(months, key = { it.toString() }) { month ->
                val inRange = month >= selection.from && month <= selection.to
                FilterChip(
                    selected = inRange,
                    onClick = {
                        val anchor = rangeAnchor
                        if (anchor != null && anchor != month) {
                            onSelectRange(anchor, month)
                            rangeAnchor = null
                        } else {
                            onSelect(month)
                            rangeAnchor = month
                        }
                    },
                    label = { Text(shortMonthLabel(month)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.tealDim
                    )
                )
            }
        }
        Text(
            when {
                months.size <= 1 ->
                    "Todavía solo hay datos de este mes — el historial guarda 6 meses. " +
                        "Cuando pase un mes más podrás comparar entre ambos."
                selection.isSingleMonth -> "Toca otro mes para comparar un rango"
                else ->
                    "Rango de ${shortMonthLabel(selection.from)} a " +
                        "${shortMonthLabel(selection.to)} · toca un mes para volver a uno solo"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/* ------------------------------------------------------------------ */
/* 1. Resumen del mes — TODO en un solo lugar                          */
/* ------------------------------------------------------------------ */

@Composable
private fun MonthSummaryCard(
    estimate: BillingEstimate,
    projectedCost: Double?,
    projectedKwh: Double?
) {
    val colors = LocalFelicityColors.current
    val offGridPercent = (estimate.offGridShare * 100).toInt()

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            // Headline: el número que responde "¿cuánto llevo consumido?"
            // sin tener que sumar nada — ya es el total del periodo elegido.
            Text(
                "CONSUMO TOTAL · ${estimate.period.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textLow
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    formatKwh(estimate.totalKwh),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = colors.textHi
                )
                Text(
                    "kWh",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(start = 6.dp, bottom = 7.dp)
                )
            }

            // Barra de reparto: de un vistazo, qué proporción se paga vs. se
            // ahorra — sin tener que comparar dos tarjetas de color distinto.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                val gridWeight = (estimate.gridKwh / estimate.totalKwh).toFloat().coerceIn(0.001f, 1f)
                val offWeight = (1f - gridWeight).coerceAtLeast(0.001f)
                Box(Modifier.weight(gridWeight).fillMaxSize().background(colors.accent))
                Box(Modifier.weight(offWeight).fillMaxSize().background(colors.green))
            }
            Text(
                "Tu sistema cubrió el $offGridPercent% de todo lo que consumiste",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 6.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = colors.hairline)

            // Los dos montos LADO A LADO, con el mismo formato visual, para
            // que se lean como un par comparable de un solo golpe de vista
            // en vez de tener que recordar el número de la tarjeta anterior.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoneyBlock(
                    icon = Icons.Default.Bolt,
                    iconTint = colors.accent,
                    label = "A PAGAR",
                    amount = estimate.gridCost,
                    amountColor = colors.textHi,
                    kwhLabel = "${formatKwh(estimate.gridKwh)} kWh con corriente",
                    modifier = Modifier.weight(1f)
                )
                MoneyBlock(
                    icon = Icons.Default.Savings,
                    iconTint = colors.green,
                    label = "AHORRADO",
                    amount = estimate.savedCost,
                    amountColor = colors.green,
                    kwhLabel = "${formatKwh(estimate.offGridKwh)} kWh sin corriente",
                    modifier = Modifier.weight(1f)
                )
            }

            // Tramo/tarifa actual: contexto de por qué el monto es el que es,
            // sin tener que ir a la tarjeta de tramos para saberlo.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Tramo ${estimate.bracketNumber} · ${formatMoney(estimate.marginalRate)} CUP/kWh",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
                estimate.kwhToNextBracket?.let { remaining ->
                    Text(
                        "${formatKwh(remaining)} kWh para el siguiente tramo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Proyección a fin de mes, solo para el mes en curso.
            if (projectedCost != null && projectedKwh != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent.copy(alpha = 0.10f))
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            "Si sigues así, a fin de mes: ${formatMoney(projectedCost)} CUP",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.textHi
                        )
                        Text(
                            "≈ ${formatKwh(projectedKwh)} kWh · proyectado con tu promedio diario",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMid
                        )
                    }
                }
            }

            Text(
                "Sin tu sistema solar habrías pagado ${formatMoney(estimate.costWithoutSolar)} CUP en total.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun MoneyBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    amount: Double,
    amountColor: Color,
    kwhLabel: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.hairline.copy(alpha = 0.25f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(13.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textLow,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                formatMoney(amount),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = amountColor
            )
            Text(
                " CUP",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Text(
            kwhLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMid,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/* ------------------------------------------------------------------ */
/* 2. Consumo día por día — lista directa                              */
/* ------------------------------------------------------------------ */

@Composable
private fun DailyBreakdownCard(estimate: BillingEstimate) {
    val colors = LocalFelicityColors.current
    if (estimate.daily.isEmpty()) return

    val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM").withLocale(Locale("es", "ES"))
    val bestDay = estimate.daily.maxByOrNull { it.totalKwh }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "CONSUMO POR DÍA",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textHi
            )
            Text(
                "Cada fila es un día: total y cuánto de ese total pagaste.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
                LegendDot(color = colors.accent, label = "Con corriente")
                LegendDot(color = colors.green, label = "Sin corriente", modifier = Modifier.padding(start = 16.dp))
            }

            estimate.daily.sortedByDescending { it.date }.forEach { day ->
                DailyBillingRow(
                    day = day,
                    dayFormatter = dayFormatter,
                    isBest = bestDay != null && day.date == bestDay.date,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun DailyBillingRow(
    day: DailyBilling,
    dayFormatter: DateTimeFormatter,
    isBest: Boolean,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                dayFormatter.format(day.date).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.textHi
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBest) {
                    Text(
                        "MÁXIMO · ",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.green
                    )
                }
                Text(
                    "${formatKwh(day.totalKwh)} kWh",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textHi
                )
            }
        }

        // Barra apilada proporcional al total del día: con/sin corriente,
        // mismo lenguaje visual del resumen de arriba.
        val gridFrac = if (day.totalKwh > 0) (day.gridKwh / day.totalKwh).toFloat().coerceIn(0f, 1f) else 0f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            if (gridFrac > 0f) Box(Modifier.weight(gridFrac).fillMaxSize().background(colors.accent))
            if (1f - gridFrac > 0f) Box(Modifier.weight((1f - gridFrac).coerceAtLeast(0.001f)).fillMaxSize().background(colors.green))
        }
        Text(
            "${formatKwh(day.gridKwh)} kWh con corriente · ${formatKwh(day.offGridKwh)} kWh sin corriente",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    val colors = LocalFelicityColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMid, modifier = Modifier.padding(start = 6.dp))
    }
}

/* ------------------------------------------------------------------ */
/* 3. Desglose por tramos                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun TariffBreakdownCard(estimate: BillingEstimate) {
    val colors = LocalFelicityColors.current
    val charges = remember(estimate.gridKwh) { ElectricityTariff.breakdown(estimate.gridKwh) }

    if (charges.isEmpty()) return

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "CÓMO SE CALCULA",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textHi
            )
            Text(
                "La tarifa es progresiva: cada tramo cobra su precio solo por los kWh que caen " +
                    "dentro de él.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 2.dp)
            )

            val maxAmount = charges.maxOf { it.amount }.coerceAtLeast(0.01)

            charges.forEach { charge ->
                Column(Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (charge.toKwh != null) "${charge.fromKwh}–${charge.toKwh}"
                            else "más de ${charge.fromKwh}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = colors.textMid,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${formatKwh(charge.kwhInBracket)} × ${formatMoney(charge.pricePerKwh)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = colors.textLow
                        )
                        Text(
                            formatMoney(charge.amount),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = colors.textHi,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (charge.amount / maxAmount).toFloat().coerceIn(0f, 1f) },
                        color = colors.accent,
                        trackColor = colors.hairline.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 4.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = colors.hairline)
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "TOTAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textHi,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${formatMoney(estimate.gridCost)} CUP",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textHi
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Notas y estado vacío                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun EmptyBillingCard() {
    val colors = LocalFelicityColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Todavía no hay consumo registrado en este periodo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textHi
            )
            Text(
                "La estimación se construye con el historial que la app va guardando en cada " +
                    "consulta. Deja el servicio de vigilancia activo y en unas horas verás la " +
                    "primera estimación.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun IncompleteHistoryNote() {
    val colors = LocalFelicityColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(14.dp)
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            "El historial local guarda 6 meses, así que este periodo puede estar incompleto y " +
                "la cifra saldría más baja de lo real.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMid,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun MethodNote() {
    val colors = LocalFelicityColors.current
    Text(
        "Cómo se mide: la app suma el consumo de cada intervalo entre lecturas y lo clasifica " +
            "según si en ese momento había corriente de la red. Es una ESTIMACIÓN — depende de " +
            "cada cuánto consulta la app, y los huecos de más de 15 minutos sin lecturas no se " +
            "cuentan. No sustituye a la factura oficial.",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textLow,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
    )
}

/* ------------------------------------------------------------------ */

private fun formatMoney(value: Double): String =
    String.format(Locale("es", "ES"), "%,.2f", value)

private fun formatKwh(value: Double): String =
    String.format(Locale("es", "ES"), "%.1f", value)

private fun shortMonthLabel(month: YearMonth): String {
    val names = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )
    return "${names[month.monthValue - 1]} ${month.year % 100}"
}
