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
import com.skypulse.weather.model.City
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.util.FileLogger
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class WeatherWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val trigger = inputData.getString("trigger") ?: "periodic"
        FileLogger.i("WidgetRefresh", "【WorkManager刷新】doWork 触发, trigger=$trigger, runAttemptCount=$runAttemptCount")
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
                        val result = repo.getWidgetWeather(city.longitude, city.latitude)
                        result.getOrNull()?.let { fresh ->
                            cache.save(city.id, fresh)
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
                requestLightweightAmapLocationWithRetry(locationManager)
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
            val resolved = locationManager.resolveLocationName(amapLocation)
            if (resolved == "未知位置") {
                Log.w("WidgetWorker", "resolveLocationName返回未知位置, lat=$lat, lon=$lon")
                val geocoded = locationManager.reverseGeocode(lat, lon)
                if (geocoded != "未知位置") {
                    Log.i("WidgetWorker", "逆地理编码成功: geocoded=$geocoded")
                    geocoded
                } else if (savedName != null && savedName != "未知位置") {
                    Log.i("WidgetWorker", "逆地理编码失败，回退到前一个位置: $savedName")
                    savedName
                } else {
                    Log.w("WidgetWorker", "所有位置解析均失败，无回退可用")
                    resolved
                }
            } else {
                resolved
            }
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

    /**
     * 带重试的轻量定位：第一次 12 秒超时，失败后等 1 秒再用 15 秒超时重试。
     * 比主 App 的 Hight_Accuracy（15s）更省电，但比原来的 8s 更可靠。
     */
    private suspend fun requestLightweightAmapLocationWithRetry(
        locationManager: LocationManager
    ): com.amap.api.location.AMapLocation? {
        val timeouts = longArrayOf(12_000L, 15_000L)
        for ((index, timeout) in timeouts.withIndex()) {
            try {
                val location = locationManager.requestLightweightAmapLocation(timeout)
                if (location != null) {
                    if (index > 0) Log.i("WidgetWorker", "定位重试成功 (第${index + 1}次)")
                    return location
                }
                Log.w("WidgetWorker", "定位第${index + 1}次失败 (timeout=${timeout}ms)")
            } catch (e: Exception) {
                Log.w("WidgetWorker", "定位第${index + 1}次异常", e)
            }
            if (index < timeouts.lastIndex) delay(1_000L)
        }
        return null
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
