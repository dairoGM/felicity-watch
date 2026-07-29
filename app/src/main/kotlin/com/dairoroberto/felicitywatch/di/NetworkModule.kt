package com.dairoroberto.felicitywatch.di

import com.dairoroberto.felicitywatch.BuildConfig
import com.dairoroberto.felicitywatch.data.remote.FelicityApiService
import com.dairoroberto.felicitywatch.data.remote.RawResponseInterceptor
import com.dairoroberto.felicitywatch.data.remote.RawResponseRecorder
import com.dairoroberto.felicitywatch.notification.CallMeBotApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private const val FELICITY_BASE_URL = "https://shine-api.felicitysolar.com/"
private const val CALLMEBOT_BASE_URL = "https://api.callmebot.com/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRawResponseRecorder(): RawResponseRecorder = RawResponseRecorder()

    @Provides
    @Singleton
    fun provideOkHttpClient(recorder: RawResponseRecorder): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // Verificado contra el servidor real: la nube de Felicity rechaza
            // el body con "deviceSn: darf nicht null sein" (código 2002006)
            // cuando el Content-Type incluye "; charset=UTF-8" -- que es lo
            // que GsonConverterFactory añade por defecto -- aunque el JSON
            // enviado sea idéntico y válido. Solo acepta "application/json"
            // a secas. Este interceptor reescribe el body con ese Content-Type
            // exacto únicamente para el host de Felicity.
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host == "shine-api.felicitysolar.com" && request.body != null) {
                    val buffer = Buffer()
                    request.body!!.writeTo(buffer)
                    val plainJsonBody = buffer.readByteArray().toRequestBody("application/json".toMediaType())
                    chain.proceed(request.newBuilder().method(request.method, plainJsonBody).build())
                } else {
                    chain.proceed(request)
                }
            }
            .addInterceptor(RawResponseInterceptor(recorder))

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("felicity")
    fun provideFelicityRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(FELICITY_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    @Named("callmebot")
    fun provideCallMeBotRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(CALLMEBOT_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideFelicityApiService(@Named("felicity") retrofit: Retrofit): FelicityApiService =
        retrofit.create(FelicityApiService::class.java)

    @Provides
    @Singleton
    fun provideCallMeBotApi(@Named("callmebot") retrofit: Retrofit): CallMeBotApi =
        retrofit.create(CallMeBotApi::class.java)
}
