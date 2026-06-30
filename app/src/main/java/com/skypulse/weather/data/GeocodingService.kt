package com.skypulse.weather.data

import android.util.Log
import com.skypulse.weather.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CityEntry(
    val name: String,
    val province: String,
    val lat: Double,
    val lon: Double
)

@JsonClass(generateAdapter = true)
data class TiandituLocation(
    val lon: String,
    val lat: String,
    val score: Int? = null,
    val level: String? = null,
    val keyWord: String? = null
)

@JsonClass(generateAdapter = true)
data class TiandituResponse(
    val status: String? = null,
    val msg: String? = null,
    val location: TiandituLocation? = null
)

@JsonClass(generateAdapter = true)
data class GeoAddressComponent(
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
data class GeoReverseResult(
    val formatted_address: String? = null,
    val addressComponent: GeoAddressComponent? = null
)

@JsonClass(generateAdapter = true)
data class GeoReverseResponse(
    val status: String? = null,
    val msg: String? = null,
    val result: GeoReverseResult? = null
)

@JsonClass(generateAdapter = true)
data class AdminCenter(
    val lng: Double? = null,
    val lat: Double? = null
)

@JsonClass(generateAdapter = true)
data class AdminDistrict(
    val name: String? = null,
    val gb: String? = null,
    val center: AdminCenter? = null,
    val level: Int? = null
)

@JsonClass(generateAdapter = true)
data class AdminSuggestion(
    val name: String? = null,
    val gb: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminData(
    val suggestion: List<AdminSuggestion>? = null,
    val district: List<AdminDistrict>? = null
)

@JsonClass(generateAdapter = true)
data class AdminResponse(
    val status: Any? = null,
    val message: String? = null,
    val data: AdminData? = null
) {
    fun isOk(): Boolean {
        if (status is Int) return status == 200
        if (status is String) return status == "200"
        return false
    }
}

class GeocodingService() {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(TiandituResponse::class.java)
    private val reverseAdapter = moshi.adapter(GeoReverseResponse::class.java)
    private val adminAdapter = moshi.adapter(AdminResponse::class.java)

    companion object {
        private const val GEOCODER = "https://api.tianditu.gov.cn/geocoder"
        private const val ADMIN_API = "http://api.tianditu.gov.cn/v2/administrative"
        private const val TIMEOUT_MS = 10_000
    }

    suspend fun search(query: String): List<CityEntry> {
        if (query.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val key = BuildConfig.T_MAP_KEY
                if (key.isBlank()) {
                    Log.e("GeocodingService", "T_MAP_KEY is not configured")
                    return@withContext emptyList()
                }

                val adminResults = administrativeSearch(key, query)
                if (adminResults != null) {
                    Log.d("CityDB.search", "admin returned ${adminResults.size} results for '$query'")
                    return@withContext adminResults
                }

                Log.d("CityDB.search", "admin returned null, falling to single geocode for '$query'")
                val singleResult = singleGeocodeSearch(key, query)
                Log.d("CityDB.search", "single geocode returned ${singleResult.size} results for '$query'")
                singleResult
            } catch (e: Exception) {
                Log.e("GeocodingService", "search failed", e)
                emptyList()
            }
        }
    }

    private fun administrativeSearch(key: String, query: String): List<CityEntry>? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$ADMIN_API?keyword=$encodedQuery&tk=$key"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            Log.d("GeocodingService", "admin API response for '$query': $body")

            val response = adminAdapter.fromJson(body)
            if (response == null || !response.isOk()) {
                Log.w("GeocodingService", "admin API failed: status=${response?.status} msg=${response?.message}")
                return null
            }

            val data = response.data ?: run {
                Log.w("GeocodingService", "admin API: data is null, body=$body")
                return null
            }

            val districts = data.district.orEmpty()
            if (districts.isNotEmpty()) {
                val results = districts.mapNotNull { dist ->
                    val center = dist.center ?: return@mapNotNull null
                    val lat = center.lat ?: return@mapNotNull null
                    val lng = center.lng ?: return@mapNotNull null
                    val entryName = dist.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val (name, province) = reverseGeocode(lat, lng, "区县", entryName)
                    CityEntry(name = name, province = province, lat = lat, lon = lng)
                }
                if (results.isNotEmpty()) return results
            }

            val suggestions = data.suggestion.orEmpty()
            if (suggestions.isNotEmpty()) {
                val queryName = query.trim()
                val results = suggestions.mapNotNull { sug ->
                    val sugName = sug.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val fullName = "$sugName$queryName"
                    geocodeFullName(key, fullName, queryName)
                }
                if (results.isNotEmpty()) return results
            }

            Log.d("GeocodingService", "admin API: no usable results, falling back")
            null
        } catch (e: Exception) {
            Log.w("GeocodingService", "administrative search failed", e)
            null
        }
    }

    private fun geocodeFullName(key: String, fullName: String, displayName: String): CityEntry? {
        return try {
            val ds = """{"keyWord":"$fullName"}"""
            val encodedDs = URLEncoder.encode(ds, "UTF-8")
            val url = "$GEOCODER?ds=$encodedDs&tk=$key"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val geoResponse = responseAdapter.fromJson(body)
            if (geoResponse?.status != "0") return null

            val loc = geoResponse.location ?: return null
            val lon = loc.lon.toDoubleOrNull() ?: return null
            val lat = loc.lat.toDoubleOrNull() ?: return null

            val (name, province) = reverseGeocode(lat, lon, loc.level ?: "", displayName)
            CityEntry(name = name, province = province, lat = lat, lon = lon)
        } catch (e: Exception) {
            Log.w("GeocodingService", "geocode full name failed: $fullName", e)
            null
        }
    }

    private fun singleGeocodeSearch(key: String, query: String): List<CityEntry> {
        try {
            val ds = """{"keyWord":"$query"}"""
            val encodedDs = URLEncoder.encode(ds, "UTF-8")
            val url = "$GEOCODER?ds=$encodedDs&tk=$key"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val response = responseAdapter.fromJson(body)
            if (response?.status != "0") {
                Log.w("GeocodingService", "Tianditu error: status=${response?.status} msg=${response?.msg}")
                return emptyList()
            }

            val loc = response.location ?: return emptyList()
            val lon = loc.lon.toDoubleOrNull() ?: return emptyList()
            val lat = loc.lat.toDoubleOrNull() ?: return emptyList()
            val geoLevel = loc.level ?: ""

            val (name, province) = reverseGeocode(lat, lon, geoLevel, query.trim())
            Log.d("GeocodingService", "Tianditu: found '$name' ($province) at ($lat, $lon) level=$geoLevel")

            return listOf(CityEntry(name = name, province = province, lat = lat, lon = lon))
        } catch (e: Exception) {
            Log.e("GeocodingService", "Tianditu search failed", e)
            return emptyList()
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double, geoLevel: String, fallbackName: String): Pair<String, String> {
        return try {
            val key = BuildConfig.T_MAP_KEY
            if (key.isBlank()) return fallbackName to ""

            val postStr = """{"lon":$lon,"lat":$lat}"""
            val encodedPost = URLEncoder.encode(postStr, "UTF-8")
            val url = "$GEOCODER?postStr=$encodedPost&format=json&tk=$key"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val geoResponse = reverseAdapter.fromJson(body) ?: return fallbackName to ""
            if (geoResponse.status != "0") {
                Log.w("GeocodingService", "reverse geocode failed: status=${geoResponse.status}")
                return fallbackName to ""
            }

            val comp = geoResponse.result?.addressComponent
            if (comp == null) {
                Log.w("GeocodingService", "reverse geocode: no addressComponent")
                return fallbackName to ""
            }

            val provinceName = comp.province?.takeIf { it.isNotBlank() }
            val cityName = comp.city?.takeIf { it.isNotBlank() }
            val countyName = comp.county?.takeIf { it.isNotBlank() }
            val townName = comp.town?.takeIf { it.isNotBlank() }

            val isCountyLevel = geoLevel.contains("区") || geoLevel.contains("县")
            val isTownLevel = geoLevel.contains("乡") || geoLevel.contains("镇") || geoLevel.contains("村")

            val effectiveName: String
            val districtInfo: String

            when {
                isTownLevel && townName != null && townName != countyName && townName != cityName -> {
                    effectiveName = townName
                    districtInfo = buildString {
                        countyName?.let { append(it) }
                        cityName?.takeIf { it != countyName }?.let {
                            if (isNotEmpty()) append("，"); append(it)
                        }
                        provinceName?.takeIf { it != cityName }?.let {
                            if (isNotEmpty()) append("，"); append(it)
                        }
                    }
                }
                isCountyLevel && countyName != null && countyName != cityName -> {
                    effectiveName = countyName
                    districtInfo = buildString {
                        cityName?.let { append(it) }
                        provinceName?.takeIf { it != cityName }?.let {
                            if (isNotEmpty()) append("，"); append(it)
                        }
                    }
                }
                else -> {
                    effectiveName = cityName ?: fallbackName
                    districtInfo = provinceName ?: ""
                }
            }

            effectiveName to districtInfo
        } catch (e: Exception) {
            Log.w("GeocodingService", "reverse geocode failed", e)
            fallbackName to ""
        }
    }
}
