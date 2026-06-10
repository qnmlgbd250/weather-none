package com.skypulse.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
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
        const val DEFAULT_LONGITUDE = 116.4074
        const val DEFAULT_LATITUDE = 39.9042
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
     * Build location name from AMapLocation result.
     * Priority: AOI (building/park-level) > district + street > city > address > "未知位置"
     * Uses aoiName (polygon-based) instead of poiName (point-based) to avoid flickering.
     */
    fun resolveLocationName(location: AMapLocation): String {
        val city = location.city?.takeIf { it.isNotBlank() }
        val district = location.district?.takeIf { it.isNotBlank() && it != city }
        val aoi = location.aoiName?.takeIf { it.isNotBlank() }
        val street = location.street?.takeIf { it.isNotBlank() }
        val streetNum = location.streetNum?.takeIf { it.isNotBlank() }

        return buildString {
            // Prefix: district or city
            when {
                district != null -> append(district)
                city != null -> append(city)
            }
            // Detail: prefer AOI (building/park level), fallback to street
            when {
                aoi != null -> append(" $aoi")
                street != null -> {
                    if (isNotEmpty()) append(" ")
                    append(street)
                    streetNum?.let { append(it) }
                }
            }
            // Final fallback
            if (isEmpty()) {
                location.address?.takeIf { it.isNotBlank() }?.let { append(it) }
            }
        }.ifEmpty { "\u672a\u77e5\u4f4d\u7f6e" }
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
