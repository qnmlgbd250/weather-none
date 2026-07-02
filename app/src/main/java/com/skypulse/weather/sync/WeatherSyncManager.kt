package com.skypulse.weather.sync

import android.util.Log
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.data.local.database.WeatherDao
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        private const val CURRENT_LOCATION_ID = "current_location"
        private const val LOCATING_NAME = "定位中..."
        private const val UNKNOWN_LOCATION = "未知位置"
        private const val RATE_LIMIT_MS = 60_000L // 60s per city
        private const val MAX_RETRIES = 2
    }

    private data class FetchRecord(
        val timeMillis: Long,
        val longitude: Double,
        val latitude: Double
    )

    private val lastFetchRecordsByCityId = ConcurrentHashMap<String, FetchRecord>()

    // ============ Public API ============

    /**
     * 为指定城市刷新天气（已知坐标）。
     * 包含：限流检查 → 网络请求（含重试）→ 写入 Room。
     */
    suspend fun refreshWeather(
        cityId: String,
        longitude: Double,
        latitude: Double
    ): SyncResult = withContext(Dispatchers.IO) {
        if (cityId == CURRENT_LOCATION_ID && isDefaultCoordinate(longitude, latitude)) {
            Log.i(TAG, "refreshWeather: 当前定位仍是占位坐标，改走定位刷新入口")
            return@withContext refreshWeatherWithLocation()
        }
        if (isRecentlyFetched(cityId)) {
            Log.i(TAG, "refreshWeather: $cityId 限流，跳过")
            return@withContext SyncResult.RateLimited
        }
        doRefreshWeather(cityId, longitude, latitude)
    }

    /**
     * 直接执行天气请求。调用方负责判断是否需要 60 秒限流。
     */
    private suspend fun doRefreshWeather(
        cityId: String,
        longitude: Double,
        latitude: Double
    ): SyncResult {
        // 只对同一城市、同一坐标的短时间重复请求做去重；定位坐标变化时必须刷新。
        val lastFetch = lastFetchRecordsByCityId[cityId]
        if (
            lastFetch != null &&
            System.currentTimeMillis() - lastFetch.timeMillis < 5000L &&
            isSameCoordinate(lastFetch, longitude, latitude)
        ) {
            Log.i(TAG, "doRefreshWeather: $cityId 5秒内同坐标已获取，跳过重复请求")
            val cached = repository.getWeatherFromCache(cityId)
            if (cached != null) return SyncResult.Success(cached)
        }
        FileLogger.i(TAG, "doRefreshWeather: cityId=$cityId, lon=$longitude, lat=$latitude")
        Log.i(TAG, "refreshWeather: $cityId 开始网络请求 lon=$longitude lat=$latitude")
        val result = fetchWithRetry(longitude, latitude)
        FileLogger.i(TAG, "doRefreshWeather: 网络请求完成, success=${result.isSuccess}")
        Log.i(TAG, "refreshWeather: 网络请求完成, success=${result.isSuccess}")
        return result.fold(
            onSuccess = { response ->
                markFetched(cityId, longitude, latitude)
                repository.saveWeatherToCache(cityId, response)
                FileLogger.i(TAG, "doRefreshWeather: 天气数据已写入 Room, cityId=$cityId, " +
                    "temp=${response.result?.realtime?.temperature}, " +
                    "skycon=${response.result?.realtime?.skycon}")
                SyncResult.Success(response)
            },
            onFailure = { e ->
                FileLogger.e(TAG, "doRefreshWeather: 网络请求失败 - ${mapError(e)}")
                SyncResult.Error(mapError(e))
            }
        )
    }

    /**
     * 完整的定位 + 天气刷新流程。
     * 解析定位 → 更新当前城市坐标/名称 → 获取天气 → 写入 Room。
     * 用于主应用的定位城市刷新（前台）。
     */
    suspend fun refreshWeatherWithLocation(): SyncResult = withContext(Dispatchers.IO) {
        val hasLocationPermission = locationManager.hasLocationPermission()
        Log.i(TAG, "refreshWeatherWithLocation: hasPermission=$hasLocationPermission")

        val currentBeforeLocation = getCurrentLocationCity()
        if (
            currentBeforeLocation != null &&
            !currentBeforeLocation.isUnresolvedCurrentLocation() &&
            isFreshEnough(currentBeforeLocation.id)
        ) {
            Log.i(TAG, "refreshWeatherWithLocation: current_location 60秒内已刷新，跳过")
            return@withContext SyncResult.RateLimited
        }

        val location = if (!hasLocationPermission) {
            Log.i(TAG, "无定位权限，尝试使用 IP 定位")
            locationManager.requestIpLocation()
        } else {
            locationManager.requestSystemOrIpLocation()
        }

        if (location != null) {
            val lon = location.longitude
            val lat = location.latitude
            val locationName = location.name
            Log.i(TAG, "定位成功: lon=$lon, lat=$lat, name=$locationName")
            val currentCity = if (locationName == UNKNOWN_LOCATION) {
                Log.w(TAG, "定位成功但地址为空，保留旧城市名, lon=$lon, lat=$lat")
                val oldName = locationManager.getCachedLocation()?.name
                    ?: getCurrentLocationCity()?.name
                    ?: LOCATING_NAME
                locationManager.saveCachedLocation(oldName, lon, lat)
                upsertCurrentLocationCity(oldName, lon, lat)
            } else {
                locationManager.saveCachedLocation(locationName, lon, lat)
                upsertCurrentLocationCity(locationName, lon, lat)
            }

            Log.i(TAG, "开始获取天气: cityId=${currentCity.id}, name=${currentCity.name}")
            return@withContext doRefreshWeather(currentCity.id, lon, lat)
        }

        val cachedLoc = locationManager.getCachedLocation()
        Log.i(TAG, "定位失败, cachedLocation=${cachedLoc?.name}")
        if (cachedLoc != null) {
            val currentCity = upsertCurrentLocationCity(
                cachedLoc.name,
                cachedLoc.longitude,
                cachedLoc.latitude
            )
            return@withContext doRefreshWeather(
                currentCity.id,
                cachedLoc.longitude,
                cachedLoc.latitude
            )
        }

        Log.w(TAG, "refreshWeatherWithLocation: 定位和缓存均失败，不写入默认北京天气")
        SyncResult.LocationFailed
    }

    /**
     * 小组件专用的定位 + 天气刷新流程。
     * 与 refreshWeatherWithLocation() 的区别：
     * 1. 不更新 Room 中的城市记录（避免污染主页的城市名显示）
     * 2. 仅更新天气缓存数据
     */
    suspend fun refreshWeatherWithLocationForWidget(): SyncResult = withContext(Dispatchers.IO) {
        val hasLocationPermission = locationManager.hasLocationPermission()
        val hasBackgroundPermission = locationManager.hasBackgroundLocationPermission()
        FileLogger.i(TAG, "refreshWeatherWithLocationForWidget: hasPermission=$hasLocationPermission, " +
            "hasBackgroundPermission=$hasBackgroundPermission")

        val location = if (!hasLocationPermission) {
            // 无定位权限，尝试使用 IP 定位
            FileLogger.i(TAG, "小组件: 无定位权限，尝试使用 IP 定位...")
            locationManager.requestIpLocation()
        } else {
            // 有定位权限，尝试系统或 IP 定位
            locationManager.requestSystemOrIpLocation()
        }

        if (location != null) {
            val lon = location.longitude
            val lat = location.latitude
            FileLogger.i(TAG, "小组件定位成功: lon=$lon, lat=$lat, name=${location.name}")
            // 只更新定位缓存，不更新 Room 城市记录
            if (location.name != "未知位置") {
                locationManager.saveCachedLocation(location.name, lon, lat)
            } else {
                val oldCachedName = locationManager.getCachedLocation()?.name
                locationManager.saveCachedLocation(oldCachedName ?: "未知位置", lon, lat)
            }
            return@withContext doRefreshWeather("current_location", lon, lat)
        }

        // 尝试缓存坐标
        val cachedLocation = locationManager.getCachedLocation()
        FileLogger.w(TAG, "小组件定位: 系统/IP定位均失败, cachedLocation=${cachedLocation?.name}")
        if (cachedLocation != null) {
            return@withContext doRefreshWeather(
                "current_location",
                cachedLocation.longitude,
                cachedLocation.latitude
            )
        }

        FileLogger.e(TAG, "小组件定位: 全部失败")
        SyncResult.LocationFailed
    }

    /**
     * 获取天气：优先用定位城市的坐标，没有则用手动添加的城市坐标，最后兜底北京。
     */
    suspend fun refreshWeatherDefault(): SyncResult = withContext(Dispatchers.IO) {
        val currentCity = getCurrentLocationCity()
        if (currentCity != null && !currentCity.isUnresolvedCurrentLocation()) {
            return@withContext doRefreshWeather(currentCity.id, currentCity.longitude, currentCity.latitude)
        }

        val cachedLocation = locationManager.getCachedLocation()
        if (cachedLocation != null) {
            val city = upsertCurrentLocationCity(
                cachedLocation.name,
                cachedLocation.longitude,
                cachedLocation.latitude
            )
            return@withContext doRefreshWeather(city.id, cachedLocation.longitude, cachedLocation.latitude)
        }

        val firstCity = cityRepository.getCities().firstOrNull { !it.isCurrentLocation }
        if (firstCity != null) {
            return@withContext doRefreshWeather(firstCity.id, firstCity.longitude, firstCity.latitude)
        }

        Log.w(TAG, "refreshWeatherDefault: 无定位缓存且无手动城市，不使用北京兜底")
        SyncResult.LocationFailed
    }

    /**
     * 检查指定城市是否最近已刷新过（用于 UI 层判断）。
     */
    fun isRecentlyFetched(cityId: String?): Boolean {
        if (cityId == null) return false
        val lastFetch = lastFetchRecordsByCityId[cityId] ?: return false
        return System.currentTimeMillis() - lastFetch.timeMillis < RATE_LIMIT_MS
    }

    suspend fun isFreshEnough(cityId: String?): Boolean = withContext(Dispatchers.IO) {
        if (cityId == null) return@withContext false
        if (isRecentlyFetched(cityId)) return@withContext true
        !repository.isCacheStale(cityId, RATE_LIMIT_MS)
    }



    private fun markFetched(cityId: String, longitude: Double, latitude: Double) {
        lastFetchRecordsByCityId[cityId] = FetchRecord(
            timeMillis = System.currentTimeMillis(),
            longitude = longitude,
            latitude = latitude
        )
    }

    private suspend fun upsertCurrentLocationCity(name: String, lon: Double, lat: Double): City {
        val currentCity = cityRepository.getCurrentLocationCity()
        if (currentCity != null) {
            val updated = currentCity.copy(name = name, longitude = lon, latitude = lat)
            cityRepository.updateCity(updated)
            return updated
        }

        val city = City(
            id = CURRENT_LOCATION_ID,
            name = name,
            longitude = lon,
            latitude = lat,
            isCurrentLocation = true
        )
        val cities = cityRepository.getCities()
            .filterNot { it.id == CURRENT_LOCATION_ID || it.isCurrentLocation }
        cityRepository.saveCities(listOf(city) + cities)
        return city
    }

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

    private fun mapError(e: Throwable): String = when {
        e is HttpException && e.code() == 429 -> "天气服务繁忙，请稍后再试"
        e is HttpException -> "网络请求失败，请检查网络连接"
        e.message?.contains("timeout", true) == true -> "网络连接超时，请检查网络"
        e.message?.contains("resolve", true) == true -> "无法连接到服务器，请检查网络"
        else -> "获取天气数据失败，请稍后重试"
    }

    private suspend fun getCurrentLocationCity(): City? {
        return cityRepository.getCurrentLocationCity()
    }

    private fun City.isUnresolvedCurrentLocation(): Boolean {
        return isCurrentLocation && isDefaultCoordinate(longitude, latitude)
    }

    private fun isDefaultCoordinate(longitude: Double, latitude: Double): Boolean {
        return kotlin.math.abs(longitude - LocationManager.DEFAULT_LONGITUDE) < 0.0001 &&
            kotlin.math.abs(latitude - LocationManager.DEFAULT_LATITUDE) < 0.0001
    }

    private fun isSameCoordinate(record: FetchRecord, longitude: Double, latitude: Double): Boolean {
        return locationManager.distanceBetween(
            record.latitude,
            record.longitude,
            latitude,
            longitude
        ) < 300f
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
