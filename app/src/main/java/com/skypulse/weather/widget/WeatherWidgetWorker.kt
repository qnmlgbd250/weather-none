package com.skypulse.weather.widget

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.api.CaiyunApi
import com.skypulse.weather.data.CityDataStore
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.data.WeatherDataStore
import com.skypulse.weather.model.City
import com.skypulse.weather.repository.WeatherRepository
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class WeatherWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val moshi = Moshi.Builder().build()
            val cityDataStore = CityDataStore(context, moshi)
            val cityManager = CityManager(context, moshi)
            val cities = cityDataStore.getCities().ifEmpty { cityManager.getCities() }
            val city = resolveWidgetCity(context, cities, cityDataStore, cityManager)

            if (city != null) {
                val cache = WeatherCache(context)
                // 先用缓存立即刷新 UI
                val cached = cache.load(city.id)
                WeatherWidgetUpdater.updateAll(context, cached, city.name)

                // 再从 API 拉取最新数据
                try {
                    val api = createCaiyunApi(moshi)
                    val repo = WeatherRepository(api)
                    val result = repo.getWeather(city.longitude, city.latitude)
                    result.getOrNull()?.let { fresh ->
                        cache.save(city.id, fresh)
                        WeatherDataStore(context, moshi).save(city.id, fresh)
                        // 用最新数据再次刷新 UI
                        WeatherWidgetUpdater.updateAll(context, fresh, city.name)
                    }
                } catch (e: Exception) {
                    Log.w("WidgetWorker", "API fetch failed, using cache", e)
                }
            } else {
                WeatherWidgetUpdater.updateAll(context, null, null)
            }
            Result.success()
        } catch (_: Exception) {
            try { WeatherWidgetUpdater.updateAll(applicationContext, null, null) } catch (_: Exception) {}
            Result.success()
        }
    }

    private suspend fun resolveWidgetCity(
        context: Context,
        cities: List<City>,
        cityDataStore: CityDataStore,
        cityManager: CityManager
    ): City? {
        val currentCity = cities.firstOrNull { it.isCurrentLocation }
        val locationManager = LocationManager(context)
        val amapLocation = if (locationManager.hasBackgroundLocationPermission()) {
            try {
                locationManager.requestAmapLocation()
            } catch (e: Exception) {
                Log.w("WidgetWorker", "Location fetch failed, using saved city", e)
                null
            }
        } else {
            Log.w("WidgetWorker", "Background location permission not granted, using cache")
            null
        }

        if (amapLocation == null) {
            locationManager.getCachedLocation()?.let { cached ->
                return saveCurrentLocationCity(
                    cities = cities,
                    currentCity = currentCity,
                    cityDataStore = cityDataStore,
                    cityManager = cityManager,
                    name = cached.name,
                    longitude = cached.longitude,
                    latitude = cached.latitude
                )
            }
            return currentCity ?: cities.firstOrNull()
        }

        val lon = amapLocation.longitude
        val lat = amapLocation.latitude
        val savedName = currentCity?.name?.takeIf { it.isNotBlank() }
        val distance = currentCity?.let { distanceBetween(lat, lon, it.latitude, it.longitude) }
        val locationName = if (savedName != null && distance != null && distance < 500f) {
            savedName
        } else {
            locationManager.resolveLocationName(amapLocation)
        }
        locationManager.saveCachedLocation(locationName, lon, lat)

        return saveCurrentLocationCity(
            cities = cities,
            currentCity = currentCity,
            cityDataStore = cityDataStore,
            cityManager = cityManager,
            name = locationName,
            longitude = lon,
            latitude = lat
        )
    }

    private suspend fun saveCurrentLocationCity(
        cities: List<City>,
        currentCity: City?,
        cityDataStore: CityDataStore,
        cityManager: CityManager,
        name: String,
        longitude: Double,
        latitude: Double
    ): City {
        val updatedCity = (currentCity ?: City(
            id = "current_location",
            name = name,
            longitude = longitude,
            latitude = latitude,
            isCurrentLocation = true
        )).copy(
            name = name,
            longitude = longitude,
            latitude = latitude,
            isCurrentLocation = true
        )
        val updatedCities = if (currentCity != null) {
            cities.map { if (it.isCurrentLocation) updatedCity else it }
        } else {
            listOf(updatedCity) + cities
        }

        cityDataStore.saveCities(updatedCities)
        cityManager.saveCities(updatedCities)
        return updatedCity
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun createCaiyunApi(moshi: Moshi): CaiyunApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://wrapper.cyapi.cn/")
            .client(client)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(CaiyunApi::class.java)
    }
}
