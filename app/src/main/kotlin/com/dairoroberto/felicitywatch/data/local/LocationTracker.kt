package com.dairoroberto.felicitywatch.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class DeviceLocation(val latitude: Double, val longitude: Double)

/**
 * Obtiene la ubicación de este dispositivo vía FusedLocationProviderClient
 * (Google Play Services) — combina GPS/WiFi/red y elige la fuente más
 * eficiente disponible, en vez de forzar el chip GPS puro. Se pide con
 * PRIORITY_BALANCED_POWER_ACCURACY (precisión de red, no GPS de alto
 * consumo): suficiente para ubicar la casa/zona en el mapa sin gastar
 * batería de más en cada ciclo de monitoreo.
 */
@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** null si no hay permiso, Play Services no está disponible, o no se pudo obtener una ubicación. */
    suspend fun getCurrentLocation(): DeviceLocation? {
        if (!hasLocationPermission()) return null

        return try {
            suspendCancellableCoroutine { continuation ->
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .build()

                @Suppress("MissingPermission") // Verificado arriba con hasLocationPermission().
                client.getCurrentLocation(request, null)
                    .addOnSuccessListener { location ->
                        val result = location?.let { DeviceLocation(it.latitude, it.longitude) }
                        if (continuation.isActive) continuation.resume(result)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            }
        } catch (e: Exception) {
            null
        }
    }
}
