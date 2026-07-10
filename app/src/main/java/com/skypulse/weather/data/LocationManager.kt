package com.skypulse.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocationClient
import com.skypulse.weather.data.provider.impl.AmapLocationProvider
import com.skypulse.weather.data.provider.impl.AmapGeocodingProvider
import com.skypulse.weather.data.provider.impl.SystemLocationProvider
import com.skypulse.weather.data.provider.impl.SystemGeocoderProvider
import com.skypulse.weather.data.provider.model.SimpleLocation
import com.skypulse.weather.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val systemLocationProvider: SystemLocationProvider,
    private val amapLocationProvider: AmapLocationProvider,
    private val systemGeocoderProvider: SystemGeocoderProvider,
    private val amapGeocodingProvider: AmapGeocodingProvider
) {
    data class CachedLocation(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val time: Long = 0L,
        val accuracy: Float = 0f
    )

    private data class PendingLocation(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val time: Long,
        val accuracy: Float
    )

    private data class LocationRequestProfile(
        val highAccuracy: Boolean,
        val timeoutMillis: Long
    )

    private data class TimedNullableResult<T>(
        val value: T?
    )

    companion object {
        private const val TAG = "LocationManager"
        const val DEFAULT_LONGITUDE = 116.4074
        const val DEFAULT_LATITUDE = 39.9042
        private const val PREFS_NAME = "location_cache"
        private const val KEY_CACHED_LAT = "cached_lat"
        private const val KEY_CACHED_LON = "cached_lon"
        private const val KEY_CACHED_NAME = "cached_name"
        private const val KEY_CACHED_TIME = "cached_time"
        private const val KEY_CACHED_ACCURACY = "cached_accuracy"
        private const val KEY_PENDING_LAT = "pending_lat"
        private const val KEY_PENDING_LON = "pending_lon"
        private const val KEY_PENDING_NAME = "pending_name"
        private const val KEY_PENDING_TIME = "pending_time"
        private const val KEY_PENDING_ACCURACY = "pending_accuracy"

        private const val HIGH_ACCURACY_TIMEOUT_MS = 9000L
        private const val REGULAR_LOCATION_TOTAL_TIMEOUT_MS = 18_000L
        private const val HIGH_ACCURACY_LOCATION_TOTAL_TIMEOUT_MS = 13_500L
        private const val ACCEPTABLE_ACCURACY_METERS = 120f

        private var privacyAgreed = false

        fun ensurePrivacyAgreed(context: Context) {
            if (!privacyAgreed) {
                try {
                    AMapLocationClient.updatePrivacyShow(context, true, true)
                    AMapLocationClient.updatePrivacyAgree(context, true)
                    privacyAgreed = true
                } catch (_: Exception) {}
            }
        }
    }

    private val cachePrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasBackgroundLocationPermission(): Boolean {
        val hasForeground = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasForeground) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    fun hasLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun getCachedLocation(): CachedLocation? {
        val lat = cachePrefs.getFloat(KEY_CACHED_LAT, 0f).toDouble()
        val lon = cachePrefs.getFloat(KEY_CACHED_LON, 0f).toDouble()
        val name = cachePrefs.getString(KEY_CACHED_NAME, null)?.takeIf { it.isNotBlank() }
        val time = cachePrefs.getLong(KEY_CACHED_TIME, 0L)
        val accuracy = cachePrefs.getFloat(KEY_CACHED_ACCURACY, 0f)
        if (lat == 0.0 || lon == 0.0 || name == null) return null
        return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
    }

    fun saveCachedLocation(
        name: String,
        longitude: Double,
        latitude: Double,
        time: Long = System.currentTimeMillis(),
        accuracy: Float = 0f
    ) {
        val normalizedName = name.takeIf { it.isNotBlank() } ?: return
        if (latitude == 0.0 || longitude == 0.0) return
        cachePrefs.edit()
            .putFloat(KEY_CACHED_LAT, latitude.toFloat())
            .putFloat(KEY_CACHED_LON, longitude.toFloat())
            .putString(KEY_CACHED_NAME, normalizedName)
            .putLong(KEY_CACHED_TIME, time)
            .putFloat(KEY_CACHED_ACCURACY, accuracy)
            .apply()
    }

    private fun getPendingLocation(): PendingLocation? {
        val lat = cachePrefs.getFloat(KEY_PENDING_LAT, 0f).toDouble()
        val lon = cachePrefs.getFloat(KEY_PENDING_LON, 0f).toDouble()
        val name = cachePrefs.getString(KEY_PENDING_NAME, null)?.takeIf { it.isNotBlank() }
        val time = cachePrefs.getLong(KEY_PENDING_TIME, 0L)
        val accuracy = cachePrefs.getFloat(KEY_PENDING_ACCURACY, 0f)
        if (lat == 0.0 || lon == 0.0 || name == null || time <= 0L) return null
        return PendingLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
    }

    private fun savePendingLocation(
        name: String,
        longitude: Double,
        latitude: Double,
        time: Long,
        accuracy: Float
    ) {
        val normalizedName = name.takeIf { it.isNotBlank() } ?: return
        if (latitude == 0.0 || longitude == 0.0) return
        cachePrefs.edit()
            .putFloat(KEY_PENDING_LAT, latitude.toFloat())
            .putFloat(KEY_PENDING_LON, longitude.toFloat())
            .putString(KEY_PENDING_NAME, normalizedName)
            .putLong(KEY_PENDING_TIME, time)
            .putFloat(KEY_PENDING_ACCURACY, accuracy)
            .apply()
    }

    private fun clearPendingLocation() {
        cachePrefs.edit()
            .remove(KEY_PENDING_LAT)
            .remove(KEY_PENDING_LON)
            .remove(KEY_PENDING_NAME)
            .remove(KEY_PENDING_TIME)
            .remove(KEY_PENDING_ACCURACY)
            .apply()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun locI(message: String) = FileLogger.locI(TAG, message)
    private fun locW(message: String) = FileLogger.locW(TAG, message)
    private fun locE(message: String, throwable: Throwable? = null) {
        if (throwable == null) FileLogger.locE(TAG, message) else FileLogger.locE(TAG, message, throwable)
    }

    private fun elapsedSince(startMs: Long): Long = android.os.SystemClock.elapsedRealtime() - startMs

    private fun Double.fullCoord(): String = String.format(Locale.US, "%.6f", this)

    private fun SimpleLocation.locSummary(): String {
        return "lat=${latitude.fullCoord()}, lon=${longitude.fullCoord()}, accuracy=${accuracy}m, " +
            "city=${cityName.safeLogValue()}, district=${districtName.safeLogValue()}, " +
            "aoi=${aoiName.safeLogValue()}, street=${street.safeLogValue()}, " +
            "streetNum=${streetNum.safeLogValue()}"
    }

    // ============ Unified Entrance for Positioning (Amap -> System) ============

    private fun applyAntiJitter(
        lat: Double,
        lon: Double,
        name: String,
        accuracy: Float = 0f,
        time: Long = System.currentTimeMillis(),
        highAccuracy: Boolean = false
    ): CachedLocation {
        val cached = getCachedLocation()
        if (cached != null && cached.name.isNotBlank() && cached.name != "未知位置") {
            val now = System.currentTimeMillis()
            val dist = distanceBetween(lat, lon, cached.latitude, cached.longitude)
            
            // 1. 时间衰减判定：若缓存记录早于 5 分钟前，强制放行更新，打破“静止后不更新”的死锁
            val timeDiff = now - cached.time
            val isCacheExpired = timeDiff > 5 * 60 * 1000L // 5分钟

            if (LocationJumpGuard.shouldHoldForConfirmation(dist, accuracy, timeDiff, highAccuracy)) {
                val pending = getPendingLocation()
                if (pending != null) {
                    val pendingAge = now - pending.time
                    val distanceToPending = distanceBetween(lat, lon, pending.latitude, pending.longitude)
                    if (LocationJumpGuard.isConfirmedByPending(distanceToPending, pendingAge)) {
                        Log.i(TAG, "低可信大距离定位已二次确认：dist=${dist}m, pendingDist=${distanceToPending}m, accuracy=${accuracy}m, name=$name")
                        FileLogger.i(TAG, "低可信大距离定位已二次确认：dist=${dist}m, pendingDist=${distanceToPending}m, accuracy=${accuracy}m, name=$name")
                        clearPendingLocation()
                        return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
                    }
                    if (LocationJumpGuard.isPendingExpired(pendingAge)) {
                        clearPendingLocation()
                    }
                }

                savePendingLocation(name, lon, lat, now, accuracy)
                Log.i(TAG, "检测到低可信大距离定位跳变，等待二次确认：dist=${dist}m, accuracy=${accuracy}m, cached=${cached.name}, candidate=$name")
                FileLogger.i(TAG, "检测到低可信大距离定位跳变，等待二次确认：dist=${dist}m, accuracy=${accuracy}m, cached=${cached.name}, candidate=$name")
                return cached
            }
            
            // 2. 基站跳变过滤（针对 1.5km 级跳变场景）：
            val isOutlierJump = !isCacheExpired && 
                                accuracy > 300f && 
                                cached.accuracy > 0f && 
                                cached.accuracy < accuracy && 
                                dist < accuracy
            
            if (isOutlierJump) {
                Log.i(TAG, "检测到疑似基站漂移跳变：新精度 ${accuracy}m，距离缓存 ${dist}m，缓存精度 ${cached.accuracy}m，复用缓存")
                FileLogger.i(TAG, "检测到疑似基站漂移跳变：新精度 ${accuracy}m，距离缓存 ${dist}m，缓存精度 ${cached.accuracy}m，复用缓存")
                return cached
            }

            val isReliableFineUpdate = highAccuracy &&
                accuracy > 0f &&
                accuracy <= ACCEPTABLE_ACCURACY_METERS &&
                (dist >= 35f || (name != cached.name && dist >= 20f))
            if (isReliableFineUpdate) {
                Log.i(TAG, "前台高精度定位放行：dist=${dist}m, accuracy=${accuracy}m, name=$name")
                clearPendingLocation()
                return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
            }

            // 3. 常规微小防抖动
            if (!isCacheExpired && dist < 200f && (accuracy >= cached.accuracy || accuracy > 100f)) {
                Log.i(TAG, "防跳变机制触发：新位置距离上次缓存仅 ${dist}米（< 200m），复用旧坐标与地名: (${cached.latitude}, ${cached.longitude}) - ${cached.name}")
                return cached
            }
        }
        clearPendingLocation()
        return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
    }

    suspend fun requestBestLocation(highAccuracy: Boolean = false): CachedLocation? {
        val startMs = android.os.SystemClock.elapsedRealtime()
        val totalTimeoutMillis = if (highAccuracy) {
            HIGH_ACCURACY_LOCATION_TOTAL_TIMEOUT_MS
        } else {
            REGULAR_LOCATION_TOTAL_TIMEOUT_MS
        }
        locI("location_flow_start: primary=amap, fallback=system, highAccuracy=$highAccuracy, totalTimeout=${totalTimeoutMillis}ms")
        val result = withTimeoutOrNull(totalTimeoutMillis) {
            TimedNullableResult(requestBestLocationInternal(highAccuracy, totalTimeoutMillis))
        }
        if (result == null) {
            Log.w(TAG, "定位总流程硬超时: ${totalTimeoutMillis}ms, highAccuracy=$highAccuracy")
            FileLogger.w(TAG, "定位总流程硬超时: ${totalTimeoutMillis}ms, highAccuracy=$highAccuracy")
            locW("location_flow_timeout: elapsed=${elapsedSince(startMs)}ms, highAccuracy=$highAccuracy")
        } else {
            locI("location_flow_complete: elapsed=${elapsedSince(startMs)}ms, highAccuracy=$highAccuracy, result=${result.value?.let { "lat=${it.latitude.fullCoord()}, lon=${it.longitude.fullCoord()}, accuracy=${it.accuracy}m, name=${it.name.safeLogValue()}" } ?: "null"}")
        }
        return result?.value
    }

    suspend fun requestSystemOrIpLocation(highAccuracy: Boolean = false): CachedLocation? {
        return requestBestLocation(highAccuracy)
    }

    private suspend fun requestBestLocationInternal(
        highAccuracy: Boolean = false,
        totalTimeoutMillis: Long
    ): CachedLocation? {
        val profile = LocationRequestProfile(
            highAccuracy = highAccuracy,
            timeoutMillis = if (highAccuracy) HIGH_ACCURACY_TIMEOUT_MS else 8000L
        )
        val startMs = android.os.SystemClock.elapsedRealtime()
        locI("location_flow_internal_start: primary=amap, fallback=system, highAccuracy=$highAccuracy, amapTimeout=${profile.timeoutMillis}ms")
        Log.i(TAG, "定位总入口被调用（高德优先+系统兜底，全面剔除IP定位）, highAccuracy=$highAccuracy...")

        if (hasLocationPermission()) {
            // 1. 国内设备主路径：高德 SDK 同时返回坐标和地址语义，优先用于当前定位城市。
            val amapStartMs = android.os.SystemClock.elapsedRealtime()
            locI("amap_primary_start: timeout=${profile.timeoutMillis}ms, highAccuracy=$highAccuracy")
            val amapLoc = amapLocationProvider.requestLocation(
                highAccuracy = profile.highAccuracy,
                timeoutMillis = profile.timeoutMillis
            )
            if (amapLoc != null) {
                val directName = resolveLocationName(amapLoc)
                val name = if (directName != "未知位置" && directName.isNotBlank()) {
                    directName
                } else {
                    reverseGeocode(
                        amapLoc.latitude,
                        amapLoc.longitude,
                        forceRefresh = highAccuracy,
                        accuracy = amapLoc.accuracy
                    )
                }
                Log.i(TAG, "高德主定位成功: lat=${amapLoc.latitude}, lon=${amapLoc.longitude}, name=$name")
                locI("amap_primary_used: elapsed=${elapsedSince(amapStartMs)}ms, total=${elapsedSince(startMs)}ms, directName=${directName.safeLogValue()}, name=${name.safeLogValue()}, ${amapLoc.locSummary()}")
                return applyAntiJitter(
                    amapLoc.latitude,
                    amapLoc.longitude,
                    name,
                    amapLoc.accuracy,
                    amapLoc.time,
                    highAccuracy
                )
            }

            Log.w(TAG, "高德主定位失败或超时，降级尝试系统定位服务...")
            locW("amap_primary_failed_start_system_fallback: elapsed=${elapsedSince(startMs)}ms")

            // 2. 系统定位提供者作为兜底（GMS Fused / 原生 LocationManager），常规场景保留有限重试。
            var sysLoc: SimpleLocation? = null
            val maxAttempts = if (highAccuracy) 1 else 2
            for (attempt in 1..maxAttempts) {
                val remainingMillis = totalTimeoutMillis - elapsedSince(startMs)
                if (remainingMillis < 2_500L) {
                    locW("system_fallback_skip_no_time: attempt=$attempt/$maxAttempts, remaining=${remainingMillis}ms")
                    break
                }
                val attemptTimeoutMillis = minOf(profile.timeoutMillis, remainingMillis - 500L)
                val attemptStartMs = android.os.SystemClock.elapsedRealtime()
                locI("system_fallback_attempt_start: attempt=$attempt/$maxAttempts, timeout=${attemptTimeoutMillis}ms")
                sysLoc = systemLocationProvider.requestLocation(profile.highAccuracy, attemptTimeoutMillis)
                if (sysLoc != null) {
                    locI("system_fallback_attempt_success: attempt=$attempt/$maxAttempts, elapsed=${elapsedSince(attemptStartMs)}ms, ${sysLoc.locSummary()}")
                    break
                }
                locW("system_fallback_attempt_failed: attempt=$attempt/$maxAttempts, elapsed=${elapsedSince(attemptStartMs)}ms")
                if (attempt < maxAttempts) {
                    Log.w(TAG, "系统兜底定位第 ${attempt} 次失败，等待 1 秒后重试...")
                    kotlinx.coroutines.delay(1000L)
                }
            }

            if (sysLoc != null) {
                val geocodeStartMs = android.os.SystemClock.elapsedRealtime()
                val name = reverseGeocode(
                    sysLoc.latitude,
                    sysLoc.longitude,
                    forceRefresh = highAccuracy,
                    accuracy = sysLoc.accuracy
                )
                locI("system_reverse_geocode_complete: elapsed=${elapsedSince(geocodeStartMs)}ms, name=${name.safeLogValue()}, location=${sysLoc.locSummary()}")
                Log.i(TAG, "系统自带定位成功: lat=${sysLoc.latitude}, lon=${sysLoc.longitude}, name=$name")
                locI("system_fallback_used: elapsed=${elapsedSince(startMs)}ms, name=${name.safeLogValue()}, ${sysLoc.locSummary()}")
                return applyAntiJitter(sysLoc.latitude, sysLoc.longitude, name, sysLoc.accuracy, sysLoc.time, highAccuracy)
            }
            Log.w(TAG, "高德主定位与系统兜底定位均已失败，直接返回null")
            locW("location_flow_internal_failed: elapsed=${elapsedSince(startMs)}ms")
        } else {
            Log.w(TAG, "无定位权限，拒绝自动定位")
            locW("location_flow_internal_no_permission: elapsed=${elapsedSince(startMs)}ms")
        }
        return null
    }

    // ============ Location Name Resolution (Amap Web + System Geocoder Fallback) ============

    suspend fun reverseGeocode(
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false,
        accuracy: Float = 0f
    ): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val startMs = android.os.SystemClock.elapsedRealtime()
        locI("reverse_geocode_start: lat=${lat.fullCoord()}, lon=${lon.fullCoord()}, forceRefresh=$forceRefresh, accuracy=${accuracy}m")
        val cached = getCachedLocation()
        if (!forceRefresh && cached != null && cached.name.isNotBlank() && cached.name != "未知位置") {
            val dist = distanceBetween(lat, lon, cached.latitude, cached.longitude)
            val timeDiff = System.currentTimeMillis() - cached.time

            val cacheLimit = when {
                dist < 35f -> 3 * 60 * 1000L
                accuracy > 200f && dist < 120f -> 5 * 60 * 1000L
                else -> 60 * 1000L
            }
            val isCacheExpired = timeDiff > cacheLimit

            if (!isCacheExpired && dist < 120f) {
                Log.i(TAG, "reverseGeocode: 距离上次缓存位置仅为 ${dist}米且未过期，复用缓存位置名称: ${cached.name}")
                locI("reverse_geocode_cache_hit: elapsed=${elapsedSince(startMs)}ms, dist=${dist}m, age=${timeDiff}ms, result=${cached.name.safeLogValue()}")
                return@withContext cached.name
            }
        }

        // 1. 优先使用官方高德 REST Web API 逆地理编码，保持与高德定位主路径一致。
        val amapStartMs = android.os.SystemClock.elapsedRealtime()
        val amapResult = amapGeocodingProvider.reverseGeocode(lat, lon)
        locI("reverse_geocode_amap_web_done: elapsed=${elapsedSince(amapStartMs)}ms, result=${amapResult.safeLogValue()}")

        if (amapResult != null && amapResult != "未知位置" && amapResult.isNotBlank()) {
            locI("reverse_geocode_selected: source=amap_web, total=${elapsedSince(startMs)}ms, result=${amapResult.safeLogValue()}")
            return@withContext amapResult
        }

        // 2. 高德 Web 失败后，降级系统 Geocoder，避免 API key/网络异常导致地址完全不可用。
        Log.i(TAG, "reverseGeocode: 高德 Web 失败，降级尝试系统 Geocoder...")
        val systemStartMs = android.os.SystemClock.elapsedRealtime()
        val systemResult = systemGeocoderProvider.reverseGeocode(lat, lon)
        locI("reverse_geocode_system_done: elapsed=${elapsedSince(systemStartMs)}ms, result=${systemResult.safeLogValue()}")

        if (systemResult != null && systemResult != "未知位置" && systemResult.isNotBlank()) {
            locI("reverse_geocode_selected: source=system_geocoder, total=${elapsedSince(startMs)}ms, result=${systemResult.safeLogValue()}")
            return@withContext systemResult
        }

        locW("reverse_geocode_all_failed: total=${elapsedSince(startMs)}ms")
        "未知位置"
    }

    fun resolveLocationName(location: SimpleLocation): String {
        val city = location.cityName?.takeIf { it.isNotBlank() }
        val district = location.districtName?.takeIf { it.isNotBlank() && it != city }
        val aoi = location.aoiName?.takeIf { it.isNotBlank() }
        val street = location.street?.takeIf { it.isNotBlank() }
        val streetNum = location.streetNum?.takeIf { it.isNotBlank() }
        val address = location.address?.takeIf { it.isNotBlank() }

        val result = buildString {
            when {
                district != null -> append(district)
                city != null -> append(city)
            }
            when {
                aoi != null -> append(" $aoi")
                street != null -> {
                    if (isNotEmpty()) append(" ")
                    append(street)
                    streetNum?.let { append(it) }
                }
            }
            if (isEmpty()) {
                address?.let { append(it) }
            }
        }

        if (result.isBlank()) {
            if (city != null) return city
            return "未知位置"
        }

        return result
    }

    private fun String?.safeLogValue(maxLength: Int = 80): String {
        val value = this?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() } ?: "null"
        return if (value.length <= maxLength) value else value.take(maxLength) + "..."
    }
}
