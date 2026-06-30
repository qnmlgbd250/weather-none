package com.skypulse.weather.sync

import android.util.Log
import androidx.core.content.ContextCompat
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.data.local.database.WeatherDao
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WeatherSyncManager — 唯一的数据生产者。
 *
 * 只有它可以：联网、刷新、同步、限流、合并请求、写数据库。
 * 其他模块（ViewModel / Widget / Notification）都不直接请求 API。
 *
 * Flow: WeatherSyncManager → WeatherRepository → Room → Flow → UI
 */
@Singleton
class WeatherSyncManager @Inject constructor(
    private val repository: WeatherRepository,
    private val cityRepository: CityRepository,
    private val locationManager: LocationManager,
    @Suppress("unused") private val weatherDao: WeatherDao
) {

    companion object {
        private const val TAG = "WeatherSyncMgr"
        private const val RATE_LIMIT_MS = 60_000L // 60s per city
        private const val MAX_RETRIES = 2
    }

    private val lastFetchTimesByCityId = ConcurrentHashMap<String, Long>()

    // ============ Public API ============

    /**
     * 为指定城市刷新天气（已知坐标）。
     * 包含：限流检查 → 网络请求（含重试）→ 写入 Room。
     */
    suspend fun refreshWeather(
        cityId: String,
        longitude: Double,
        latitude: Double
    ): SyncResult {
        if (isRecentlyFetched(cityId)) {
            return SyncResult.RateLimited
        }

        val result = fetchWithRetry(longitude, latitude)
        return result.fold(
            onSuccess = { response ->
                markFetched(cityId)
                repository.saveWeatherToCache(cityId, response)
                SyncResult.Success(response)
            },
            onFailure = { e ->
                SyncResult.Error(mapError(e))
            }
        )
    }

    /**
     * 完整的定位 + 天气刷新流程。
     * GPS 解析 → 更新当前城市坐标/名称 → 获取天气 → 写入 Room。
     * 用于主应用的定位城市刷新。
     */
    suspend fun refreshWeatherWithLocation(): SyncResult {
        val hasLocationPermission = hasLocationPermission()

        if (!hasLocationPermission) {
            // 无定位权限，使用默认坐标
            return refreshWeather(
                cityId = "current_location",
                longitude = LocationManager.DEFAULT_LONGITUDE,
                latitude = LocationManager.DEFAULT_LATITUDE
            )
        }

        // 有定位权限，带重试获取 GPS
        var amapLocation = requestLocationWithRetry()
        if (amapLocation != null) {
            val lon = amapLocation.longitude
            val lat = amapLocation.latitude
            val locationName = locationManager.resolveLocationName(amapLocation)
            locationManager.saveCachedLocation(locationName, lon, lat)
            updateCurrentLocationCity(locationName, lon, lat)

            val currentCity = getCurrentLocationCity()
            if (currentCity != null) {
                return refreshWeather(currentCity.id, lon, lat)
            }
        }

        // GPS 失败，尝试缓存位置
        val cachedLocation = locationManager.getCachedLocation()
        if (cachedLocation != null) {
            val currentCity = getCurrentLocationCity()
            if (currentCity != null) {
                return refreshWeather(
                    currentCity.id,
                    cachedLocation.longitude,
                    cachedLocation.latitude
                )
            }
        }

        // 全部失败
        return SyncResult.LocationFailed
    }

    /**
     * 使用默认坐标（北京）获取天气。
     * 用于首次安装无定位权限时的兜底。
     */
    suspend fun refreshWeatherDefault(): SyncResult {
        val currentCity = getCurrentLocationCity()
        val cityId = currentCity?.id ?: "current_location"
        return refreshWeather(
            cityId = cityId,
            longitude = LocationManager.DEFAULT_LONGITUDE,
            latitude = LocationManager.DEFAULT_LATITUDE
        )
    }

    /**
     * 检查指定城市是否最近已刷新过（用于 UI 层判断）。
     */
    fun isRecentlyFetched(cityId: String?): Boolean {
        if (cityId == null) return false
        val lastFetch = lastFetchTimesByCityId[cityId] ?: return false
        return System.currentTimeMillis() - lastFetch < RATE_LIMIT_MS
    }

    /**
     * 获取指定城市上次刷新的时间戳。
     * 用于 Worker 自定义过期判断（不同 Worker 有不同的 TTL）。
     */
    fun getLastFetchTime(cityId: String): Long {
        return lastFetchTimesByCityId[cityId] ?: 0L
    }

    // ============ Internal ============

    private suspend fun fetchWithRetry(
        lon: Double,
        lat: Double
    ): Result<WeatherResponse> {
        var lastException: Exception? = null
        repeat(MAX_RETRIES + 1) { attempt ->
            if (attempt > 0) {
                kotlinx.coroutines.delay(1000L * attempt)
            }
            val result = repository.getWeather(lon, lat, includeYesterday = true)
            result.fold(
                onSuccess = { response ->
                    val hourly = response.result?.hourly
                    if (hourly == null || hourly.temperature.isNullOrEmpty()) {
                        lastException = Exception("empty_hourly")
                    } else {
                        return Result.success(response)
                    }
                },
                onFailure = { e ->
                    lastException = e as? Exception ?: Exception(e)
                    if (e is HttpException && e.code() == 429) {
                        return Result.failure(e)
                    }
                }
            )
        }
        return Result.failure(lastException ?: Exception("未知错误"))
    }

    private suspend fun requestLocationWithRetry(): com.amap.api.location.AMapLocation? {
        for (attempt in 0..1) {
            val location = locationManager.requestAmapLocation()
            if (location != null) return location
            if (attempt < 1) {
                Log.w(TAG, "定位第${attempt + 1}次失败，1秒后重试")
                kotlinx.coroutines.delay(1_000L)
            }
        }
        return null
    }

    private fun markFetched(cityId: String) {
        lastFetchTimesByCityId[cityId] = System.currentTimeMillis()
    }

    private suspend fun updateCurrentLocationCity(name: String, lon: Double, lat: Double) {
        val currentCity = cityRepository.getCurrentLocationCity()
        if (currentCity != null) {
            cityRepository.updateCity(currentCity.copy(name = name, longitude = lon, latitude = lat))
        }
    }

    private suspend fun getCurrentLocationCity(): City? {
        return cityRepository.getCurrentLocationCity()
    }

    private fun hasLocationPermission(): Boolean {
        val ctx = locationManager.context
        return ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun mapError(e: Throwable): String = when {
        e is HttpException && e.code() == 429 -> "天气服务繁忙，请稍后再试"
        e is HttpException -> "网络请求失败，请检查网络连接"
        e.message?.contains("timeout", true) == true -> "网络连接超时，请检查网络"
        e.message?.contains("resolve", true) == true -> "无法连接到服务器，请检查网络"
        else -> "获取天气数据失败，请稍后重试"
    }
}

/**
 * 天气同步结果。
 */
sealed class SyncResult {
    data class Success(val weather: WeatherResponse) : SyncResult()
    data class Error(val message: String) : SyncResult()
    data object RateLimited : SyncResult()
    data object LocationFailed : SyncResult()

    inline fun <T> fold(
        onSuccess: (WeatherResponse) -> T,
        onFailure: (Throwable) -> T
    ): T = when (this) {
        is Success -> onSuccess(weather)
        is Error -> onFailure(Exception(message))
        is RateLimited -> onFailure(Exception("操作过于频繁，请稍后再试"))
        is LocationFailed -> onFailure(Exception("无法获取定位，请到室外空旷处重试"))
    }

    inline fun onSuccess(action: (WeatherResponse) -> Unit): SyncResult {
        if (this is Success) action(weather)
        return this
    }

    inline fun onFailure(action: (Throwable) -> Unit): SyncResult {
        if (this is Error) action(Exception(message))
        else if (this is RateLimited) action(Exception("操作过于频繁，请稍后再试"))
        else if (this is LocationFailed) action(Exception("无法获取定位，请到室外空旷处重试"))
        return this
    }

    fun getOrNull(): WeatherResponse? = (this as? Success)?.weather
}
