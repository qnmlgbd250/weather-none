package com.skypulse.weather.di

import android.content.Context
import com.skypulse.weather.data.CityDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideCityDatabase(
        @ApplicationContext context: Context
    ): CityDatabase = CityDatabase(context)
}
