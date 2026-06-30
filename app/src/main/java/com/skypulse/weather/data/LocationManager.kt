package com.skypulse.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.skypulse.weather.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext val context: Context
) {
    data class CachedLocation(
        val latitude: Double,
        val longitude: Double,
        val name: String
    )

    companion object {
        private const val TAG = "LocationManager"
        const val DEFAULT_LONGITUDE = 116.4074
        const val DEFAULT_LATITUDE = 39.9042
        private const val PREFS_NAME = "location_cache"
        private const val KEY_CACHED_LAT = "cached_lat"
        private const val KEY_CACHED_LON = "cached_lon"
        private const val KEY_CACHED_NAME = "cached_name"

        private const val T_REVERSE_ENDPOINT = "https://api.tianditu.gov.cn/geocoder"
        private const val T_TIMEOUT_MS = 8_000

        private var privacyAgreed = false

        fun ensurePrivacyAgreed(context: Context) {
            if (!privacyAgreed) {
                AMapLocationClient.updatePrivacyShow(context, true, true)
                AMapLocationClient.updatePrivacyAgree(context, true)
                privacyAgreed = true
            }
        }
    }

    private val cachePrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @JsonClass(generateAdapter = true)
    data class TiandituAddressComponent(
        val nation: String? = null,
        val province: String? = null,
        val city: String? = null,
        val county: String? = null,
        val town: String? = null,
        val road: String? = null,
        val address: String? = null,
        val poi: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class TiandituReverseResult(
        val formatted_address: String? = null,
        val addressComponent: TiandituAddressComponent? = null
    )

    @JsonClass(generateAdapter = true)
    data class TiandituReverseResponse(
        val status: String? = null,
        val msg: String? = null,
        val result: TiandituReverseResult? = null
    )

    private val reverseAdapter = moshi.adapter(TiandituReverseResponse::class.java)

    fun hasBackgroundLocationPermission(): Boolean {
        val hasForeground = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasForeground) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
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

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    // ============ AMAP GPS Positioning ============

    suspend fun requestAmapLocation(): AMapLocation? {
        return requestAmapLocation(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy, 15_000L)
    }

    suspend fun requestLightweightAmapLocation(): AMapLocation? {
        return requestLightweightAmapLocation(8_000L)
    }

    suspend fun requestLightweightAmapLocation(timeoutMillis: Long): AMapLocation? {
        return requestAmapLocation(AMapLocationClientOption.AMapLocationMode.Battery_Saving, timeoutMillis)
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
                            cont.resume(location)
                        } else {
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
            Log.w(TAG, "resolveLocationName: all fields empty, " +
                "city=${location.city}, district=${location.district}, " +
                "aoiName=${location.aoiName}, street=${location.street}, " +
                "address=${location.address}, errorCode=${location.errorCode}, " +
                "locationDetail=${location.locationDetail}")
            if (city != null) return city
            return "未知位置"
        }

        return result
    }

    // ============ Tianditu Reverse Geocoding ============

    suspend fun reverseGeocode(lat: Double, lon: Double): String {
        return tiandituReverseGeocode(lat, lon) ?: geocoderFallback(lat, lon)
    }

    private suspend fun tiandituReverseGeocode(lat: Double, lon: Double): String? {
        val key = BuildConfig.T_MAP_KEY
        if (key.isBlank()) return null
        return try {
            val postStr = """{"lon":$lon,"lat":$lat}"""
            val encodedPost = URLEncoder.encode(postStr, "UTF-8")
            val url = "$T_REVERSE_ENDPOINT?postStr=$encodedPost&format=json&tk=$key"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = T_TIMEOUT_MS
                readTimeout = T_TIMEOUT_MS
                instanceFollowRedirects = true
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val response = reverseAdapter.fromJson(body) ?: return null
            if (response.status != "0") {
                Log.w(TAG, "Tianditu reverse geocode failed: status=${response.status}")
                return null
            }

            val result = response.result ?: return null
            val comp = result.addressComponent
            if (comp == null) {
                result.formatted_address?.takeIf { it.isNotBlank() }?.let { return it }
                return null
            }

            buildString {
                comp.city?.takeIf { it.isNotBlank() }?.let { append(it) }
                comp.county?.takeIf { it.isNotBlank() && it != comp.city }?.let {
                    if (isNotEmpty()) append("")
                    append(it)
                }
                comp.town?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("")
                    append(it)
                }
                comp.poi?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" ")
                    append(it)
                }
                if (isEmpty()) {
                    comp.road?.takeIf { it.isNotBlank() }?.let { append(it) }
                }
                if (isEmpty()) {
                    result.formatted_address?.takeIf { it.isNotBlank() }?.let { append(it) }
                }
            }.ifBlank { null }.also { name ->
                Log.d(TAG, "tiandituReverseGeocode($lat,$lon): result=$name")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tianditu reverse geocode failed", e)
            null
        }
    }

    private fun geocoderFallback(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val result = buildString {
                    addr.locality?.let { append(it) }
                    addr.subLocality?.let { append(it) }
                    val thoroughfare = addr.thoroughfare?.takeIf { it.isNotBlank() }
                    val subThoroughfare = addr.subThoroughfare?.takeIf { it.isNotBlank() }
                    if (thoroughfare != null) {
                        append(" $thoroughfare")
                        subThoroughfare?.let { append(it) }
                    }
                }
                Log.d(TAG, "geocoderFallback($lat,$lon): locality=${addr.locality}, " +
                    "subLocality=${addr.subLocality}, thoroughfare=${addr.thoroughfare}, " +
                    "result=${result.ifBlank { "EMPTY" }}")
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
}
