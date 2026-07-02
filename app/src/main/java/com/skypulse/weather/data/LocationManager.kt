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
import java.util.Locale
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

        @Suppress("UNUSED_PARAMETER")
        fun ensurePrivacyAgreed(context: Context) {
            // AMap is removed, this is a dummy no-op method for compatibility
            privacyAgreed = true
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
            val fused = requestFusedLocation(timeoutMillis)
            if (fused != null) {
                Log.i(TAG, "GMS FusedLocation 定位成功: lat=${fused.latitude}, lon=${fused.longitude}")
                return@withContext fused
            }
            Log.w(TAG, "GMS FusedLocation 失败，降级尝试原生 LocationManager...")
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

            val providers = nativeLocManager.getProviders(true)
            val provider = when {
                providers.contains(android.location.LocationManager.NETWORK_PROVIDER) ->
                    android.location.LocationManager.NETWORK_PROVIDER
                providers.contains(android.location.LocationManager.PASSIVE_PROVIDER) ->
                    android.location.LocationManager.PASSIVE_PROVIDER
                providers.contains(android.location.LocationManager.GPS_PROVIDER) ->
                    android.location.LocationManager.GPS_PROVIDER
                else -> {
                    Log.w(TAG, "NativeLocation: 无可用 provider")
                    return null
                }
            }

            val lastKnown = try {
                nativeLocManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
            if (lastKnown != null) {
                val age = System.currentTimeMillis() - lastKnown.time
                if (age < 15 * 60 * 1000) {
                    Log.i(TAG, "NativeLocation: 使用近期的 lastKnown: lat=${lastKnown.latitude}, lon=${lastKnown.longitude}")
                    return lastKnown
                }
            }

            Log.i(TAG, "NativeLocation: 请求 $provider 定位, timeout=${timeoutMillis}ms")
            kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(loc: Location) {
                                Log.i(TAG, "NativeLocation 定位成功: lat=${loc.latitude}, lon=${loc.longitude}")
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
                        Log.e(TAG, "NativeLocation SecurityException", e)
                        if (cont.isActive) cont.resume(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "NativeLocation 异常", e)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "NativeLocation 发生异常", e)
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

    suspend fun requestSystemOrIpLocation(): CachedLocation? {
        Log.i(TAG, "定位总入口被调用...")
        
        // 1. 尝试系统自带定位服务 (需要位置权限)
        if (hasLocationPermission()) {
            val sysLoc = requestSystemLocation()
            if (sysLoc != null) {
                val name = reverseGeocode(sysLoc.latitude, sysLoc.longitude)
                Log.i(TAG, "系统定位成功: lat=${sysLoc.latitude}, lon=${sysLoc.longitude}, name=$name")
                return CachedLocation(latitude = sysLoc.latitude, longitude = sysLoc.longitude, name = name)
            }
            Log.w(TAG, "系统定位未获取到有效坐标，降级使用 IP 定位...")
        } else {
            Log.i(TAG, "无定位权限，直接跳过系统定位进入 IP 定位...")
        }

        // 2. 降级使用 IP 定位 (无需位置权限)
        val ipLoc = requestIpLocation()
        if (ipLoc != null) {
            Log.i(TAG, "IP 定位成功: lat=${ipLoc.latitude}, lon=${ipLoc.longitude}, name=${ipLoc.name}")
            return ipLoc
        }

        Log.w(TAG, "所有自动定位策略（系统定位 & IP 定位）均已宣告失败")
        return null
    }

    // ============ Location Name Resolution (Geocoder fallback) ============

    suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                geocoderFallback(lat, lon)
            } ?: "未知位置"
        } catch (_: Exception) {
            "未知位置"
        }
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
}
