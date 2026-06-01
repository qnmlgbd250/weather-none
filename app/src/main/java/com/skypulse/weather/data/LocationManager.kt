package com.skypulse.weather.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
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

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LocationManager"
        private const val DEFAULT_LOCATION_NAME = "北京市"
        const val DEFAULT_LONGITUDE = 116.4074
        const val DEFAULT_LATITUDE = 39.9042
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
        location.city?.takeIf { it.isNotBlank() }?.let { append(it) }
        location.district?.takeIf { it.isNotBlank() && it != location.city }?.let { append(it) }
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
                200f,
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
                                        addr.city?.takeIf { it.isNotBlank() }?.let { append(it) }
                                        addr.district?.takeIf { it.isNotBlank() && it != addr.city }?.let { append(it) }
                                        val pois = addr.pois
                                        val poi = pois?.firstOrNull { !it.title.isNullOrBlank() }
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
