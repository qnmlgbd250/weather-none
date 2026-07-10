package com.skypulse.weather.data.provider

import com.skypulse.weather.data.provider.model.SimpleLocation

interface ILocationProvider {
    suspend fun requestLocation(highAccuracy: Boolean, timeoutMillis: Long): SimpleLocation?
}
