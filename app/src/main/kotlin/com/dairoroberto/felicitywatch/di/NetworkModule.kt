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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
