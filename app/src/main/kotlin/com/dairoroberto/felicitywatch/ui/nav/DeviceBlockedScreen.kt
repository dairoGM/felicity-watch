package com.dairoroberto.felicitywatch.ui.nav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors

/**
 * Pantalla de bloqueo total para un dispositivo cliente sin aprobación
 * vigente — se muestra en vez de toda la app (Panel incluido) hasta que se
 * canjea un PIN generado desde el dispositivo master (Ajustes > Sistema >
 * Dispositivos). Las credenciales de FSolar ya están configuradas en este
 * punto (el onboarding ya se completó); esta pantalla es la verificación
 * adicional que decide si de verdad puede monitorear.
 */
@Composable
fun DeviceBlockedScreen(
    onAccessGranted: () -> Unit,
    onRetryCheck: () -> Unit,
    viewModel: DeviceBlockedViewModel = hiltViewModel()
) {
    val colors = LocalFelicityColors.current
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    val isRedeeming by viewModel.isRedeeming.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Justo al aprobarse es el único momento en que este dispositivo cliente
    // vuelve a estar frente al usuario antes de entrar a la app normal — pedir
    // el permiso de ubicación aquí evita que dependa de que encuentre la
    // tarjeta en Ajustes > Sistema por su cuenta (donde en la práctica nunca
    // se reportaba nada porque el permiso jamás se llegaba a pedir).
    var showBackgroundLocationExplainer by remember { mutableStateOf(false) }
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            viewModel.captureLocationNow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showBackgroundLocationExplainer = true
                return@rememberLauncherForActivityResult
            }
        }
        onAccessGranted()
    }
    val onApproved: () -> Unit = {
        val hasForegroundPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        when {
            hasForegroundPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                viewModel.captureLocationNow()
                showBackgroundLocationExplainer = true
            }
            hasForegroundPermission -> {
                viewModel.captureLocationNow()
                onAccessGranted()
            }
            else -> foregroundLocationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    if (showBackgroundLocationExplainer) {
        AlertDialog(
            onDismissRequest = {
                showBackgroundLocationExplainer = false
                onAccessGranted()
            },
            title = { Text("Un permiso más") },
            text = {
                Text(
                    "Android pide un permiso aparte para que la app funcione bien en segundo " +
                        "plano: \"Permitir todo el tiempo\". Vas a ver la pantalla de permisos de " +
                        "la app — elige esa opción en Ubicación."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundLocationExplainer = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                    onAccessGranted()
                }) { Text("Abrir ajustes") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackgroundLocationExplainer = false
                    onAccessGranted()
                }) { Text("Ahora no") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .then(Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = colors.textLow,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                "Esperando aprobación",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textHi,
                textAlign = TextAlign.Center
            )

            Text(
                "Este teléfono todavía no fue aprobado por tu dispositivo principal. " +
                    "Pide un código de acceso desde Ajustes > Sistema > Dispositivos en el " +
                    "teléfono marcado como principal, y escríbelo aquí.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMid,
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value = pin,
                onValueChange = { input ->
                    pin = input.filter { it.isDigit() }.take(6)
                    if (pin.length == 6) viewModel.redeem(pin, onApproved)
                },
                label = { Text("Código de 6 dígitos") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            if (isRedeeming) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                Button(
                    onClick = { viewModel.redeem(pin, onApproved) },
                    enabled = pin.length == 6,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Verificar") }
            }

            TextButton(onClick = onRetryCheck) {
                Text("Ya me aprobaron, reintentar")
            }
        }
    }
}
