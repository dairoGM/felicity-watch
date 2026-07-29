package com.dairoroberto.felicitywatch.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Singleton

/**
 * Guarda el texto crudo (sin pasar por Gson) del último cuerpo de respuesta
 * recibido para cada endpoint de Felicity. Existe solo para diagnóstico
 * remoto sin ADB: permite comparar "lo que Gson interpretó" contra "los
 * bytes que realmente llegaron por la red", ya que se sospecha una
 * discrepancia entre ambos (respuesta con datos reales confirmada en el
 * servidor, pero el objeto deserializado llega vacío).
 */
@Singleton
class RawResponseRecorder {
    @Volatile
    var lastSnapshotBody: String? = null
        private set

    @Volatile
    var lastLoginBody: String? = null
        private set

    fun recordSnapshot(body: String) {
        lastSnapshotBody = body
    }

    fun recordLogin(body: String) {
        lastLoginBody = body
    }
}

class RawResponseInterceptor(private val recorder: RawResponseRecorder) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val path = request.url.encodedPath
        val isSnapshot = path.endsWith("get_device_snapshot")
        val isLogin = path.endsWith("sec/login") || path.endsWith("userlogin")
        if (!isSnapshot && !isLogin) return response

        val body = response.body ?: return response
        val source = body.source()
        source.request(Long.MAX_VALUE)
        val bodyString = source.buffer.clone().readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)

        if (isSnapshot) recorder.recordSnapshot(bodyString) else recorder.recordLogin(bodyString)

        return response
    }
}
