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
            val resolved = resolveWidgetCity(context, cities, cityDataStore, cityManager)
            val city = resolved?.city

            if (city != null) {
                val cache = WeatherCache(context)
                val cached = cache.load(city.id)
                WeatherWidgetUpdater.updateAll(context, cached, city.name)

                val lastFetchTime = getLastFetchTime(context, city.id)
                val shouldFetch = cached == null || WidgetRefreshPolicy.shouldFetchWeather(
                    distanceMeters = resolved.distanceMeters,
                    lastFetchTimeMillis = lastFetchTime,
                    nowMillis = System.currentTimeMillis()
                )

                if (shouldFetch) {
                    try {
                        val api = createCaiyunApi(moshi)
                        val repo = WeatherRepository(api)
                        val result = repo.getWeather(city.longitude, city.latitude)
                        result.getOrNull()?.let { fresh ->
                            cache.save(city.id, fresh)
                            WeatherDataStore(context, moshi).save(city.id, fresh)
                            saveLastFetchTime(context, city.id, System.currentTimeMillis())
                            WeatherWidgetUpdater.updateAll(context, fresh, city.name)
                        }
                    } catch (e: Exception) {
                        Log.w("WidgetWorker", "API fetch failed, using cache", e)
                    }
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
    ): ResolvedWidgetCity? {
        val currentCity = cities.firstOrNull { it.isCurrentLocation }
        val locationManager = LocationManager(context)
        val amapLocation = if (locationManager.hasBackgroundLocationPermission()) {
            try {
                locationManager.requestLightweightAmapLocation()
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
                val distance = currentCity?.let {
                    distanceBetween(cached.latitude, cached.longitude, it.latitude, it.longitude)
                }
                return ResolvedWidgetCity(
                    city = saveCurrentLocationCity(
                        cities = cities,
                        currentCity = currentCity,
                        cityDataStore = cityDataStore,
                        cityManager = cityManager,
                        name = cached.name,
                        longitude = cached.longitude,
                        latitude = cached.latitude
                    ),
                    distanceMeters = distance
                )
            }
            return (currentCity ?: cities.firstOrNull())?.let { city ->
                ResolvedWidgetCity(city = city, distanceMeters = 0f)
            }
        }

        val lon = amapLocation.longitude
        val lat = amapLocation.latitude
        val distance = currentCity?.let { distanceBetween(lat, lon, it.latitude, it.longitude) }
        val savedName = currentCity?.name?.takeIf { it.isNotBlank() }
        val locationName = if (savedName != null && !WidgetRefreshPolicy.hasMovedSignificantly(distance)) {
            savedName
        } else {
            locationManager.resolveLocationName(amapLocation)
        }
        locationManager.saveCachedLocation(locationName, lon, lat)

        return ResolvedWidgetCity(
            city = saveCurrentLocationCity(
                cities = cities,
                currentCity = currentCity,
                cityDataStore = cityDataStore,
                cityManager = cityManager,
                name = locationName,
                longitude = lon,
                latitude = lat
            ),
            distanceMeters = distance
        )
    }

    private data class ResolvedWidgetCity(
        val city: City,
        val distanceMeters: Float?
    )

    private fun getLastFetchTime(context: Context, cityId: String): Long? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("$KEY_LAST_FETCH_PREFIX$cityId", 0L)
        return value.takeIf { it > 0L }
    }

    private fun saveLastFetchTime(context: Context, cityId: String, timeMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("$KEY_LAST_FETCH_PREFIX$cityId", timeMillis)
            .apply()
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

    companion object {
        private const val PREFS_NAME = "weather_widget_refresh"
        private const val KEY_LAST_FETCH_PREFIX = "last_fetch_"
    }
}
