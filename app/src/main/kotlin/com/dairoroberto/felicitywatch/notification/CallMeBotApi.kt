package com.dairoroberto.felicitywatch.notification

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** Guía sección 7.3. Base URL: https://api.callmebot.com/ */
interface CallMeBotApi {
    @GET("whatsapp.php")
    suspend fun sendMessage(
        @Query("phone") phone: String,
        @Query("text") text: String,
        @Query("apikey") apiKey: String
    ): Response<ResponseBody>
}
