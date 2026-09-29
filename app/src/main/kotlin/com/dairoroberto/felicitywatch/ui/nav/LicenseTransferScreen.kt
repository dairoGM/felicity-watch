package com.dairoroberto.felicitywatch.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.domain.model.LicenseState
import com.dairoroberto.felicitywatch.domain.model.LicenseStatus
import com.dairoroberto.felicitywatch.domain.model.MAX_REJECTED_ATTEMPTS
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors

/**
 * Pantalla del cliente cuando se le acabó el periodo free: declara el ID de su
 * transferencia y espera la decisión del master.
 *
 * Cubre los tres momentos del ciclo con la misma pantalla, porque para el
 * usuario son el mismo asunto ("mi pago"): pedir el comprobante, esperar la
 * validación, y el aviso de rechazo con los intentos que le quedan.
 */
@Composable
fun LicenseTransferScreen(
    license: LicenseState,
    freePeriodDays: Int,
    onStateChanged: () -> Unit,
    viewModel: LicenseTransferViewModel = hiltViewModel()
) {
    val colors = LocalFelicityColors.current
    var reference by remember { mutableStateOf("") }
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val pending = license.status == LicenseStatus.PENDING

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            val rejected = license.status == LicenseStatus.REJECTED

            LicenseIcon(
                icon = when {
                    pending -> Icons.Default.HourglassTop
                    rejected -> Icons.Default.Cancel
                    else -> Icons.Default.ReceiptLong
                },
                tint = when {
                    pending -> colors.accent
                    rejected -> MaterialTheme.colorScheme.error
                    else -> colors.textMid
                }
            )

            Text(
                when {
                    pending -> "Esperando validación"
                    rejected -> "Transferencia rechazada"
                    else -> "Tu periodo de prueba terminó"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (rejected) MaterialTheme.colorScheme.error else colors.textHi,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp)
            )

            Text(
                when {
                    pending ->
                        "Ya recibimos el ID de tu transferencia. No podrás usar la app hasta " +
                            "que se valide desde el dispositivo principal."
                    rejected ->
                        "El pago no fue validado. Pide un código nuevo al dispositivo " +
                            "principal para volver a intentarlo."
                    else ->
                        "Se acabaron tus $freePeriodDays días de prueba. Para seguir usando la app, " +
                            "escribe el ID de la transferencia que realizaste."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMid,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )

            // El motivo que escribió el master es lo único que le dice al
            // usuario QUÉ corregir — sin esto, un rechazo es un callejón sin
            // salida en el que solo puede adivinar.
            license.rejectReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    "Motivo: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface2)
                        .padding(12.dp)
                )
            }

            // En espera o rechazado no se muestra el formulario: en el primer
            // caso ya hay una transferencia esperando decisión (mandar otra
            // solo confundiría al master), y en el segundo el rechazo revocó
            // el dispositivo, así que primero necesita canjear un código nuevo
            // — escribir otro ID aquí no le devolvería el acceso.
            if (pending || rejected) {
                license.transferReference?.let { reference ->
                    Text(
                        "ID enviado: $reference",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                if (rejected) {
                    Text(
                        "Intento ${license.rejectedAttempts} de $MAX_REJECTED_ATTEMPTS" +
                            if (license.attemptsRemaining > 0) {
                                " · te ${if (license.attemptsRemaining == 1) "queda" else "quedan"} " +
                                    "${license.attemptsRemaining} " +
                                    if (license.attemptsRemaining == 1) "intento" else "intentos"
                            } else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                Button(
                    onClick = { viewModel.refresh(onStateChanged) },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Comprobar de nuevo")
                    }
                }
                return@Column
            }

            OutlinedTextField(
                value = reference,
                onValueChange = {
                    reference = it
                    if (errorMessage != null) viewModel.clearError()
                },
                singleLine = true,
                label = { Text("ID de la transferencia") },
                isError = errorMessage != null,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            )

            // Solo se muestra una vez que hubo al menos un rechazo: antes de
            // eso, anunciar "te quedan 3 intentos" suena a amenaza sin motivo.
            if (license.rejectedAttempts > 0) {
                Text(
                    "Intento ${license.rejectedAttempts + 1} de $MAX_REJECTED_ATTEMPTS. " +
                        "Si se rechaza ${license.attemptsRemaining} ${if (license.attemptsRemaining == 1) "vez más" else "veces más"}, " +
                        "el acceso quedará bloqueado.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            errorMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Button(
                onClick = { viewModel.submit(reference) { onStateChanged() } },
                enabled = !isSubmitting && reference.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Enviar para validación")
                }
            }

            TextButton(
                onClick = { viewModel.refresh(onStateChanged) },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Ya lo envié — comprobar estado", color = colors.textMid)
            }
        }
    }
}

/**
 * Pantalla terminal: agotó los intentos. No ofrece ninguna acción a propósito
 * — el desbloqueo es del master, y un botón aquí solo llevaría a reintentar
 * algo que el servidor va a rechazar.
 */
@Composable
fun LicenseBlockedScreen(license: LicenseState) {
    val colors = LocalFelicityColors.current

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            LicenseIcon(icon = Icons.Default.Lock, tint = MaterialTheme.colorScheme.error)

            Text(
                "Acceso bloqueado",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textHi,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp)
            )

            Text(
                "Se rechazaron $MAX_REJECTED_ATTEMPTS transferencias para este dispositivo. " +
                    "Solo se puede desbloquear desde el dispositivo principal.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMid,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp)
            )

            license.rejectReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(
                    "Último motivo: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface2)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun LicenseIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    val colors = LocalFelicityColors.current
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(colors.surface2),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
}
