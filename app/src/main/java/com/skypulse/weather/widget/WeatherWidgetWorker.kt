package com.skypulse.weather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.repository.WeatherRepository

class WeatherWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val cities = CityManager(context).getCities()
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
            if (city != null) {
                val cache = WeatherCache(context)
                val repo = WeatherRepository()
                val cached = cache.load(city.id)
                if (cached == null) {
                    repo.getWeather(city.longitude, city.latitude).getOrNull()?.let {
                        cache.save(city.id, it)
                    }
                } else {
                    repo.getWeather(city.longitude, city.latitude).getOrNull()?.let {
                        cache.save(city.id, it)
                    }
                }
            }
            WeatherWidgetUpdater.updateAll(context)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
