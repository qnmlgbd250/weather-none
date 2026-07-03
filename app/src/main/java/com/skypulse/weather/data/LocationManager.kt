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
        val name: String
    )

    data class IpGeoResult(
        val province: String?,
        val city: String?,
        val region: String?
    )

    companion object {
        private const val TAG = "LocationManager"
        const val DEFAULT_LONGITUDE = 116.4074
        const val DEFAULT_LATITUDE = 39.9042
        private const val PREFS_NAME = "location_cache"
        private const val KEY_CACHED_LAT = "cached_lat"
        private const val KEY_CACHED_LON = "cached_lon"
        private const val KEY_CACHED_NAME = "cached_name"

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
        if (lat == 0.0 || lon == 0.0 || name == null) return null
        return CachedLocation(latitude = lat, longitude = lon, name = name)
    }

    fun saveCachedLocation(name: String, longitude: Double, latitude: Double) {
        val normalizedName = name.takeIf { it.isNotBlank() } ?: return
        if (latitude == 0.0 || longitude == 0.0) return
        cachePrefs.edit()
            .putFloat(KEY_CACHED_LAT, latitude.toFloat())
            .putFloat(KEY_CACHED_LON, longitude.toFloat())
            .putString(KEY_CACHED_NAME, normalizedName)
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

    suspend fun requestSystemLocation(timeoutMillis: Long = 8000L): Location? = withContext(Dispatchers.IO) {
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
            Log.i(TAG, "尝试 GMS FusedLocation...")
            val fused = try {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                    requestFusedLocation(timeoutMillis)
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
        requestNativeLocation(timeoutMillis)
    }

    private suspend fun requestFusedLocation(timeoutMillis: Long): Location? {
        return try {
            val location = suspendCancellableCoroutine<Location?> { cont ->
                try {
                    val hasFine = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val priority = if (hasFine) {
                        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    } else {
                        com.google.android.gms.location.Priority.PRIORITY_LOW_POWER
                    }
                    val request = com.google.android.gms.location.LocationRequest.Builder(priority, timeoutMillis)
                        .setMaxUpdates(1)
                        .build()

                    val callback = object : com.google.android.gms.location.LocationCallback() {
                        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                            val loc = result.lastLocation
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

    private suspend fun requestNativeLocation(timeoutMillis: Long): Location? {
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
            if (gpsLastKnown != null && (System.currentTimeMillis() - gpsLastKnown.time) < 10 * 60 * 1000) {
                Log.i(TAG, "NativeLocation: 使用近期的 GPS lastKnown: lat=${gpsLastKnown.latitude}, lon=${gpsLastKnown.longitude}")
                return gpsLastKnown
            }

            val netLastKnown = try {
                nativeLocManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) { null }
            if (netLastKnown != null && (System.currentTimeMillis() - netLastKnown.time) < 5 * 60 * 1000) {
                Log.i(TAG, "NativeLocation: 使用近期的 Network lastKnown: lat=${netLastKnown.latitude}, lon=${netLastKnown.longitude}")
                return netLastKnown
            }

            val providers = nativeLocManager.getProviders(true)
            if (providers.isEmpty()) {
                Log.w(TAG, "NativeLocation: 无任何可用的 Location Provider")
                return null
            }

            // 2. 双通道并行定位策略 (GPS 与 Network 同时开启监听，取最快返回的有效位置)
            Log.i(TAG, "NativeLocation: 启动 GPS & Network 双通道并行定位, 超时=${timeoutMillis}ms")
            val activeLoc = requestParallelLocation(nativeLocManager, providers, timeoutMillis)
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

    // ============ IP Geolocation Positioning ============

    suspend fun requestIpLocation(): CachedLocation? {
        Log.i(TAG, "开始获取 IP 地址定位...")
        val geo = fetchIpGeo() ?: fetchIpGeoFallback()
        if (geo == null) {
            Log.w(TAG, "IP 定位获取归属地数据失败")
            return null
        }

        // 按精度高低依次匹配经纬度坐标
        val searchQueries = listOfNotNull(geo.region, geo.city, geo.province)
        for (query in searchQueries) {
            if (query.isBlank()) continue
            try {
                Log.d(TAG, "尝试查询 $query 对应的经纬度...")
                val results = geocodingService.search(query)
                val match = results.firstOrNull()
                if (match != null) {
                    val displayName = geo.region ?: geo.city ?: geo.province ?: match.name
                    Log.i(TAG, "IP 定位与地理编码成功: 归属地=$displayName, query=$query, 坐标=(${match.lon}, ${match.lat})")
                    return CachedLocation(latitude = match.lat, longitude = match.lon, name = displayName)
                }
            } catch (e: Exception) {
                Log.w(TAG, "通过 Geocoding 查询 $query 坐标异常: ${e.message}")
            }
        }
        Log.w(TAG, "IP 定位所解析的归属地无法匹配到任何有效经纬度")
        return null
    }

    private suspend fun fetchIpGeo(): IpGeoResult? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url("https://whois.pconline.com.cn/ipJson.jsp?json=true")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyBytes = response.body?.bytes() ?: return@withContext null
                val jsonString = String(bodyBytes, java.nio.charset.Charset.forName("GBK"))
                val jsonObject = org.json.JSONObject(jsonString)
                val pro = jsonObject.optString("pro").takeIf { !it.isNullOrBlank() && it != "None" }
                val city = jsonObject.optString("city").takeIf { !it.isNullOrBlank() && it != "None" }
                val region = jsonObject.optString("region").takeIf { !it.isNullOrBlank() && it != "None" }
                return@withContext IpGeoResult(province = pro, city = city, region = region)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PCOnline IP 解析异常: ${e.message}")
        }
        null
    }

    private suspend fun fetchIpGeoFallback(): IpGeoResult? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url("https://ipapi.co/json/")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val jsonString = response.body?.string() ?: return@withContext null
                val jsonObject = org.json.JSONObject(jsonString)
                val city = jsonObject.optString("city").takeIf { !it.isNullOrBlank() }
                val regionName = jsonObject.optString("region").takeIf { !it.isNullOrBlank() }
                return@withContext IpGeoResult(province = regionName, city = city, region = null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ipapi.co IP 解析异常: ${e.message}")
        }
        null
    }

    // ============ Unified Entrance for Positioning (System -> IP) ============

    private fun applyAntiJitter(lat: Double, lon: Double, name: String): CachedLocation {
        val cached = getCachedLocation()
        if (cached != null && cached.name.isNotBlank() && cached.name != "未知位置") {
            val dist = distanceBetween(lat, lon, cached.latitude, cached.longitude)
            if (dist < 200f) {
                Log.i(TAG, "防跳变机制触发：新位置距离上次缓存仅 ${dist}米（< 200m），复用缓存坐标与地名: (${cached.latitude}, ${cached.longitude}) - ${cached.name}")
                return cached
            }
        }
        return CachedLocation(latitude = lat, longitude = lon, name = name)
    }

    suspend fun requestSystemOrIpLocation(): CachedLocation? {
        Log.i(TAG, "定位总入口被调用...")

        // 1. 尝试系统定位服务 (需要位置权限)
        if (hasLocationPermission()) {
            // 首选尝试高德定位 SDK，增加重试机制（尝试 3 次，每次间隔 1 秒）以应对刚授予权限后的冷启动延迟
            var amapLoc: AMapLocation? = null
            for (attempt in 1..3) {
                amapLoc = requestAmapLocation()
                if (amapLoc != null) break
                if (attempt < 3) {
                    Log.w(TAG, "高德定位第 ${attempt} 次失败，等待 1 秒后重试...")
                    kotlinx.coroutines.delay(1000L)
                }
            }
            if (amapLoc != null) {
                val name = resolveLocationName(amapLoc)
                Log.i(TAG, "高德定位成功: lat=${amapLoc.latitude}, lon=${amapLoc.longitude}, name=$name")
                val finalLoc = applyAntiJitter(amapLoc.latitude, amapLoc.longitude, name)
                return finalLoc
            }
            Log.w(TAG, "高德定位失败或超时，降级尝试 GMS/系统原生定位服务...")

            // 降级尝试 GMS FusedLocation / 系统原生定位服务
            val sysLoc = requestSystemLocation()
            if (sysLoc != null) {
                val name = reverseGeocode(sysLoc.latitude, sysLoc.longitude)
                Log.i(TAG, "系统定位成功: lat=${sysLoc.latitude}, lon=${sysLoc.longitude}, name=$name")
                val finalLoc = applyAntiJitter(sysLoc.latitude, sysLoc.longitude, name)
                return finalLoc
            }
            Log.w(TAG, "系统定位及高德定位均未获取到有效坐标，降级使用 IP 定位...")
        } else {
            Log.i(TAG, "无定位权限，直接跳过系统定位及高德定位，进入 IP 定位...")
        }

        // 2. 降级使用 IP 定位 (无需位置权限)
        val ipLoc = requestIpLocation()
        if (ipLoc != null) {
            Log.i(TAG, "IP 定位成功: lat=${ipLoc.latitude}, lon=${ipLoc.longitude}, name=${ipLoc.name}")
            // IP 定位精度低，需要更保守的防跳变策略：
            // 如果已有缓存位置（来自上次 GPS），且 IP 定位结果偏差超过 50km，
            // 说明 IP 归属地可能不准（VPN/代理/运营商 NAT），沿用缓存位置。
            val cached = getCachedLocation()
            if (cached != null && cached.name.isNotBlank() && cached.name != "未知位置") {
                val dist = distanceBetween(ipLoc.latitude, ipLoc.longitude, cached.latitude, cached.longitude)
                if (dist > 50_000f) {
                    Log.w(TAG, "IP 定位防跳变：IP 结果(${ipLoc.name})距缓存(${cached.name}) ${dist/1000}km，" +
                        "偏差过大，沿用缓存位置")
                    return cached
                }
            }
            return CachedLocation(latitude = ipLoc.latitude, longitude = ipLoc.longitude, name = ipLoc.name)
        }

        Log.w(TAG, "所有自动定位策略均已宣告失败")
        return null
    }

    // ============ Location Name Resolution (Geocoder fallback) ============

    suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val cached = getCachedLocation()
        if (cached != null && cached.name.isNotBlank() && cached.name != "未知位置") {
            val dist = distanceBetween(lat, lon, cached.latitude, cached.longitude)
            if (dist < 200f) {
                Log.i(TAG, "reverseGeocode: 距离上次缓存位置仅为 ${dist}米，复用缓存位置名称: ${cached.name}")
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
                    normalizeFineLocationPart(addr.thoroughfare),
                    normalizeFineLocationPart(addr.featureName),
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
            .truncateAfterUsefulLocationSuffix()
            .takeIf { it.isNotBlank() }
            ?: return null
        if (cleaned.matches(Regex("^\\d+[号弄栋幢单元室]?$"))) return null
        return cleaned.take(14)
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
