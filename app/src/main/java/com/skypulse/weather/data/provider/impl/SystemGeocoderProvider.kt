package com.skypulse.weather.data.provider.impl

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.skypulse.weather.data.provider.IGeocodingProvider
import com.skypulse.weather.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemGeocoderProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : IGeocodingProvider {

    companion object {
        private const val TAG = "SystemGeocoderProvider"
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.CHINA)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val area = selectGeocoderArea(addr)
                val detail = buildGeocoderDetail(addr, area)
                val result = listOfNotNull(area, detail)
                    .joinToString(" ")
                Log.d(TAG, "geocoderFallback($latitude,$longitude): area=$area, detail=$detail, result=${result.ifBlank { "EMPTY" }}")
                
                val details = "line=${addr.getAddressLine(0).safeLogValue()}, country=${addr.countryName.safeLogValue()}, admin=${addr.adminArea.safeLogValue()}, subAdmin=${addr.subAdminArea.safeLogValue()}, locality=${addr.locality.safeLogValue()}, subLocality=${addr.subLocality.safeLogValue()}, thoroughfare=${addr.thoroughfare.safeLogValue()}, subThoroughfare=${addr.subThoroughfare.safeLogValue()}, feature=${addr.featureName.safeLogValue()}"
                FileLogger.i(TAG, "reverseGeocode[system_geocoder_raw]: coord=${latitude},${longitude}, result=${result.ifEmpty { "未知位置" }}, $details")
                
                result.ifEmpty { null }
            } else {
                Log.w(TAG, "geocoderFallback($latitude,$longitude): no addresses returned")
                FileLogger.i(TAG, "reverseGeocode[system_geocoder_raw]: coord=${latitude},${longitude}, result=null, no_addresses")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "geocoderFallback($latitude,$longitude): exception: ${e.message}")
            FileLogger.i(TAG, "reverseGeocode[system_geocoder_raw]: coord=${latitude},${longitude}, result=null, exception=${e.javaClass.simpleName}:${e.message}")
            null
        }
    }

    private fun selectGeocoderArea(addr: android.location.Address): String? {
        val subLocality = normalizeRawGeocoderText(addr.subLocality)
        val subAdminArea = normalizeRawGeocoderText(addr.subAdminArea)
        val locality = normalizeRawGeocoderText(addr.locality)
        val adminArea = normalizeRawGeocoderText(addr.adminArea)
        return subLocality
            ?: subAdminArea?.takeIf { it.endsWith("区") || it.endsWith("县") || it.endsWith("旗") }
            ?: locality
            ?: subAdminArea
            ?: adminArea
    }

    private fun buildGeocoderDetail(addr: android.location.Address, area: String?): String? {
        extractGeocoderDetailFromAddressLine(addr, area)?.let { return it }

        val street = normalizeRawGeocoderText(addr.thoroughfare)
        val streetNumber = normalizeRawGeocoderText(addr.subThoroughfare)
        val streetPart = listOfNotNull(street, streetNumber)
            .joinToString("")
            .takeIf { it.isNotBlank() }

        val rawParts = listOfNotNull(
            streetPart,
            normalizeRawGeocoderText(addr.premises),
            normalizeRawGeocoderText(addr.featureName)
        )
        val detail = mergeGeocoderDetailParts(rawParts, area)
            .takeIf { it.isNotBlank() }
        return detail
    }

    private fun mergeGeocoderDetailParts(parts: List<String>, area: String?): String {
        val merged = mutableListOf<String>()
        parts.filterNot { it == area }.forEach { part ->
            if (merged.any { existing -> existing.contains(part) }) return@forEach
            val coveredIndex = merged.indexOfFirst { existing -> part.contains(existing) }
            if (coveredIndex >= 0) {
                merged[coveredIndex] = part
            } else {
                merged += part
            }
        }
        return merged.joinToString("")
    }

    private fun extractGeocoderDetailFromAddressLine(
        addr: android.location.Address,
        area: String?
    ): String? {
        var detail = normalizeRawGeocoderText(addr.getAddressLine(0)) ?: return null
        if (!area.isNullOrBlank()) {
            val index = detail.indexOf(area)
            if (index >= 0) {
                return detail.substring(index + area.length)
                    .trim()
                    .takeIf { it.isNotBlank() && it != area }
            }
        }

        listOf(
            addr.countryName,
            addr.adminArea,
            addr.subAdminArea,
            addr.locality,
            addr.subLocality,
            area
        )
            .mapNotNull { normalizeRawGeocoderText(it) }
            .distinct()
            .forEach { prefix ->
                detail = detail.removePrefix(prefix)
            }
        return detail.takeIf { it.isNotBlank() && it != area }
    }

    private fun normalizeRawGeocoderText(value: String?): String? {
        return value
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun String?.safeLogValue(maxLength: Int = 80): String {
        val value = this?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() } ?: "null"
        return if (value.length <= maxLength) value else value.take(maxLength) + "..."
    }
}
