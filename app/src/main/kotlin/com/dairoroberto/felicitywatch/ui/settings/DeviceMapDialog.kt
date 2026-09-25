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

@Composable
private fun DeviceMapView(devices: List<AccountDeviceInfo>, context: Context, modifier: Modifier = Modifier) {
    val located = remember(devices) { devices.filter { it.latitude != null && it.longitude != null } }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(if (located.size == 1) 15.0 else 5.0)
            val first = located.firstOrNull()
            if (first != null) {
                controller.setCenter(GeoPoint(first.latitude!!, first.longitude!!))
            } else {
                controller.setCenter(GeoPoint(0.0, 0.0))
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
