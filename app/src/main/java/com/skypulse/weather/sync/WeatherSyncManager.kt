package com.skypulse.weather.sync

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.data.local.database.WeatherDao
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

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
        private const val PREFS_NAME = "sync_manager_prefs"
        private const val KEY_LAST_LOCATION_TIME = "last_location_success_time"
        private const val FUSED_LOCATION_TIMEOUT_MS = 15_000L
    }

    private val lastFetchTimesByCityId = ConcurrentHashMap<String, Long>()

    private val prefs by lazy {
        locationManager.context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(locationManager.context)
    }

    /**
     * 判断是否是真正的冷启动（首次安装或长时间未使用）。
     * 使用 SharedPreferences 持久化记录，避免进程被杀后误判。
     */
    private fun isTrueColdStart(): Boolean {
        val lastLocationTime = prefs.getLong(KEY_LAST_LOCATION_TIME, 0L)
        if (lastLocationTime == 0L) return true
        // 超过24小时未定位，认为是冷启动
        val elapsed = System.currentTimeMillis() - lastLocationTime
        return elapsed > 24 * 60 * 60 * 1000L
    }

    private fun markLocationSuccess() {
        prefs.edit().putLong(KEY_LAST_LOCATION_TIME, System.currentTimeMillis()).apply()
    }

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
        if (isRecentlyFetched(cityId)) {
            Log.i(TAG, "refreshWeather: $cityId 限流，跳过")
            return@withContext SyncResult.RateLimited
        }
        doRefreshWeather(cityId, longitude, latitude)
    }

    /**
     * 不限流的天气刷新。用于 GPS 定位成功后的强制刷新。
     * GPS 定位结果是最新数据，不应被限流跳过。
     */
    private suspend fun doRefreshWeather(
        cityId: String,
        longitude: Double,
        latitude: Double
    ): SyncResult {
        FileLogger.i(TAG, "doRefreshWeather: cityId=$cityId, lon=$longitude, lat=$latitude")
        Log.i(TAG, "refreshWeather: $cityId 开始网络请求 lon=$longitude lat=$latitude")
        val result = fetchWithRetry(longitude, latitude)
        FileLogger.i(TAG, "doRefreshWeather: 网络请求完成, success=${result.isSuccess}")
        Log.i(TAG, "refreshWeather: 网络请求完成, success=${result.isSuccess}")
        return result.fold(
            onSuccess = { response ->
                markFetched(cityId)
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
     * GPS 解析 → 更新当前城市坐标/名称 → 获取天气 → 写入 Room。
     * 用于主应用的定位城市刷新（前台，AMap GPS 可靠）。
     */
    suspend fun refreshWeatherWithLocation(): SyncResult = withContext(Dispatchers.IO) {
        val hasLocationPermission = hasLocationPermission()
        Log.i(TAG, "refreshWeatherWithLocation: hasPermission=$hasLocationPermission")

        if (!hasLocationPermission) {
            // 无定位权限，使用默认坐标
            Log.i(TAG, "无定位权限，使用默认坐标")
            return@withContext doRefreshWeather(
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
            Log.i(TAG, "GPS 成功: lon=$lon, lat=$lat, name=$locationName")
            if (locationName == "未知位置") {
                // 地址解析失败（AMAP 反向地理编码未完成），只更新坐标，保留旧城市名
                Log.w(TAG, "GPS 成功但地址为空，保留旧城市名, lon=$lon, lat=$lat")
                val oldCachedName = locationManager.getCachedLocation()?.name
                locationManager.saveCachedLocation(oldCachedName ?: locationName, lon, lat)
                updateCurrentLocationCityCoordsOnly(lon, lat)
            } else {
                locationManager.saveCachedLocation(locationName, lon, lat)
                updateCurrentLocationCity(locationName, lon, lat)
            }

            val currentCity = getCurrentLocationCity()
            Log.i(TAG, "getCurrentLocationCity: ${currentCity?.id}, name=${currentCity?.name}")
            if (currentCity != null) {
                Log.i(TAG, "开始获取天气: cityId=${currentCity.id}")
                return@withContext doRefreshWeather(currentCity.id, lon, lat)
            }
        }

        // GPS 失败，尝试缓存位置
        val cachedLocation = locationManager.getCachedLocation()
        Log.i(TAG, "GPS 失败, cachedLocation=${cachedLocation?.name}")
        if (cachedLocation != null) {
            val currentCity = getCurrentLocationCity()
            Log.i(TAG, "使用缓存: cityId=${currentCity?.id}")
            if (currentCity != null) {
                return@withContext doRefreshWeather(
                    currentCity.id,
                    cachedLocation.longitude,
                    cachedLocation.latitude
                )
            }
        }

        // 全部失败
        Log.w(TAG, "refreshWeatherWithLocation: 全部失败，返回 LocationFailed")
        SyncResult.LocationFailed
    }

    /**
     * 小组件专用的定位 + 天气刷新流程。
     * 与 refreshWeatherWithLocation() 的区别：
     * 1. AMap 失败后会尝试 FusedLocation（GMS）和原生 LocationManager 作为后备
     * 2. 不更新 Room 中的城市记录（避免污染主页的城市名显示）
     * 3. 仅更新天气缓存数据
     */
    suspend fun refreshWeatherWithLocationForWidget(): SyncResult = withContext(Dispatchers.IO) {
        val hasLocationPermission = hasLocationPermission()
        val hasBackgroundPermission = hasBackgroundLocationPermission()
        FileLogger.i(TAG, "refreshWeatherWithLocationForWidget: hasPermission=$hasLocationPermission, " +
            "hasBackgroundPermission=$hasBackgroundPermission")

        if (!hasLocationPermission) {
            FileLogger.w(TAG, "小组件: 无定位权限，使用默认坐标 (北京)")
            return@withContext doRefreshWeather(
                cityId = "current_location",
                longitude = LocationManager.DEFAULT_LONGITUDE,
                latitude = LocationManager.DEFAULT_LATITUDE
            )
        }

        // 方案1: AMap GPS
        FileLogger.i(TAG, "小组件定位: 尝试 AMap GPS...")
        val amapLocation = requestLocationWithRetry()
        if (amapLocation != null) {
            val lon = amapLocation.longitude
            val lat = amapLocation.latitude
            FileLogger.i(TAG, "小组件定位: AMap GPS 成功 lon=$lon, lat=$lat")
            // 只更新定位缓存，不更新 Room 城市记录
            locationManager.saveCachedLocation(
                locationManager.resolveLocationName(amapLocation), lon, lat
            )
            return@withContext doRefreshWeather("current_location", lon, lat)
        }

        // 方案2: FusedLocation (GMS) + 原生 LocationManager
        FileLogger.w(TAG, "小组件定位: AMap GPS 失败, 尝试后备方案...")
        val fallbackLocation = requestFallbackLocation()
        if (fallbackLocation != null) {
            val lon = fallbackLocation.longitude
            val lat = fallbackLocation.latitude
            FileLogger.i(TAG, "小组件定位: 后备成功 lon=$lon, lat=$lat")
            // 只更新定位缓存，不更新 Room 城市记录
            try {
                val name = locationManager.reverseGeocode(lat, lon)
                if (name != "未知位置") {
                    locationManager.saveCachedLocation(name, lon, lat)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                FileLogger.w(TAG, "小组件定位: 反向地理编码失败 - ${e.message}")
            }
            return@withContext doRefreshWeather("current_location", lon, lat)
        }

        // 方案3: 缓存坐标
        val cachedLocation = locationManager.getCachedLocation()
        FileLogger.w(TAG, "小组件定位: 后备也失败, cachedLocation=${cachedLocation?.name}, " +
            "lon=${cachedLocation?.longitude}, lat=${cachedLocation?.latitude}")
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
        if (currentCity != null) {
            // 有定位城市（含缓存坐标），用其坐标获取天气
            return@withContext doRefreshWeather(currentCity.id, currentCity.longitude, currentCity.latitude)
        }
        // 无定位城市，用手动添加的第一个城市的坐标
        val allCities = cityRepository.getCities()
        val firstCity = allCities.firstOrNull()
        if (firstCity != null) {
            return@withContext doRefreshWeather(firstCity.id, firstCity.longitude, firstCity.latitude)
        }
        // 都没有，兜底北京
        doRefreshWeather(
            cityId = "current_location",
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
        // 真正冷启动时 AMap SDK 内部初始化需要时间，首次尝试前等待 3 秒
        // 使用 SharedPreferences 持久化判断，避免进程被杀后误判为冷启动
        val isColdStart = isTrueColdStart()
        if (isColdStart) {
            FileLogger.d(TAG, "冷启动定位，等待 AMap SDK 初始化 3 秒")
            Log.d(TAG, "冷启动定位，等待 AMap SDK 初始化 3 秒")
            kotlinx.coroutines.delay(3_000L)
        }

        val maxAttempts = if (isColdStart) 4 else 2
        FileLogger.i(TAG, "AMap GPS 定位: maxAttempts=$maxAttempts, isColdStart=$isColdStart")
        for (attempt in 0 until maxAttempts) {
            FileLogger.d(TAG, "AMap GPS 尝试 ${attempt + 1}/$maxAttempts")
            val location = locationManager.requestAmapLocation()
            if (location != null) {
                markLocationSuccess()  // 记录定位成功时间
                FileLogger.i(TAG, "AMap GPS 成功: lon=${location.longitude}, lat=${location.latitude}")
                return location
            }
            FileLogger.w(TAG, "AMap GPS 第${attempt + 1}次失败")
            if (attempt < maxAttempts - 1) {
                val delayMs = if (isColdStart) 2_000L else 1_000L
                Log.w(TAG, "定位第${attempt + 1}次失败，${delayMs / 1000}秒后重试")
                kotlinx.coroutines.delay(delayMs)
            }
        }
        FileLogger.w(TAG, "AMap GPS 全部 $maxAttempts 次尝试失败")
        return null
    }

    /**
     * 后备定位方案：先尝试 FusedLocation（需要 GMS），再尝试 Android 原生 LocationManager。
     * 国产手机可能没有 GMS，所以用原生 LocationManager 兜底。
     */
    private suspend fun requestFallbackLocation(): Location? {
        if (!hasLocationPermission()) {
            FileLogger.w(TAG, "后备定位: 无定位权限，跳过")
            return null
        }

        // 方案1: FusedLocation（需要 Google Play Services）
        val hasGms = try {
            com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(locationManager.context) ==
                com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (_: Exception) {
            false
        }

        if (hasGms) {
            FileLogger.i(TAG, "后备定位: 尝试 FusedLocation (GMS 可用)")
            val fused = requestFusedLocation()
            if (fused != null) return fused
            FileLogger.w(TAG, "后备定位: FusedLocation 失败, 尝试原生 LocationManager")
        } else {
            FileLogger.i(TAG, "后备定位: GMS 不可用, 直接尝试原生 LocationManager")
        }

        // 方案2: Android 原生 LocationManager（所有手机都支持）
        return requestNativeLocation()
    }

    /**
     * Google FusedLocationProviderClient 定位。
 * 需要 Google Play Services，在国产手机上可能不可用。
     */
    private suspend fun requestFusedLocation(): Location? {
        return try {
            FileLogger.i(TAG, "FusedLocation: 开始定位, timeout=${FUSED_LOCATION_TIMEOUT_MS}ms")
            val location = suspendCancellableCoroutine<Location?> { cont ->
                try {
                    val hasFine = ContextCompat.checkSelfPermission(
                        locationManager.context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val priority = if (hasFine) {
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    } else {
                        Priority.PRIORITY_LOW_POWER
                    }
                    val request = LocationRequest.Builder(priority, FUSED_LOCATION_TIMEOUT_MS)
                        .setMaxUpdates(1)
                        .build()

                    val callback = object : com.google.android.gms.location.LocationCallback() {
                        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                            val loc = result.lastLocation
                            if (loc != null) {
                                FileLogger.i(TAG, "FusedLocation: 定位成功 lon=${loc.longitude}, lat=${loc.latitude}")
                            } else {
                                FileLogger.w(TAG, "FusedLocation: 结果为 null")
                            }
                            if (cont.isActive) cont.resume(loc)
                            try {
                                fusedLocationClient.removeLocationUpdates(this)
                            } catch (_: Exception) {}
                        }
                    }

                    cont.invokeOnCancellation {
                        try {
                            fusedLocationClient.removeLocationUpdates(callback)
                        } catch (_: Exception) {}
                    }

                    fusedLocationClient.requestLocationUpdates(
                        request,
                        callback,
                        Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    FileLogger.e(TAG, "FusedLocation: 请求异常 - ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
            location
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "FusedLocation: 异常 - ${e.message}")
            null
        }
    }

    /**
     * Android 原生 LocationManager 定位。
     * 所有 Android 手机都支持，不依赖 Google Play Services。
     * 使用 NETWORK_PROVIDER（Wi-Fi/基站），不需要 GPS 硬件信号。
     */
    private suspend fun requestNativeLocation(): Location? {
        return try {
            val nativeLocManager = locationManager.context.getSystemService(
                android.content.Context.LOCATION_SERVICE
            ) as? android.location.LocationManager
            if (nativeLocManager == null) {
                FileLogger.w(TAG, "NativeLocation: 无法获取系统 LocationManager")
                return null
            }

            // 检查是否有可用的 provider
            val providers = nativeLocManager.getProviders(true)
            FileLogger.i(TAG, "NativeLocation: 可用 providers=$providers")

            // 优先用 network provider（不需要 GPS 信号，后台更可靠）
            val provider = when {
                providers.contains(android.location.LocationManager.NETWORK_PROVIDER) ->
                    android.location.LocationManager.NETWORK_PROVIDER
                providers.contains(android.location.LocationManager.PASSIVE_PROVIDER) ->
                    android.location.LocationManager.PASSIVE_PROVIDER
                providers.contains(android.location.LocationManager.GPS_PROVIDER) ->
                    android.location.LocationManager.GPS_PROVIDER
                else -> {
                    FileLogger.w(TAG, "NativeLocation: 无可用 provider")
                    return null
                }
            }
            FileLogger.i(TAG, "NativeLocation: 使用 provider=$provider")

            // 先尝试 getLastKnownLocation（立即返回）
            val lastKnown = try {
                nativeLocManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
            if (lastKnown != null) {
                val age = System.currentTimeMillis() - lastKnown.time
                FileLogger.i(TAG, "NativeLocation: lastKnown age=${age}ms, " +
                    "lon=${lastKnown.longitude}, lat=${lastKnown.latitude}")
                // 如果 lastKnown 不超过 15 分钟，直接使用
                if (age < 15 * 60 * 1000) {
                    return lastKnown
                }
                FileLogger.i(TAG, "NativeLocation: lastKnown 过期 (${age}ms), 请求新位置")
            }

            // 请求一次新的位置更新（带超时保护）
            FileLogger.i(TAG, "NativeLocation: 请求 $provider 定位, timeout=${FUSED_LOCATION_TIMEOUT_MS}ms")
            val location = kotlinx.coroutines.withTimeoutOrNull(FUSED_LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(loc: Location) {
                                FileLogger.i(TAG, "NativeLocation: 定位成功 lon=${loc.longitude}, lat=${loc.latitude}")
                                if (cont.isActive) cont.resume(loc)
                                try {
                                    nativeLocManager.removeUpdates(this)
                                } catch (_: Exception) {}
                            }
                            @Deprecated("Deprecated in API")
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }

                        cont.invokeOnCancellation {
                            try {
                                nativeLocManager.removeUpdates(listener)
                            } catch (_: Exception) {}
                        }

                        nativeLocManager.requestLocationUpdates(
                            provider,
                            0L,  // minTimeMs=0 尽快获取
                            0f,  // minDistanceM
                            listener,
                            Looper.getMainLooper()
                        )
                    } catch (e: SecurityException) {
                        FileLogger.e(TAG, "NativeLocation: SecurityException - ${e.message}")
                        if (cont.isActive) cont.resume(null)
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "NativeLocation: 异常 - ${e.message}")
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }

            if (location == null) {
                FileLogger.w(TAG, "NativeLocation: 定位超时或失败")
            }
            location
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "NativeLocation: 异常 - ${e.message}")
            null
        }
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        val ctx = locationManager.context
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
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

    /** 只更新坐标，不改名。用于地址解析失败时保留旧城市名。 */
    private suspend fun updateCurrentLocationCityCoordsOnly(lon: Double, lat: Double) {
        val currentCity = cityRepository.getCurrentLocationCity()
        if (currentCity != null) {
            cityRepository.updateCity(currentCity.copy(longitude = lon, latitude = lat))
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
