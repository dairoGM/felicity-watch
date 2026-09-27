package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.remote.FelicityApiException
import com.dairoroberto.felicitywatch.data.remote.FelicityAuthException
import com.dairoroberto.felicitywatch.data.repository.FelicityCredentialsMissingException
import java.io.IOException

/** Lanzada por [RunMonitoringCycleUseCase] cuando este dispositivo cliente
 * fue revocado o eliminado por la master — corta el ciclo ANTES de
 * consultar Felicity (no tiene sentido gastar batería/datos en un cliente
 * sin acceso), y no debe contarse como una falla de conexión real. */
class DeviceAccessRevokedException : Exception("Acceso revocado por el dispositivo principal")

/** Mensaje amigable compartido entre el servicio y las lecturas manuales. */
fun describeMonitoringError(e: Exception): String = when (e) {
    is FelicityCredentialsMissingException -> "Faltan credenciales de FSolar"
    is FelicityAuthException -> "No se pudo iniciar sesión en Felicity: ${e.message}"
    is FelicityApiException -> "Error de la API de Felicity: ${e.message}"
    is DeviceAccessRevokedException -> e.message ?: "Acceso revocado"
    is IOException -> "Sin conexión a internet o Felicity no responde"
    else -> e.message ?: e.toString()
}
