package com.skypulse.weather.di

import com.skypulse.weather.data.GeocodingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideGeocodingService(): GeocodingService = GeocodingService()
}
