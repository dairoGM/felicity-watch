package com.dairoroberto.felicitywatch.di

import android.content.Context
import androidx.room.Room
import com.dairoroberto.felicitywatch.data.local.AlertEventDao
import com.dairoroberto.felicitywatch.data.local.AlertRuleDao
import com.dairoroberto.felicitywatch.data.local.AppDatabase
import com.dairoroberto.felicitywatch.data.local.PowerReadingDao
import com.dairoroberto.felicitywatch.data.local.PushNotificationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            // Sin usuarios en producción todavía: destructiva es aceptable
            // en vez de escribir una migración formal para este cambio.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAlertRuleDao(database: AppDatabase): AlertRuleDao = database.alertRuleDao()

    @Provides
    fun provideAlertEventDao(database: AppDatabase): AlertEventDao = database.alertEventDao()

    @Provides
    fun providePowerReadingDao(database: AppDatabase): PowerReadingDao = database.powerReadingDao()

    @Provides
    fun providePushNotificationDao(database: AppDatabase): PushNotificationDao = database.pushNotificationDao()
}
