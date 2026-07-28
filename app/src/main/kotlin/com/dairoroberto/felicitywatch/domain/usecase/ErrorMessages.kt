package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.remote.FelicityApiException
import com.dairoroberto.felicitywatch.data.remote.FelicityAuthException
import com.dairoroberto.felicitywatch.data.repository.FelicityCredentialsMissingException
import java.io.IOException

/** Mensaje amigable compartido entre el servicio y las lecturas manuales. */
fun describeMonitoringError(e: Exception): String = when (e) {
    is FelicityCredentialsMissingException -> "Faltan credenciales de FSolar"
    is FelicityAuthException -> "No se pudo iniciar sesión en Felicity: ${e.message}"
    is FelicityApiException -> "Error de la API de Felicity: ${e.message}"
    is IOException -> "Sin conexión a internet o Felicity no responde"
    else -> e.message ?: e.toString()
}
