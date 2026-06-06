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
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class CachedLocation(
        val latitude: Double,
        val longitude: Double,
        val name: String
    )

    companion object {
        private const val TAG = "LocationManager"
        private const val DEFAULT_LOCATION_NAME = "\u5317\u4eac\u5e02"
        const val DEFAULT_LONGITUDE = 116.4074
        const val DEFAULT_LATITUDE = 39.9042
        private const val CACHE_RADIUS_METERS = 500f
        private const val PREFS_NAME = "location_cache"
        private const val KEY_CACHED_LAT = "cached_lat"
        private const val KEY_CACHED_LON = "cached_lon"
        private const val KEY_CACHED_NAME = "cached_name"
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

    fun getCachedLocation(): CachedLocation? {
        val lat = cachePrefs.getFloat(KEY_CACHED_LAT, 0f).toDouble()
        val lon = cachePrefs.getFloat(KEY_CACHED_LON, 0f).toDouble()
        val name = cachePrefs.getString(KEY_CACHED_NAME, null)?.takeIf { it.isNotBlank() }
        if (lat == 0.0 || lon == 0.0 || name == null) return null
        return CachedLocation(latitude = lat, longitude = lon, name = name)
    }

    private fun saveCachedLocation(lat: Double, lon: Double, name: String) {
        cachePrefs.edit()
            .putFloat(KEY_CACHED_LAT, lat.toFloat())
            .putFloat(KEY_CACHED_LON, lon.toFloat())
            .putString(KEY_CACHED_NAME, name)
            .apply()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * \u8ba1\u7b97\u4e24\u4e2a\u5750\u6807\u4e4b\u95f4\u7684\u8ddd\u79bb\uff08\u7c73\uff09
     */
    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    /**
     * Request current GPS location via AMap SDK.
     * Returns null if location fails.
     */
    suspend fun requestLocation(): Location? {
        return try {
            val client = AMapLocationClient(context)
            val option = AMapLocationClientOption().apply {
                isOnceLocation = true
                isNeedAddress = true
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                httpTimeOut = 15_000L
            }
            client.setLocationOption(option)

            val result = suspendCancellableCoroutine { cont ->
                client.setLocationListener { location ->
                    if (cont.isActive) {
                        if (location != null && location.errorCode == 0) {
                            cont.resume(location)
                        } else {
                            Log.w(TAG, "AMap error: ${location?.errorCode} ${location?.errorInfo}")
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

            result?.let { loc ->
                Location("amap").apply {
                    latitude = loc.latitude
                    longitude = loc.longitude
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AMap location failed", e)
            null
        }
    }

    /**
     * Get a human-readable location name from an AMap location result.
     */
    fun buildLocationName(location: AMapLocation): String = buildString {
        val city = location.city?.takeIf { it.isNotBlank() }
        val district = location.district?.takeIf { it.isNotBlank() && it != city }
        when {
            district != null -> append(district)
            city != null -> append(city)
        }
        val poi = location.poiName?.takeIf { it.isNotBlank() }
        val street = location.street?.takeIf { it.isNotBlank() }
        val streetNum = location.streetNum?.takeIf { it.isNotBlank() }
        when {
            poi != null -> append(" $poi")
            street != null -> {
                append(street)
                streetNum?.let { append(it) }
            }
        }
        if (isEmpty()) {
            location.address?.takeIf { it.isNotBlank() }?.let { append(it) }
        }
    }

    /**
     * Request location and return the full AMapLocation result (includes poiName, city, district, etc.)
     */
    suspend fun requestAmapLocation(): AMapLocation? {
        return try {
            val client = AMapLocationClient(context)
            val option = AMapLocationClientOption().apply {
                isOnceLocation = true
                isNeedAddress = true
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                httpTimeOut = 15_000L
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

    /**
     * Build location name from a full AMapLocation result, prioritizing POI name.
     * Uses coordinate-based caching to prevent POI name flickering at the same location.
     */
    /**
     * Build location name from a full AMapLocation result, prioritizing POI name.
     * Uses SharedPreferences-based coordinate caching to prevent POI name flickering.
     */
    fun resolveLocationName(location: AMapLocation): String {
        val lat = location.latitude
        val lon = location.longitude

        // \u5982\u679c\u8ddd\u79bb\u4e0a\u6b21\u5b9a\u4f4d\u70b9 < 500\u7c73\uff0c\u76f4\u63a5\u8fd4\u56de\u7f13\u5b58\u7684\u540d\u79f0
        val cLat = cachePrefs.getFloat(KEY_CACHED_LAT, 0f).toDouble()
        val cLon = cachePrefs.getFloat(KEY_CACHED_LON, 0f).toDouble()
        val cName = cachePrefs.getString(KEY_CACHED_NAME, null)
        if (cName != null && cLat != 0.0 && cLon != 0.0) {
            if (distanceBetween(lat, lon, cLat, cLon) < CACHE_RADIUS_METERS) {
                return cName
            }
        }

        val city = location.city?.takeIf { it.isNotBlank() }
        val district = location.district?.takeIf { it.isNotBlank() && it != city }
        val poi = location.poiName?.takeIf { it.isNotBlank() }
        val street = location.street?.takeIf { it.isNotBlank() }
        val streetNum = location.streetNum?.takeIf { it.isNotBlank() }
        val name = buildString {
            when {
                district != null -> append(district)
                city != null -> append(city)
            }
            when {
                poi != null -> append(" $poi")
                street != null -> {
                    append(street)
                    streetNum?.let { append(it) }
                }
            }
            if (isEmpty()) {
                location.address?.takeIf { it.isNotBlank() }?.let { append(it) }
            }
        }.ifEmpty { "\u672a\u77e5\u4f4d\u7f6e" }

        // \u66f4\u65b0\u7f13\u5b58
        saveCachedLocation(lat, lon, name)
        return name
    }
    /**
     * Reverse geocode via AMap SDK. Falls back to Android Geocoder on failure.
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): String {
        return amapReverseGeocode(lat, lon) ?: geocoderFallback(lat, lon)
    }

    private suspend fun amapReverseGeocode(lat: Double, lon: Double): String? {
        return try {
            val search = GeocodeSearch(context)
            val query = RegeocodeQuery(
                LatLonPoint(lat, lon),
                1000f,
                GeocodeSearch.AMAP
            )
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine { cont ->
                    search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                        override fun onRegeocodeSearched(
                            result: com.amap.api.services.geocoder.RegeocodeResult?,
                            rCode: Int
                        ) {
                            if (cont.isActive) {
                                if (rCode == 1000 && result?.regeocodeAddress != null) {
                                    val addr = result.regeocodeAddress
                                    val name = buildString {
                                        val rCity = addr.city?.takeIf { it.isNotBlank() }
                                        val rDistrict = addr.district?.takeIf { it.isNotBlank() && it != rCity }
                                        when {
                                            rDistrict != null -> append(rDistrict)
                                            rCity != null -> append(rCity)
                                        }
                                        val pois = addr.pois?.filter { !it.title.isNullOrBlank() }
                                        val poi = pois?.firstOrNull()
                                            ?: pois?.firstOrNull()
                                        val street = addr.streetNumber?.street?.takeIf { it.isNotBlank() }
                                        val streetNum = addr.streetNumber?.number?.takeIf { it.isNotBlank() }
                                        when {
                                            poi != null -> append(" ${poi.title}")
                                            street != null -> {
                                                append(street)
                                                streetNum?.let { append(it) }
                                            }
                                        }
                                        if (isEmpty()) {
                                            addr.formatAddress?.takeIf { it.isNotBlank() }?.let { append(it) }
                                        }
                                    }
                                    cont.resume(name.takeIf { it.isNotBlank() }, null)
                                } else {
                                    cont.resume(null, null)
                                }
                            }
                        }
                        override fun onGeocodeSearched(
                            result: com.amap.api.services.geocoder.GeocodeResult?,
                            rCode: Int
                        ) {}
                    })
                    search.getFromLocationAsyn(query)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun geocoderFallback(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                buildString {
                    addr.locality?.let { append(it) }
                    addr.subLocality?.let { append(it) }
                    val thoroughfare = addr.thoroughfare?.takeIf { it.isNotBlank() }
                    val subThoroughfare = addr.subThoroughfare?.takeIf { it.isNotBlank() }
                    if (thoroughfare != null) {
                        append(" $thoroughfare")
                        subThoroughfare?.let { append(it) }
                    }
                }.ifEmpty { "未知位置" }
            } else {
                "未知位置"
            }
        } catch (_: Exception) {
            "未知位置"
        }
    }
}
