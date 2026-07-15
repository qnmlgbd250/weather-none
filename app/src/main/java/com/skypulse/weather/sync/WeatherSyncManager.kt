package com.skypulse.weather.sync

import android.util.Log
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.data.local.database.WeatherDao
import com.skypulse.weather.data.remote.SkyconCalibrator
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.util.FileLogger
import com.skypulse.weather.util.WeatherUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val skyconCalibrator: SkyconCalibrator,
    @Suppress("unused") private val weatherDao: WeatherDao
) {

    companion object {
        private const val TAG = "WeatherSyncMgr"
        private const val CURRENT_LOCATION_ID = "current_location"
        private const val LOCATING_NAME = "定位中..."
        private const val UNKNOWN_LOCATION = "未知位置"
        private const val MAX_RETRIES = 2
        private const val WEATHER_FETCH_ATTEMPT_TIMEOUT_MS = 12_000L
        private const val WEATHER_FETCH_TOTAL_TIMEOUT_MS = 40_000L
        private const val WIDGET_WEATHER_FETCH_MAX_RETRIES = 0
        private const val WIDGET_WEATHER_FETCH_ATTEMPT_TIMEOUT_MS = 6_000L
        private const val WIDGET_WEATHER_FETCH_TOTAL_TIMEOUT_MS = 7_000L
        private const val LOCATION_CALIBRATION_MIN_INTERVAL_MS = 7 * 60 * 1000L
        private const val LOCATION_CALIBRATION_FORCE_INTERVAL_MS = 30 * 60 * 1000L
        private const val LOCATION_CALIBRATION_DISTANCE_METERS = 700f
    }

    private data class FetchOptions(
        val maxRetries: Int,
        val attemptTimeoutMillis: Long,
        val totalTimeoutMillis: Long,
        val retryDelayMillis: Long = 1_000L
    )

    private data class FetchRecord(
        val timeMillis: Long,
        val longitude: Double,
        val latitude: Double
    )

    private val lastFetchRecordsByCityId = ConcurrentHashMap<String, FetchRecord>()
    private val locationMutex = Mutex()
    private val cityMutexes = ConcurrentHashMap<String, Mutex>()
    @Volatile
    private var lastLocationCalibrationMillis: Long = 0L
    private fun getMutexForCity(cityId: String) = cityMutexes.computeIfAbsent(cityId) { Mutex() }

    private fun locI(message: String) = FileLogger.locI(TAG, message)
    private fun locW(message: String) = FileLogger.locW(TAG, message)
    private fun locE(message: String) = FileLogger.locE(TAG, message)
    private fun weatherI(message: String) = FileLogger.weatherI(TAG, message)
    private fun weatherW(message: String) = FileLogger.weatherW(TAG, message)
    private fun weatherE(message: String) = FileLogger.weatherE(TAG, message)
    private fun elapsedSince(startMs: Long): Long = android.os.SystemClock.elapsedRealtime() - startMs

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
        latitude: Double,
        fetchOptions: FetchOptions = FetchOptions(
            maxRetries = MAX_RETRIES,
            attemptTimeoutMillis = WEATHER_FETCH_ATTEMPT_TIMEOUT_MS,
            totalTimeoutMillis = WEATHER_FETCH_TOTAL_TIMEOUT_MS
        )
    ): SyncResult {
        val mutex = getMutexForCity(cityId)
        val waitStartMs = android.os.SystemClock.elapsedRealtime()
        locI("city_mutex_wait_start: cityId=$cityId, lon=$longitude, lat=$latitude")
        return mutex.withLock {
            locI("city_mutex_acquired: cityId=$cityId, wait=${elapsedSince(waitStartMs)}ms")
            val startMs = android.os.SystemClock.elapsedRealtime()
            // 只对同一城市、同一坐标的短时间重复请求做去重；定位坐标变化时必须刷新。
            // 获锁后进行二次检查：防止在排队等待期间，前一个请求已经成功刷新了天气，当前请求可直接复用缓存。
            val lastFetch = lastFetchRecordsByCityId[cityId]
            if (
                lastFetch != null &&
                RefreshPolicy.isSameCoordinateDedupeWindow(System.currentTimeMillis(), lastFetch.timeMillis) &&
                isSameCoordinate(lastFetch, longitude, latitude)
            ) {
                Log.i(TAG, "doRefreshWeather: $cityId 排队后检查：5秒内同坐标已获取，跳过重复请求")
                val cached = repository.getWeatherFromCache(cityId)
                locI("city_refresh_deduped_after_wait: cityId=$cityId, elapsed=${elapsedSince(startMs)}ms, hasCache=${cached != null}")
                if (cached != null) return@withLock SyncResult.Success(cached)
            }
            FileLogger.i(TAG, "doRefreshWeather: cityId=$cityId, lon=$longitude, lat=$latitude")
            Log.i(TAG, "refreshWeather: $cityId 开始网络请求 lon=$longitude lat=$latitude")
            locI("weather_fetch_start: cityId=$cityId, lon=$longitude, lat=$latitude")
            weatherI("weather_fetch_start: cityId=$cityId, lon=$longitude, lat=$latitude")
            val fetchStartMs = android.os.SystemClock.elapsedRealtime()
            val result = fetchWithRetry(longitude, latitude, fetchOptions)
            locI("weather_fetch_done: cityId=$cityId, elapsed=${elapsedSince(fetchStartMs)}ms, success=${result.isSuccess}")
            weatherI("weather_fetch_done: cityId=$cityId, elapsed=${elapsedSince(fetchStartMs)}ms, success=${result.isSuccess}")
            FileLogger.i(TAG, "doRefreshWeather: 网络请求完成, success=${result.isSuccess}")
            Log.i(TAG, "refreshWeather: 网络请求完成, success=${result.isSuccess}")
            result.fold(
                onSuccess = { rawResponse ->
                    // 当前定位城市：校准彩云的"阴天"偏差
                    val response = if (cityId == CURRENT_LOCATION_ID) {
                        calibrateSkyconIfNeeded(rawResponse, longitude, latitude)
                    } else {
                        rawResponse
                    }

                    markFetched(cityId, longitude, latitude)
                    val saveStartMs = android.os.SystemClock.elapsedRealtime()
                    repository.saveWeatherToCache(cityId, response)
                    locI("weather_cache_saved: cityId=$cityId, elapsed=${elapsedSince(saveStartMs)}ms, total=${elapsedSince(startMs)}ms")
                    weatherI("weather_cache_saved: cityId=$cityId, elapsed=${elapsedSince(saveStartMs)}ms, total=${elapsedSince(startMs)}ms")
                    FileLogger.i(TAG, "doRefreshWeather: 天气数据已写入 Room, cityId=$cityId, " +
                        "temp=${response.result?.realtime?.temperature}, " +
                        "skycon=${response.result?.realtime?.skycon}")
                    SyncResult.Success(response)
                },
                onFailure = { e ->
                    FileLogger.e(TAG, "doRefreshWeather: 网络请求失败 - ${mapError(e)}")
                    locE("weather_fetch_failed: cityId=$cityId, total=${elapsedSince(startMs)}ms, error=${mapError(e)}")
                    weatherE("weather_fetch_failed: cityId=$cityId, total=${elapsedSince(startMs)}ms, error=${mapError(e)}")
                    SyncResult.Error(mapError(e))
                }
            )
        }
    }

    /**
     * 完整的定位 + 天气刷新流程。
     * 解析定位 → 更新当前城市坐标/名称 → 获取天气 → 写入 Room。
     * 用于主应用的定位城市刷新（前台）。
     */
    suspend fun refreshWeatherWithLocation(highAccuracy: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val waitStartMs = android.os.SystemClock.elapsedRealtime()
        locI("location_mutex_wait_start: highAccuracy=$highAccuracy")
        locationMutex.withLock {
            val startMs = android.os.SystemClock.elapsedRealtime()
            locI("location_mutex_acquired: wait=${elapsedSince(waitStartMs)}ms, highAccuracy=$highAccuracy")
            val hasLocationPermission = locationManager.hasLocationPermission()
            Log.i(TAG, "refreshWeatherWithLocation: hasPermission=$hasLocationPermission, highAccuracy=$highAccuracy")
            locI("refresh_with_location_start: hasPermission=$hasLocationPermission, highAccuracy=$highAccuracy")

            // 加锁后再次读取 Room 中定位城市信息做新鲜度校验（双重检查）
            val currentBeforeLocation = getCurrentLocationCity()
            if (
                !highAccuracy &&
                currentBeforeLocation != null &&
                !currentBeforeLocation.isUnresolvedCurrentLocation() &&
                isFreshEnough(currentBeforeLocation.id)
            ) {
                Log.i(TAG, "refreshWeatherWithLocation: current_location 120秒内已刷新，跳过")
                val cached = repository.getWeatherFromCache(currentBeforeLocation.id)
                locI("refresh_with_location_fresh_skip: elapsed=${elapsedSince(startMs)}ms, hasCache=${cached != null}")
                return@withLock if (cached != null) SyncResult.Success(cached) else SyncResult.RateLimited
            }

            val locateStartMs = android.os.SystemClock.elapsedRealtime()
            val location = if (!hasLocationPermission) {
                Log.i(TAG, "无定位权限，IP定位已剔除，直接跳过定位")
                locW("refresh_with_location_no_permission: elapsed=${elapsedSince(startMs)}ms")
                null
            } else {
                locationManager.requestBestLocation(highAccuracy = highAccuracy)
            }
            locI("refresh_with_location_locate_done: elapsed=${elapsedSince(locateStartMs)}ms, result=${location?.let { "lat=${it.latitude}, lon=${it.longitude}, accuracy=${it.accuracy}m, name=${it.name}" } ?: "null"}")

            if (location != null) {
                lastLocationCalibrationMillis = System.currentTimeMillis()
                val lon = location.longitude
                val lat = location.latitude
                val locationName = location.name
                Log.i(TAG, "定位成功: lon=$lon, lat=$lat, name=$locationName, isReliableName=${location.isReliableName}")
                locI("refresh_with_location_location_success: elapsed=${elapsedSince(startMs)}ms, lon=$lon, lat=$lat, accuracy=${location.accuracy}m, name=$locationName, isReliableName=${location.isReliableName}")
                val currentCity = if (locationName == UNKNOWN_LOCATION) {
                    Log.w(TAG, "定位成功但地址为空，保留旧城市名, lon=$lon, lat=$lat")
                    var oldName = locationManager.getCachedLocation()?.name
                        ?: getCurrentLocationCity()?.name
                        ?: "当前位置"
                    if (oldName == LOCATING_NAME || oldName.isBlank()) {
                        oldName = "当前位置"
                    }
                    locationManager.saveCachedLocation(oldName, lon, lat, location.time, location.accuracy, isReliableName = true)
                    upsertCurrentLocationCity(oldName, lon, lat)
                } else {
                    // saveCachedLocation 内部会在 isReliableName=false 时沿用上一次正常名称
                    locationManager.saveCachedLocation(locationName, lon, lat, location.time, location.accuracy, location.isReliableName)
                    // 读回实际保存的名称（可能已被替换为 lastGoodName），保证 Room 一致
                    val savedName = locationManager.getCachedLocation()?.name ?: locationName
                    upsertCurrentLocationCity(savedName, lon, lat)
                }

                Log.i(TAG, "开始获取天气: cityId=${currentCity.id}, name=${currentCity.name}")
                val result = doRefreshWeather(currentCity.id, lon, lat)
                locI("refresh_with_location_complete: elapsed=${elapsedSince(startMs)}ms, result=${result::class.simpleName}")
                return@withLock result
            }

            val cachedLoc = locationManager.getCachedLocation()
            Log.i(TAG, "定位失败, cachedLocation=${cachedLoc?.name}")
            locW("refresh_with_location_locate_failed: elapsed=${elapsedSince(startMs)}ms, cached=${cachedLoc?.name}")
            if (cachedLoc != null) {
                val currentCity = upsertCurrentLocationCity(
                    cachedLoc.name,
                    cachedLoc.longitude,
                    cachedLoc.latitude
                )
                val result = doRefreshWeather(
                    currentCity.id,
                    cachedLoc.longitude,
                    cachedLoc.latitude
                )
                locI("refresh_with_location_cached_complete: elapsed=${elapsedSince(startMs)}ms, result=${result::class.simpleName}")
                return@withLock result
            }

            Log.w(TAG, "refreshWeatherWithLocation: 定位和缓存均失败，不写入默认北京天气")
            locW("refresh_with_location_failed_no_cache: elapsed=${elapsedSince(startMs)}ms")
            SyncResult.LocationFailed
        }
    }

    /**
     * 首页快路径：不做阻塞式定位，优先用已确认的当前定位城市坐标或定位缓存刷新天气。
     * 准确性由后台 calibrateCurrentLocation() 持续校准。
     */
    suspend fun refreshCurrentLocationFast(): SyncResult = withContext(Dispatchers.IO) {
        val startMs = android.os.SystemClock.elapsedRealtime()
        locI("current_location_fast_start")
        val currentCity = getCurrentLocationCity()
        if (currentCity != null && !currentCity.isUnresolvedCurrentLocation()) {
            locI("current_location_fast_city_coords: elapsed=${elapsedSince(startMs)}ms, cityId=${currentCity.id}, lon=${currentCity.longitude}, lat=${currentCity.latitude}")
            return@withContext doRefreshWeather(currentCity.id, currentCity.longitude, currentCity.latitude)
        }

        val cached = locationManager.getCachedLocation()
        if (cached != null) {
            val city = upsertCurrentLocationCity(cached.name, cached.longitude, cached.latitude)
            locI("current_location_fast_cached_coords: elapsed=${elapsedSince(startMs)}ms, cityId=${city.id}, lon=${cached.longitude}, lat=${cached.latitude}, name=${cached.name}")
            return@withContext doRefreshWeather(city.id, cached.longitude, cached.latitude)
        }

        locW("current_location_fast_no_trusted_coords: elapsed=${elapsedSince(startMs)}ms")
        refreshWeatherWithLocation(highAccuracy = false)
    }

    /**
     * 后台定位校准：保持当前位置最终准确，但不参与首页可见刷新等待。
     */
    suspend fun calibrateCurrentLocation(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sinceLast = now - lastLocationCalibrationMillis
        if (!force && sinceLast >= 0L && sinceLast < LOCATION_CALIBRATION_MIN_INTERVAL_MS) {
            locI("location_calibration_skip_recent: sinceLast=${sinceLast}ms")
            return@withContext SyncResult.RateLimited
        }

        val waitStartMs = android.os.SystemClock.elapsedRealtime()
        locI("location_calibration_mutex_wait_start: force=$force, sinceLast=${sinceLast}ms")
        locationMutex.withLock {
            val startMs = android.os.SystemClock.elapsedRealtime()
            val lockedSinceLast = System.currentTimeMillis() - lastLocationCalibrationMillis
            if (!force && lockedSinceLast >= 0L && lockedSinceLast < LOCATION_CALIBRATION_MIN_INTERVAL_MS) {
                locI("location_calibration_skip_recent_after_wait: wait=${elapsedSince(waitStartMs)}ms, sinceLast=${lockedSinceLast}ms")
                return@withLock SyncResult.RateLimited
            }

            if (!locationManager.hasLocationPermission()) {
                locW("location_calibration_no_permission: wait=${elapsedSince(waitStartMs)}ms")
                return@withLock SyncResult.LocationFailed
            }

            val location = locationManager.requestBestLocation(highAccuracy = false)
            lastLocationCalibrationMillis = System.currentTimeMillis()
            if (location == null) {
                locW("location_calibration_failed: elapsed=${elapsedSince(startMs)}ms")
                return@withLock SyncResult.LocationFailed
            }

            val currentCity = getCurrentLocationCity()
            val shouldRefreshWeather = shouldRefreshAfterLocationCalibration(currentCity, location, force)
            val cityName = if (location.name == UNKNOWN_LOCATION || location.name.isBlank()) {
                currentCity?.name?.takeIf { it != LOCATING_NAME && it.isNotBlank() }
                    ?: locationManager.getCachedLocation()?.name
                    ?: "当前位置"
            } else {
                location.name
            }
            // 传递 isReliableName，不可靠时 saveCachedLocation 会沿用上一次正常名称
            locationManager.saveCachedLocation(cityName, location.longitude, location.latitude, location.time, location.accuracy, location.isReliableName)
            val savedName = locationManager.getCachedLocation()?.name ?: cityName
            val city = upsertCurrentLocationCity(savedName, location.longitude, location.latitude)
            locI("location_calibration_location_saved: elapsed=${elapsedSince(startMs)}ms, shouldRefreshWeather=$shouldRefreshWeather, lon=${location.longitude}, lat=${location.latitude}, accuracy=${location.accuracy}m, name=$cityName")

            if (shouldRefreshWeather) {
                val result = doRefreshWeather(city.id, location.longitude, location.latitude)
                locI("location_calibration_weather_complete: elapsed=${elapsedSince(startMs)}ms, result=${result::class.simpleName}")
                result
            } else {
                val cachedWeather = repository.getWeatherFromCache(city.id)
                if (cachedWeather != null) SyncResult.Success(cachedWeather) else SyncResult.RateLimited
            }
        }
    }

    /**
     * 小组件专用的定位 + 天气刷新流程。
     * 与 refreshWeatherWithLocation() 的区别：
     * 1. 不更新 Room 中的城市记录（避免污染主页的城市名显示）
     * 2. 仅更新天气缓存数据
     */
    suspend fun refreshWeatherWithLocationForWidget(): SyncResult = withContext(Dispatchers.IO) {
        val waitStartMs = android.os.SystemClock.elapsedRealtime()
        locI("widget_location_mutex_wait_start")
        locationMutex.withLock {
            val startMs = android.os.SystemClock.elapsedRealtime()
            locI("widget_location_mutex_acquired: wait=${elapsedSince(waitStartMs)}ms")
            val hasLocationPermission = locationManager.hasLocationPermission()
            val hasBackgroundPermission = locationManager.hasBackgroundLocationPermission()
            FileLogger.i(TAG, "refreshWeatherWithLocationForWidget: hasPermission=$hasLocationPermission, " +
                "hasBackgroundPermission=$hasBackgroundPermission")

            // 加锁后再次读取定位城市信息做新鲜度校验（双重检查）
            if (isFreshEnough(CURRENT_LOCATION_ID)) {
                FileLogger.i(TAG, "refreshWeatherWithLocationForWidget: current_location 120秒内已刷新，跳过")
                val cached = repository.getWeatherFromCache(CURRENT_LOCATION_ID)
                locI("widget_refresh_fresh_skip: elapsed=${elapsedSince(startMs)}ms, hasCache=${cached != null}")
                return@withLock if (cached != null) SyncResult.Success(cached) else SyncResult.RateLimited
            }

            val locateStartMs = android.os.SystemClock.elapsedRealtime()
            val location = if (!hasLocationPermission) {
                FileLogger.i(TAG, "小组件: 无定位权限，IP定位已剔除，直接跳过定位")
                locW("widget_refresh_no_permission: elapsed=${elapsedSince(startMs)}ms")
                null
            } else {
                locationManager.requestBestLocation()
            }
            locI("widget_refresh_locate_done: elapsed=${elapsedSince(locateStartMs)}ms, result=${location?.let { "lat=${it.latitude}, lon=${it.longitude}, accuracy=${it.accuracy}m, name=${it.name}" } ?: "null"}")

            if (location != null) {
                val lon = location.longitude
                val lat = location.latitude
                FileLogger.i(TAG, "小组件定位成功: lon=$lon, lat=$lat, name=${location.name}, isReliableName=${location.isReliableName}")
                // 只更新定位缓存，不更新 Room 城市记录
                if (location.name != "未知位置") {
                    locationManager.saveCachedLocation(location.name, lon, lat, location.time, location.accuracy, location.isReliableName)
                } else {
                    val oldCachedName = locationManager.getCachedLocation()?.name
                    locationManager.saveCachedLocation(oldCachedName ?: "未知位置", lon, lat, location.time, location.accuracy, isReliableName = true)
                }
                val result = doRefreshWeather(
                    "current_location",
                    lon,
                    lat,
                    fetchOptions = FetchOptions(
                        maxRetries = WIDGET_WEATHER_FETCH_MAX_RETRIES,
                        attemptTimeoutMillis = WIDGET_WEATHER_FETCH_ATTEMPT_TIMEOUT_MS,
                        totalTimeoutMillis = WIDGET_WEATHER_FETCH_TOTAL_TIMEOUT_MS
                    )
                )
                locI("widget_refresh_complete: elapsed=${elapsedSince(startMs)}ms, result=${result::class.simpleName}")
                return@withLock result
            }

            // 尝试缓存坐标
            val cachedLocation = locationManager.getCachedLocation()
            FileLogger.w(TAG, "小组件定位: 定位均失败, cachedLocation=${cachedLocation?.name}")
            if (cachedLocation != null) {
                val result = doRefreshWeather(
                    "current_location",
                    cachedLocation.longitude,
                    cachedLocation.latitude,
                    fetchOptions = FetchOptions(
                        maxRetries = WIDGET_WEATHER_FETCH_MAX_RETRIES,
                        attemptTimeoutMillis = WIDGET_WEATHER_FETCH_ATTEMPT_TIMEOUT_MS,
                        totalTimeoutMillis = WIDGET_WEATHER_FETCH_TOTAL_TIMEOUT_MS
                    )
                )
                locI("widget_refresh_cached_complete: elapsed=${elapsedSince(startMs)}ms, result=${result::class.simpleName}")
                return@withLock result
            }

            FileLogger.e(TAG, "小组件定位: 全部失败")
            locW("widget_refresh_failed_no_cache: elapsed=${elapsedSince(startMs)}ms")
            SyncResult.LocationFailed
        }
    }

    /**
     * 获取天气：优先用定位城市的坐标，没有则用定位缓存坐标。
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

        Log.w(TAG, "refreshWeatherDefault: 无定位缓存，无法获取天气")
        SyncResult.LocationFailed
    }

    /**
     * 检查指定城市是否最近已刷新过（用于 UI 层判断）。
     */
    fun isRecentlyFetched(cityId: String?): Boolean {
        if (cityId == null) return false
        val lastFetch = lastFetchRecordsByCityId[cityId] ?: return false
        return RefreshPolicy.isCityRateLimited(System.currentTimeMillis(), lastFetch.timeMillis)
    }

    suspend fun isFreshEnough(cityId: String?): Boolean = withContext(Dispatchers.IO) {
        if (cityId == null) return@withContext false
        if (isRecentlyFetched(cityId)) return@withContext true
        !repository.isCacheStale(cityId, RefreshPolicy.CITY_RATE_LIMIT_MS)
    }



    /**
     * 校准彩云天气的"阴天"偏差。
     * 仅在 skycon == "CLOUDY" 时触发小米天气请求。
     * 校准成功则覆盖 skycon，失败则保持原值。
     */
    private suspend fun calibrateSkyconIfNeeded(
        response: WeatherResponse,
        longitude: Double,
        latitude: Double
    ): WeatherResponse {
        val originalSkycon = response.result?.realtime?.skycon
        val isDay = WeatherUtils.isCurrentlyDay(response.result?.daily)
        val calibratedSkycon = skyconCalibrator.calibrateIfNeeded(originalSkycon, longitude, latitude, isDay)
        if (calibratedSkycon == originalSkycon) {
            return response
        }
        weatherI("skycon_calibrated: $originalSkycon → $calibratedSkycon, isDay=$isDay, lon=$longitude, lat=$latitude")
        return response.copy(
            result = response.result?.copy(
                realtime = response.result.realtime?.copy(skycon = calibratedSkycon)
            )
        )
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

    private suspend fun shouldRefreshAfterLocationCalibration(
        currentCity: City?,
        location: LocationManager.CachedLocation,
        force: Boolean
    ): Boolean {
        if (force) return true
        if (currentCity == null || currentCity.isUnresolvedCurrentLocation()) return true
        if (repository.getWeatherFromCache(currentCity.id) == null) return true

        val distance = locationManager.distanceBetween(
            currentCity.latitude,
            currentCity.longitude,
            location.latitude,
            location.longitude
        )
        if (distance >= LOCATION_CALIBRATION_DISTANCE_METERS) {
            locI("location_calibration_weather_needed_by_distance: distance=${distance}m")
            return true
        }

        val lastFetchTime = repository.getLastFetchTime(currentCity.id)
        val cacheAge = System.currentTimeMillis() - lastFetchTime
        if (cacheAge >= LOCATION_CALIBRATION_FORCE_INTERVAL_MS) {
            locI("location_calibration_weather_needed_by_age: cacheAge=${cacheAge}ms")
            return true
        }

        locI("location_calibration_weather_skip: distance=${distance}m, cacheAge=${cacheAge}ms")
        return false
    }

    private suspend fun fetchWithRetry(
        lon: Double,
        lat: Double,
        options: FetchOptions
    ): Result<WeatherResponse> {
        val fetchStartMs = android.os.SystemClock.elapsedRealtime()
        var lastException: Exception? = null
        val timedResult = withTimeoutOrNull(options.totalTimeoutMillis) {
            repeat(options.maxRetries + 1) { attempt ->
                val remainingBeforeDelay = options.totalTimeoutMillis - elapsedSince(fetchStartMs)
                if (remainingBeforeDelay <= 0L) {
                    locW("weather_fetch_budget_exhausted_before_attempt: attempt=${attempt + 1}, total=${elapsedSince(fetchStartMs)}ms")
                    weatherW("weather_fetch_budget_exhausted_before_attempt: attempt=${attempt + 1}, total=${elapsedSince(fetchStartMs)}ms")
                    return@withTimeoutOrNull Result.failure(lastException ?: Exception("weather_fetch_timeout"))
                }

                if (attempt > 0) {
                    val delayMillis = options.retryDelayMillis * attempt
                    if (remainingBeforeDelay <= delayMillis + 300L) {
                        locW("weather_fetch_skip_retry_no_budget: attempt=${attempt + 1}, remaining=${remainingBeforeDelay}ms")
                        weatherW("weather_fetch_skip_retry_no_budget: attempt=${attempt + 1}, remaining=${remainingBeforeDelay}ms")
                        return@withTimeoutOrNull Result.failure(lastException ?: Exception("weather_fetch_timeout"))
                    }
                    delay(delayMillis)
                }

                val remainingForAttempt = options.totalTimeoutMillis - elapsedSince(fetchStartMs)
                if (remainingForAttempt <= 0L) {
                    locW("weather_fetch_budget_exhausted_after_delay: attempt=${attempt + 1}, total=${elapsedSince(fetchStartMs)}ms")
                    weatherW("weather_fetch_budget_exhausted_after_delay: attempt=${attempt + 1}, total=${elapsedSince(fetchStartMs)}ms")
                    return@withTimeoutOrNull Result.failure(lastException ?: Exception("weather_fetch_timeout"))
                }

                val attemptStartMs = android.os.SystemClock.elapsedRealtime()
                locI("weather_fetch_attempt_start: attempt=${attempt + 1}/${options.maxRetries + 1}, lon=$lon, lat=$lat")
                weatherI("weather_fetch_attempt_start: attempt=${attempt + 1}/${options.maxRetries + 1}, lon=$lon, lat=$lat")
                val attemptTimeoutMillis = minOf(options.attemptTimeoutMillis, remainingForAttempt)
                val result = withTimeoutOrNull(attemptTimeoutMillis) {
                    repository.getWeather(lon, lat, includeYesterday = true)
                } ?: Result.failure(Exception("weather_fetch_timeout"))
                result.fold(
                    onSuccess = { response ->
                        val hourly = response.result?.hourly
                        if (hourly == null || hourly.temperature.isNullOrEmpty()) {
                            lastException = Exception("empty_hourly")
                            locW("weather_fetch_attempt_empty_hourly: attempt=${attempt + 1}, elapsed=${elapsedSince(attemptStartMs)}ms")
                            weatherW("weather_fetch_attempt_empty_hourly: attempt=${attempt + 1}, elapsed=${elapsedSince(attemptStartMs)}ms")
                        } else {
                            locI("weather_fetch_attempt_success: attempt=${attempt + 1}, elapsed=${elapsedSince(attemptStartMs)}ms")
                            weatherI("weather_fetch_attempt_success: attempt=${attempt + 1}, elapsed=${elapsedSince(attemptStartMs)}ms")
                            return@withTimeoutOrNull Result.success(response)
                        }
                    },
                    onFailure = { e ->
                        lastException = e as? Exception ?: Exception(e)
                        locW("weather_fetch_attempt_failed: attempt=${attempt + 1}, elapsed=${elapsedSince(attemptStartMs)}ms, error=${mapError(e)}")
                        weatherW("weather_fetch_attempt_failed: attempt=${attempt + 1}, elapsed=${elapsedSince(attemptStartMs)}ms, error=${mapError(e)}")
                        if (e is HttpException && e.code() == 429) {
                            return@withTimeoutOrNull Result.failure(e)
                        }
                    }
                )
            }
            Result.failure(lastException ?: Exception("未知错误"))
        }
        return timedResult ?: Result.failure(Exception("weather_fetch_timeout"))
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
        ) < 200f
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
