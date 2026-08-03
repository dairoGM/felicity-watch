package com.dairoroberto.felicitywatch.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors

data class MoreMenuItem(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/** Lista de herramientas secundarias — punto de crecimiento del menú: cada
 * función nueva que no amerite un lugar fijo en la barra inferior se agrega
 * aquí como una fila más, sin tener que reorganizar la navegación principal. */
@Composable
fun MoreScreen(items: List<MoreMenuItem>) {
    val colors = LocalFelicityColors.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surface2),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, contentDescription = item.label, tint = colors.accent)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(item.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMid)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = colors.textLow,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
