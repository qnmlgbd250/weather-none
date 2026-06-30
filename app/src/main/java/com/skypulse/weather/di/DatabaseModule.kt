package com.skypulse.weather.di

import android.content.Context
import androidx.room.Room
import com.skypulse.weather.data.local.database.AppDatabase
import com.skypulse.weather.data.local.database.CityDao
import com.skypulse.weather.data.local.database.WeatherDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "skypulse_weather.db"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .enableMultiInstanceInvalidation()
        .build()

    @Provides
    fun provideWeatherDao(database: AppDatabase): WeatherDao = database.weatherDao()

    @Provides
    fun provideCityDao(database: AppDatabase): CityDao = database.cityDao()
}
