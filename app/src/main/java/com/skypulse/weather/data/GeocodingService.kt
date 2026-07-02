package com.skypulse.weather.data

import android.util.Log
import com.skypulse.weather.BuildConfig
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject

data class CityEntry(
    val name: String,
    val province: String,
    val lat: Double,
    val lon: Double
)

@JsonClass(generateAdapter = true)
data class XiaomiCityResult(
    val name: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val affiliation: String? = null,
    val key: String? = null,
    val locationKey: String? = null,
    val status: Int? = null,
    val timeZoneShift: Int? = null
)

interface XiaomiGeocodingApi {
    @GET("wtr-v3/location/city/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("appKey") appKey: String,
        @Query("sign") sign: String,
        @Query("romVersion") romVersion: String = "eng.localh.20231105.141708",
        @Query("appVersion") appVersion: String = "17000318",
        @Query("alpha") alpha: Boolean = false,
        @Query("isGlobal") isGlobal: Boolean = false,
        @Query("device") device: String = "dandelion",
        @Query("modDevice") modDevice: String = "dandelion",
        @Query("locale") locale: String = "zh_cn",
        @Query("oaid") oaid: String = ""
    ): List<XiaomiCityResult>
}

class GeocodingService @Inject constructor(
    private val api: XiaomiGeocodingApi
) {

    suspend fun search(query: String): List<CityEntry> {
        if (query.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val appKey = BuildConfig.XIAOMI_APP_KEY
                val sign = BuildConfig.XIAOMI_SIGN

                if (appKey.isBlank() || sign.isBlank()) {
                    Log.e("GeocodingService", "Xiaomi API credentials are not configured")
                    return@withContext emptyList()
                }

                val response = api.search(query, appKey, sign)
                Log.d("GeocodingService", "Xiaomi API returned ${response.size} results for '$query'")

                response.mapNotNull { item ->
                    try {
                        val name = item.name ?: return@mapNotNull null
                        val latStr = item.latitude ?: return@mapNotNull null
                        val lonStr = item.longitude ?: return@mapNotNull null
                        val affiliation = item.affiliation ?: ""

                        val lat = latStr.toDoubleOrNull() ?: return@mapNotNull null
                        val lon = lonStr.toDoubleOrNull() ?: return@mapNotNull null

                        val province = extractProvince(affiliation)

                        CityEntry(name = name, province = province, lat = lat, lon = lon)
                    } catch (e: Exception) {
                        Log.w("GeocodingService", "Failed to parse Xiaomi city result", e)
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("GeocodingService", "search failed", e)
                emptyList()
            }
        }
    }

    private fun extractProvince(affiliation: String): String {
        val parts = affiliation.split(",").map { it.trim() }
        return when {
            parts.size >= 3 -> parts[1]
            parts.size == 2 -> parts[0]
            else -> ""
        }
    }
}
