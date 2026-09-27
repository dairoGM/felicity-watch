package com.dairoroberto.felicitywatch.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dairoroberto.felicitywatch.data.repository.AccountDeviceInfo
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Mapa (OpenStreetMap vía osmdroid — sin API key, a diferencia de Google
 * Maps) con un marcador por cada dispositivo con ubicación conocida. Solo
 * la master llega a abrir esto (botón oculto para clientes en la UI que
 * llama a este diálogo).
 */
@Composable
fun DeviceMapDialog(devices: List<AccountDeviceInfo>, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            DeviceMapView(devices = devices, context = context, modifier = Modifier.fillMaxSize())

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }
    }
}

// Centro aproximado de Cuba (Ciego de Ávila) y zoom que encuadra toda la
// isla — todos los dispositivos de esta cuenta están ahí, así que no tiene
// sentido arrancar en el (0,0) del Atlántico cuando ninguno tiene ubicación
// todavía.
private val CUBA_CENTER = GeoPoint(21.5, -79.5)
private const val CUBA_ZOOM = 7.0

@Composable
private fun DeviceMapView(devices: List<AccountDeviceInfo>, context: Context, modifier: Modifier = Modifier) {
    val located = remember(devices) { devices.filter { it.latitude != null && it.longitude != null } }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            val first = located.firstOrNull()
            if (first != null) {
                controller.setZoom(if (located.size == 1) 15.0 else 5.0)
                controller.setCenter(GeoPoint(first.latitude!!, first.longitude!!))
            } else {
                controller.setZoom(CUBA_ZOOM)
                controller.setCenter(CUBA_CENTER)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    AndroidView(
        factory = {
            located.forEach { device ->
                val marker = Marker(mapView)
                marker.position = GeoPoint(device.latitude!!, device.longitude!!)
                marker.title = device.displayName ?: "Dispositivo ${device.deviceId.take(8)}"
                marker.subDescription = if (device.role == "master") "Principal" else "Cliente"
                mapView.overlays.add(marker)
            }
            mapView
        },
        modifier = modifier
    )
}
