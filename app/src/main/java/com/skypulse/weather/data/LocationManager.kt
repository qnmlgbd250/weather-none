package com.skypulse.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.skypulse.weather.util.FileLogger
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val geocodingService: GeocodingService
) {
    data class CachedLocation(
        val latitude: Double,
        val longitude: Double,
        val name: String,
        val time: Long = 0L,
        val accuracy: Float = 0f
    )

    private data class LocationRequestProfile(
        val highAccuracy: Boolean,
        val timeoutMillis: Long
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

        private const val HIGH_ACCURACY_TIMEOUT_MS = 9000L
        private const val GOOD_ACCURACY_METERS = 60f
        private const val ACCEPTABLE_ACCURACY_METERS = 120f
        private const val COARSE_ACCURACY_METERS = 200f
        private const val RECENT_ACTIVE_FIX_MS = 60 * 1000L
        private const val RECENT_LAST_KNOWN_MS = 90 * 1000L

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

    private val fusedLocationClient by lazy {
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
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

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    // ============ AMAP GPS Positioning ============

    suspend fun requestAmapLocation(): AMapLocation? {
        return requestAmapLocation(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy, 8000L)
    }

    private suspend fun requestAmapLocation(
        mode: AMapLocationClientOption.AMapLocationMode,
        timeoutMillis: Long
    ): AMapLocation? {
        ensurePrivacyAgreed(context)
        return try {
            val client = AMapLocationClient(context)
            val option = AMapLocationClientOption().apply {
                isOnceLocation = true
                isNeedAddress = true
                locationMode = mode
                httpTimeOut = timeoutMillis
            }
            client.setLocationOption(option)

            suspendCancellableCoroutine { cont ->
                client.setLocationListener { location ->
                    if (cont.isActive) {
                        if (location != null && location.errorCode == 0) {
                            Log.i(TAG, "AMap 定位成功: ${location.latitude}, ${location.longitude}")
                            FileLogger.i(TAG, "AMap 定位成功: lat=${location.latitude}, lon=${location.longitude}, " +
                                "city=${location.city}, district=${location.district}, " +
                                "aoi=${location.aoiName}, street=${location.street}")
                            cont.resume(location)
                        } else {
                            Log.w(TAG, "AMap 定位失败: errorCode=${location?.errorCode}, errorDetail=${location?.locationDetail}")
                            FileLogger.w(TAG, "AMap 定位失败: errorCode=${location?.errorCode}, " +
                                "errorDetail=${location?.locationDetail}")
                            cont.resume(null)
                        }
                    }
                    client.stopLocation()
                    client.onDestroy()
                }
                client.startLocation()
                cont.invokeOnCancellation {
                    client.stopLocation()
                    client.onDestroy()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AMap location failed", e)
            null
        }
    }

    // ============ Location Name Resolution ============

    fun resolveLocationName(location: AMapLocation): String {
        val city = location.city?.takeIf { it.isNotBlank() }
        val district = location.district?.takeIf { it.isNotBlank() && it != city }
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

    // ============ System Positioning (FusedLocation & Native LocationManager) ============

    suspend fun requestSystemLocation(
        timeoutMillis: Long = 8000L,
        highAccuracy: Boolean = false
    ): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "requestSystemLocation: 无定位权限，跳过")
            return@withContext null
        }

        // 1. 尝试 GMS Fused Location
        val hasGms = try {
            com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) ==
                com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (_: Exception) {
            false
        }

        if (hasGms) {
            Log.i(TAG, "尝试 GMS FusedLocation, highAccuracy=$highAccuracy...")
            val fused = try {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                    requestFusedLocation(timeoutMillis, highAccuracy)
                }
            } catch (e: Exception) {
                Log.w(TAG, "GMS FusedLocation timeout or exception: ${e.message}")
                null
            }
            if (fused != null) {
                Log.i(TAG, "GMS FusedLocation 定位成功: lat=${fused.latitude}, lon=${fused.longitude}")
                return@withContext fused
            }
            Log.w(TAG, "GMS FusedLocation 失败或超时，降级尝试原生 LocationManager...")
        }

        // 2. 尝试原生 LocationManager
        requestNativeLocation(timeoutMillis, highAccuracy)
    }

    private suspend fun requestFusedLocation(timeoutMillis: Long, highAccuracy: Boolean): Location? {
        return try {
            val location = suspendCancellableCoroutine<Location?> { cont ->
                try {
                    val hasFine = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val priority = if (hasFine) {
                        if (highAccuracy) {
                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
                        } else {
                            com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
                        }
                    } else {
                        com.google.android.gms.location.Priority.PRIORITY_LOW_POWER
                    }
                    val requestInterval = if (highAccuracy) 1000L else timeoutMillis
                    val request = com.google.android.gms.location.LocationRequest.Builder(priority, requestInterval)
                        .setMaxUpdates(if (highAccuracy) 4 else 1)
                        .setMinUpdateIntervalMillis(requestInterval)
                        .build()

                    var bestLocation: Location? = null
                    var finished = false
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    lateinit var callback: com.google.android.gms.location.LocationCallback

                    fun finish() {
                        if (finished) return
                        finished = true
                        handler.removeCallbacksAndMessages(null)
                        try {
                            fusedLocationClient.removeLocationUpdates(callback)
                        } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(bestLocation)
                    }

                    val finishRunnable = Runnable { finish() }

                    callback = object : com.google.android.gms.location.LocationCallback() {
                        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                            val locations = result.locations.ifEmpty { listOfNotNull(result.lastLocation) }
                            locations.forEach { loc ->
                                bestLocation = betterLocation(bestLocation, loc)
                            }
                            val best = bestLocation
                            if (!highAccuracy || best.isGoodEnough()) {
                                finish()
                            }
                        }
                    }

                    cont.invokeOnCancellation {
                        handler.removeCallbacksAndMessages(null)
                        try {
                            fusedLocationClient.removeLocationUpdates(callback)
                        } catch (_: Exception) {}
                    }

                    if (highAccuracy) {
                        handler.postDelayed(finishRunnable, timeoutMillis)
                    }
                    fusedLocationClient.requestLocationUpdates(
                        request,
                        callback,
                        android.os.Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "FusedLocation 请求异常", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
            location
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "FusedLocation 异常", e)
            null
        }
    }

    private suspend fun requestNativeLocation(timeoutMillis: Long, highAccuracy: Boolean = false): Location? {
        return try {
            val nativeLocManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (nativeLocManager == null) {
                Log.w(TAG, "NativeLocation: 无法获取系统 LocationManager")
                return null
            }

            // 1. 优先检查并复用近期有效的高精度 LastKnownLocation
            val gpsLastKnown = try {
                nativeLocManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            } catch (_: SecurityException) { null }
            if (gpsLastKnown != null && shouldUseLastKnown(gpsLastKnown, highAccuracy)) {
                Log.i(TAG, "NativeLocation: 使用近期的 GPS lastKnown: lat=${gpsLastKnown.latitude}, lon=${gpsLastKnown.longitude}")
                return gpsLastKnown
            }

            val netLastKnown = try {
                nativeLocManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) { null }
            if (netLastKnown != null && shouldUseLastKnown(netLastKnown, highAccuracy)) {
                Log.i(TAG, "NativeLocation: 使用近期的 Network lastKnown: lat=${netLastKnown.latitude}, lon=${netLastKnown.longitude}")
                return netLastKnown
            }

            val providers = nativeLocManager.getProviders(true)
            if (providers.isEmpty()) {
                Log.w(TAG, "NativeLocation: 无任何可用的 Location Provider")
                return null
            }

            // 2. 双通道并行定位策略：常规场景取最快；前台强定位采样后择优。
            Log.i(TAG, "NativeLocation: 启动 GPS & Network 双通道并行定位, highAccuracy=$highAccuracy, 超时=${timeoutMillis}ms")
            val activeLoc = if (highAccuracy) {
                requestBestParallelLocation(nativeLocManager, providers, timeoutMillis)
            } else {
                requestParallelLocation(nativeLocManager, providers, timeoutMillis)
            }
            if (activeLoc != null) {
                Log.i(TAG, "NativeLocation: 并行定位成功: provider=${activeLoc.provider}, lat=${activeLoc.latitude}, lon=${activeLoc.longitude}")
                return activeLoc
            }

            // 3. 尝试 Passive 定位作为最后原生兜底
            if (providers.contains(android.location.LocationManager.PASSIVE_PROVIDER)) {
                Log.i(TAG, "NativeLocation: 并行定位均超时或失败，尝试 Passive 定位...")
                val passiveLoc = requestSingleProviderLocation(nativeLocManager, android.location.LocationManager.PASSIVE_PROVIDER, 2000L)
                if (passiveLoc != null) {
                    Log.i(TAG, "NativeLocation: Passive 定位成功")
                    return passiveLoc
                }
            }

            Log.w(TAG, "NativeLocation: 所有并行及兜底定位方式均已失败")
            null
        } catch (e: Exception) {
            Log.e(TAG, "NativeLocation 发生异常", e)
            null
        }
    }

    private fun shouldUseLastKnown(location: Location, highAccuracy: Boolean): Boolean {
        val age = System.currentTimeMillis() - location.time
        if (age < 0L) return false
        return if (highAccuracy) {
            age <= RECENT_LAST_KNOWN_MS && location.accuracyOrDefault() <= GOOD_ACCURACY_METERS
        } else {
            val maxAge = if (location.provider == android.location.LocationManager.GPS_PROVIDER) {
                10 * 60 * 1000L
            } else {
                5 * 60 * 1000L
            }
            age <= maxAge
        }
    }

    private suspend fun requestBestParallelLocation(
        nativeLocManager: android.location.LocationManager,
        providers: List<String>,
        timeoutMillis: Long
    ): Location? {
        return try {
            suspendCancellableCoroutine<Location?> { cont ->
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                var bestLocation: Location? = null
                var finished = false
                lateinit var listener: android.location.LocationListener

                fun finish() {
                    if (finished) return
                    finished = true
                    try {
                        nativeLocManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(bestLocation)
                }

                val finishRunnable = Runnable { finish() }
                listener = object : android.location.LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        bestLocation = betterLocation(bestLocation, loc)
                        Log.i(TAG, "BestParallelLocation: 收到 ${loc.provider}, accuracy=${loc.accuracyOrDefault()}m, best=${bestLocation?.provider}")
                        if (bestLocation.isGoodEnough()) {
                            handler.removeCallbacks(finishRunnable)
                            finish()
                        }
                    }

                    @Deprecated("Deprecated in API")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                cont.invokeOnCancellation {
                    handler.removeCallbacks(finishRunnable)
                    try {
                        nativeLocManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                }

                try {
                    var registeredAny = false
                    if (providers.contains(android.location.LocationManager.GPS_PROVIDER)) {
                        nativeLocManager.requestLocationUpdates(
                            android.location.LocationManager.GPS_PROVIDER,
                            0L, 0f, listener, android.os.Looper.getMainLooper()
                        )
                        registeredAny = true
                    }
                    if (providers.contains(android.location.LocationManager.NETWORK_PROVIDER)) {
                        nativeLocManager.requestLocationUpdates(
                            android.location.LocationManager.NETWORK_PROVIDER,
                            0L, 0f, listener, android.os.Looper.getMainLooper()
                        )
                        registeredAny = true
                    }

                    if (registeredAny) {
                        handler.postDelayed(finishRunnable, timeoutMillis)
                    } else if (cont.isActive) {
                        cont.resume(null)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "requestBestParallelLocation SecurityException", e)
                    if (cont.isActive) cont.resume(null)
                } catch (e: Exception) {
                    Log.e(TAG, "requestBestParallelLocation Exception", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestBestParallelLocation error", e)
            null
        }
    }

    private suspend fun requestParallelLocation(
        nativeLocManager: android.location.LocationManager,
        providers: List<String>,
        timeoutMillis: Long
    ): Location? {
        return try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val listener = object : android.location.LocationListener {
                        @Volatile
                        private var hasResumed = false

                        override fun onLocationChanged(loc: Location) {
                            synchronized(this) {
                                if (!hasResumed) {
                                    hasResumed = true
                                    Log.i(TAG, "ParallelLocation: 收到定位数据来自 ${loc.provider}, lat=${loc.latitude}, lon=${loc.longitude}")
                                    if (cont.isActive) cont.resume(loc)
                                    try {
                                        nativeLocManager.removeUpdates(this)
                                    } catch (_: Exception) {}
                                }
                            }
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

                    try {
                        var registeredAny = false
                        if (providers.contains(android.location.LocationManager.GPS_PROVIDER)) {
                            nativeLocManager.requestLocationUpdates(
                                android.location.LocationManager.GPS_PROVIDER,
                                0L, 0f, listener, android.os.Looper.getMainLooper()
                            )
                            registeredAny = true
                        }
                        if (providers.contains(android.location.LocationManager.NETWORK_PROVIDER)) {
                            nativeLocManager.requestLocationUpdates(
                                android.location.LocationManager.NETWORK_PROVIDER,
                                0L, 0f, listener, android.os.Looper.getMainLooper()
                            )
                            registeredAny = true
                        }

                        if (!registeredAny) {
                            if (cont.isActive) cont.resume(null)
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "requestParallelLocation SecurityException", e)
                        if (cont.isActive) cont.resume(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "requestParallelLocation Exception", e)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestParallelLocation error", e)
            null
        }
    }

    private suspend fun requestSingleProviderLocation(
        nativeLocManager: android.location.LocationManager,
        provider: String,
        timeoutMillis: Long
    ): Location? {
        return try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(loc: Location) {
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
                            0L,
                            0f,
                            listener,
                            android.os.Looper.getMainLooper()
                        )
                    } catch (e: SecurityException) {
                        Log.e(TAG, "requestSingleProviderLocation SecurityException", e)
                        if (cont.isActive) cont.resume(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "requestSingleProviderLocation Exception", e)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestSingleProviderLocation error", e)
            null
        }
    }

    private fun betterLocation(current: Location?, candidate: Location?): Location? {
        if (candidate == null) return current
        if (current == null) return candidate
        val currentAccuracy = current.accuracyOrDefault()
        val candidateAccuracy = candidate.accuracyOrDefault()
        val candidateIsFresh = candidate.time > current.time + 2_000L
        val candidateIsMuchMoreAccurate = candidateAccuracy + 20f < currentAccuracy
        val candidateIsAccurateAndFresh = candidateAccuracy <= currentAccuracy + 25f && candidateIsFresh
        val candidateUsesGps = candidate.provider == android.location.LocationManager.GPS_PROVIDER &&
            current.provider != android.location.LocationManager.GPS_PROVIDER &&
            candidateAccuracy <= ACCEPTABLE_ACCURACY_METERS

        return if (candidateIsMuchMoreAccurate || candidateIsAccurateAndFresh || candidateUsesGps) {
            candidate
        } else {
            current
        }
    }

    private fun Location?.isGoodEnough(): Boolean {
        val location = this ?: return false
        val age = System.currentTimeMillis() - location.time
        return location.accuracyOrDefault() <= GOOD_ACCURACY_METERS && age in 0L..RECENT_ACTIVE_FIX_MS
    }

    private fun Location.accuracyOrDefault(): Float {
        return if (hasAccuracy()) accuracy else Float.MAX_VALUE
    }

    // ============ Unified Entrance for Positioning (System -> Amap) ============

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
            val dist = distanceBetween(lat, lon, cached.latitude, cached.longitude)
            
            // 1. 时间衰减判定：若缓存记录早于 5 分钟前，强制放行更新，打破“静止后不更新”的死锁
            val timeDiff = time - cached.time
            val isCacheExpired = timeDiff > 5 * 60 * 1000L // 5分钟
            
            // 2. 基站跳变过滤（针对 1.5km 级跳变场景）：
            // 若新定位精度较差（如基站定位，精度半径 > 300m），且缓存未过期，
            // 并且上次缓存的精度更好，同时上次缓存点落在新定位误差圆内部（dist < accuracy），
            // 说明这次的大范围跳变大概率是由于基站切换产生的“粗略定位”，实际位置并未发生剧烈改变，应予以过滤并复用高精度缓存。
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
                return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
            }

            // 3. 常规微小防抖动：在缓存未过期的情况下，如果位移小于 200m，
            // 且新定位的精度并未“显著优于”旧定位（如新精度 >= 旧精度，或新定位精度本身大于 100m），则判定为抖动，复用旧位置。
            if (!isCacheExpired && dist < 200f && (accuracy >= cached.accuracy || accuracy > 100f)) {
                Log.i(TAG, "防跳变机制触发：新位置距离上次缓存仅 ${dist}米（< 200m），复用旧坐标与地名: (${cached.latitude}, ${cached.longitude}) - ${cached.name}")
                return cached
            }
        }
        return CachedLocation(latitude = lat, longitude = lon, name = name, time = time, accuracy = accuracy)
    }

    suspend fun requestSystemOrIpLocation(highAccuracy: Boolean = false): CachedLocation? {
        val profile = LocationRequestProfile(
            highAccuracy = highAccuracy,
            timeoutMillis = if (highAccuracy) HIGH_ACCURACY_TIMEOUT_MS else 8000L
        )
        Log.i(TAG, "定位总入口被调用（系统自带优先+高德兜底，已剔除IP定位）, highAccuracy=$highAccuracy...")

        if (hasLocationPermission()) {
            // 1. 优先尝试手机自带的系统定位服务（GMS Fused / 原生 LocationManager），增加 3 次重试机制以提高冷启动成功率
            var sysLoc: Location? = null
            val maxAttempts = if (highAccuracy) 1 else 3
            for (attempt in 1..maxAttempts) {
                sysLoc = requestSystemLocation(profile.timeoutMillis, profile.highAccuracy)
                if (sysLoc != null) break
                if (attempt < maxAttempts) {
                    Log.w(TAG, "系统自带定位第 ${attempt} 次失败，等待 1 秒后重试...")
                    kotlinx.coroutines.delay(1000L)
                }
            }

            if (sysLoc != null) {
                val name = reverseGeocode(
                    sysLoc.latitude,
                    sysLoc.longitude,
                    forceRefresh = highAccuracy,
                    accuracy = sysLoc.accuracy
                )
                Log.i(TAG, "系统自带定位成功: lat=${sysLoc.latitude}, lon=${sysLoc.longitude}, name=$name")
                
                // 若系统定位拿到了经纬度，但地名解析失败，则启动高德定位进行地名补全与二次校准
                if (name == "未知位置" || name.isBlank()) {
                    Log.w(TAG, "系统定位成功但地址解析为未知，降级启动高德定位以补全位置名...")
                    val amapLoc = requestAmapLocation()
                    if (amapLoc != null) {
                        val amapName = resolveLocationName(amapLoc)
                        if (amapName != "未知位置" && amapName.isNotBlank()) {
                            val distanceToSystem = distanceBetween(
                                sysLoc.latitude,
                                sysLoc.longitude,
                                amapLoc.latitude,
                                amapLoc.longitude
                            )
                            if (distanceToSystem <= ACCEPTABLE_ACCURACY_METERS || sysLoc.accuracyOrDefault() <= ACCEPTABLE_ACCURACY_METERS) {
                                Log.i(TAG, "高德辅助解析成功，仅采用名称: distanceToSystem=${distanceToSystem}m, name=$amapName")
                                return applyAntiJitter(
                                    sysLoc.latitude,
                                    sysLoc.longitude,
                                    amapName,
                                    sysLoc.accuracy,
                                    sysLoc.time,
                                    highAccuracy
                                )
                            }
                            Log.i(TAG, "系统定位地址未知且精度较差，采用高德兜底坐标与名称: distanceToSystem=${distanceToSystem}m")
                            return applyAntiJitter(
                                amapLoc.latitude,
                                amapLoc.longitude,
                                amapName,
                                amapLoc.accuracy,
                                amapLoc.time,
                                highAccuracy
                            )
                        }
                    }
                }
                
                return applyAntiJitter(sysLoc.latitude, sysLoc.longitude, name, sysLoc.accuracy, sysLoc.time, highAccuracy)
            }
            Log.w(TAG, "系统自带定位失败或超时，降级尝试高德定位服务作为最终兜底...")

            // 2. 降级尝试高德定位 SDK 作为兜底
            val amapLoc = requestAmapLocation()
            if (amapLoc != null) {
                val name = resolveLocationName(amapLoc)
                Log.i(TAG, "高德兜底定位成功: lat=${amapLoc.latitude}, lon=${amapLoc.longitude}, name=$name")
                return applyAntiJitter(amapLoc.latitude, amapLoc.longitude, name, amapLoc.accuracy, amapLoc.time, highAccuracy)
            }
            Log.w(TAG, "系统自带定位与高德兜底定位均已失败，已剔除IP定位，直接返回null")
        } else {
            Log.w(TAG, "无定位权限，且已剔除IP定位，拒绝自动定位")
        }
        return null
    }

    // ============ Location Name Resolution (Geocoder fallback) ============

    suspend fun reverseGeocode(
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false,
        accuracy: Float = 0f
    ): String = withContext(Dispatchers.IO) {
        val cached = getCachedLocation()
        if (!forceRefresh && cached != null && cached.name.isNotBlank() && cached.name != "未知位置") {
            val dist = distanceBetween(lat, lon, cached.latitude, cached.longitude)
            val timeDiff = System.currentTimeMillis() - cached.time

            val cacheLimit = when {
                dist < 35f -> 3 * 60 * 1000L
                accuracy > COARSE_ACCURACY_METERS && dist < 120f -> 5 * 60 * 1000L
                else -> 60 * 1000L
            }
            val isCacheExpired = timeDiff > cacheLimit

            if (!isCacheExpired && dist < 120f) {
                Log.i(TAG, "reverseGeocode: 距离上次缓存位置仅为 ${dist}米且未过期，复用缓存位置名称: ${cached.name}")
                return@withContext cached.name
            }
        }

        val systemResult = try {
            kotlinx.coroutines.withTimeoutOrNull(4000L) {
                geocoderFallback(lat, lon)
            }
        } catch (e: Exception) {
            Log.w(TAG, "reverseGeocode: 系统 Geocoder 抛出异常", e)
            null
        }

        if (systemResult != null && systemResult != "未知位置" && systemResult.isNotBlank()) {
            return@withContext systemResult
        }

        // 系统 Geocoder 失败，尝试 BigDataCloud Web 逆地理编码 (中国大陆友好，无 Key 免费方案)
        Log.i(TAG, "reverseGeocode: 系统 Geocoder 失败，尝试 BigDataCloud Web 逆地理编码...")
        val bdcResult = queryBigDataCloud(lat, lon)
        if (bdcResult != null && bdcResult != "未知位置" && bdcResult.isNotBlank()) {
            Log.i(TAG, "reverseGeocode: BigDataCloud 解析成功: $bdcResult")
            return@withContext bdcResult
        }

        // 尝试 Nominatim Web 逆地理编码 (与 Breezy Weather 对齐的备用方案)
        Log.i(TAG, "reverseGeocode: BigDataCloud 失败，尝试 Nominatim Web 逆地理编码...")
        val osmResult = queryNominatim(lat, lon)
        if (osmResult != null && osmResult != "未知位置" && osmResult.isNotBlank()) {
            Log.i(TAG, "reverseGeocode: Nominatim 解析成功: $osmResult")
            return@withContext osmResult
        }

        "未知位置"
    }

    @Suppress("DEPRECATION")
    private fun geocoderFallback(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val city = normalizeLocationPart(addr.locality)
                val district = normalizeLocationPart(addr.subLocality)
                    ?.takeIf { it != city }
                val detail = listOfNotNull(
                    normalizeFineLocationPart(addr.featureName),
                    normalizeFineLocationPart(addr.thoroughfare),
                    normalizeFineLocationPart(addr.getAddressLine(0))
                )
                    .distinct()
                    .firstOrNull { part -> part != city && part != district }

                val result = listOfNotNull(district ?: city, detail)
                    .distinct()
                    .joinToString(" ")
                Log.d(TAG, "geocoderFallback($lat,$lon): city=$city, district=$district, detail=$detail, result=${result.ifBlank { "EMPTY" }}")
                result.ifEmpty { "未知位置" }
            } else {
                Log.w(TAG, "geocoderFallback($lat,$lon): no addresses returned")
                "未知位置"
            }
        } catch (e: Exception) {
            Log.w(TAG, "geocoderFallback($lat,$lon): exception: ${e.message}")
            "未知位置"
        }
    }

    private fun normalizeLocationPart(value: String?): String? {
        val normalized = value
            ?.replace(Regex("\\s+"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        return normalized.removeCommonLocationNoise()
    }

    private fun normalizeFineLocationPart(value: String?): String? {
        val normalized = normalizeLocationPart(value) ?: return null
        val cleaned = normalized
            .removePrefix("中国")
            .removeCommonLocationNoise()
            .removeAdministrativePrefix()
            .extractBuildingName()
            .truncateAfterUsefulLocationSuffix()
            .takeIf { it.isNotBlank() }
            ?: return null
        if (cleaned.matches(Regex("^\\d+[号弄栋幢单元室]?$"))) return null
        return cleaned.take(14)
    }

    private fun String.extractBuildingName(): String {
        // Match a street/road/number prefix followed by a building/landmark name
        val pattern = Regex("^.*?(?:街|路|道|巷|号|弄|区|园|村)(.+?(?:大厦|大楼|写字楼|中心|广场|大厅|公馆|公寓|小区|花园|阁|轩|馆|院|大戏院|剧院|学校|大学|医院|大酒店|酒店|商厦|大商场|商场|超市|大门|正门|北门|南门|东门|西门|地铁站|公交站|厂|大厂|TCL))$")
        val match = pattern.find(this)
        if (match != null) {
            val candidate = match.groupValues[1].trim()
            if (candidate.length >= 2) {
                return candidate
            }
        }
        return this
    }

    private fun String.removeCommonLocationNoise(): String {
        return replace("附近", "")
            .replace("中国", "")
            .trim()
    }

    private fun String.removeAdministrativePrefix(): String {
        return replace(Regex("^.*?(?:省|自治区|特别行政区)"), "")
            .replace(Regex("^.*?(?:市|自治州|地区|盟)"), "")
            .replace(Regex("^.*?(?:区|县|自治县|旗)"), "")
    }

    private fun String.truncateAfterUsefulLocationSuffix(): String {
        val pattern = Regex("(.+?(?:街道|大道|大街|公路|高速|快速路|路|街|巷|弄|镇|乡|村|社区|广场|公园|园区|商圈))(?:\\d+.*|[甲乙丙丁戊己庚辛壬癸]座.*|[东西南北中]门.*|出入口.*|附近.*|$)")
        val match = pattern.find(this)
        if (match != null) return match.groupValues[1]
        return replace(Regex("\\d+号.*$"), "")
            .replace(Regex("\\d+弄.*$"), "")
            .replace(Regex("\\d+栋.*$"), "")
            .replace(Regex("\\d+幢.*$"), "")
    }

    private suspend fun queryBigDataCloud(lat: Double, lon: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=zh"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SkyPulseWeatherApp/1.0 (com.skypulse.weather)")
                    .build()

                val shortTimeoutClient = okHttpClient.newBuilder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()

                shortTimeoutClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "BigDataCloud API error: ${response.code}")
                        return@withContext null
                    }
                    val bodyStr = response.body?.string() ?: return@withContext null
                    val json = org.json.JSONObject(bodyStr)

                    val localityInfo = json.optJSONObject("localityInfo")
                    val adminList = localityInfo?.optJSONArray("administrative")
                    if (adminList != null && adminList.length() > 0) {
                        for (i in adminList.length() - 1 downTo 0) {
                            val item = adminList.optJSONObject(i)
                            val name = item?.optString("name")
                            if (!name.isNullOrBlank() && name != "中华人民共和国" && !name.endsWith("省")) {
                                val cleaned = normalizeFineLocationPart(name)
                                if (cleaned != null) return@withContext cleaned
                            }
                        }
                    }

                    val city = json.optString("city").takeIf { it.isNotBlank() }
                    val locality = json.optString("locality").takeIf { it.isNotBlank() }
                    val fallback = locality ?: city
                    fallback?.let { normalizeFineLocationPart(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "BigDataCloud query failed", e)
                null
            }
        }
    }

    private suspend fun queryNominatim(lat: Double, lon: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=zh"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SkyPulseWeatherApp/1.0 (com.skypulse.weather)")
                    .build()

                val shortTimeoutClient = okHttpClient.newBuilder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .build()

                shortTimeoutClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Nominatim API error: ${response.code}")
                        return@withContext null
                    }
                    val bodyStr = response.body?.string() ?: return@withContext null
                    val json = org.json.JSONObject(bodyStr)

                    val address = json.optJSONObject("address")
                    val road = address?.optString("road")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("pedestrian")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("suburb")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("quarter")?.takeIf { it.isNotBlank() }

                    if (road != null) {
                        val cleaned = normalizeFineLocationPart(road)
                        if (cleaned != null) return@withContext cleaned
                    }

                    val displayName = json.optString("display_name")
                    if (!displayName.isNullOrBlank()) {
                        val firstPart = displayName.split(",").firstOrNull()?.trim()
                        if (!firstPart.isNullOrBlank() && firstPart != "中国") {
                            val cleaned = normalizeFineLocationPart(firstPart)
                            if (cleaned != null) return@withContext cleaned
                        }
                    }

                    val city = address?.optString("city")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("town")?.takeIf { it.isNotBlank() }
                        ?: address?.optString("village")?.takeIf { it.isNotBlank() }
                    city?.let { normalizeLocationPart(it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Nominatim query failed", e)
                null
            }
        }
    }
}
