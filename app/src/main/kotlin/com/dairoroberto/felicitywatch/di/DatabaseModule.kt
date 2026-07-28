package com.dairoroberto.felicitywatch.di

import android.content.Context
import androidx.room.Room
import com.dairoroberto.felicitywatch.data.local.AlertEventDao
import com.dairoroberto.felicitywatch.data.local.AlertRuleDao
import com.dairoroberto.felicitywatch.data.local.AppDatabase
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
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()

    @Provides
    fun provideAlertRuleDao(database: AppDatabase): AlertRuleDao = database.alertRuleDao()

    @Provides
    fun provideAlertEventDao(database: AppDatabase): AlertEventDao = database.alertEventDao()
}
