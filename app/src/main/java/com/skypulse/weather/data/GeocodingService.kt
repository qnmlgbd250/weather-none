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

class GeocodingService() {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val xiaomiListAdapter = moshi.adapter(List::class.java)

    companion object {
        private const val XIAOMI_SEARCH_API = "https://weatherapi.market.xiaomi.com/wtr-v3/location/city/search"
        private const val TIMEOUT_MS = 10_000
    }

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

                val results = xiaomiCitySearch(query, appKey, sign)
                Log.d("GeocodingService", "Xiaomi API returned ${results.size} results for '$query'")
                results
            } catch (e: Exception) {
                Log.e("GeocodingService", "search failed", e)
                emptyList()
            }
        }
    }

    private fun xiaomiCitySearch(query: String, appKey: String, sign: String): List<CityEntry> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$XIAOMI_SEARCH_API?name=$encodedQuery" +
                    "&appKey=$appKey" +
                    "&sign=$sign" +
                    "&romVersion=eng.localh.20231105.141708" +
                    "&appVersion=17000318" +
                    "&alpha=false" +
                    "&isGlobal=false" +
                    "&device=dandelion" +
                    "&modDevice=dandelion" +
                    "&locale=zh_cn" +
                    "&oaid="

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
            }
            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            Log.d("GeocodingService", "Xiaomi API response for '$query': $body")

            val response = xiaomiListAdapter.fromJson(body)
            if (response == null) {
                Log.w("GeocodingService", "Xiaomi API returned null response")
                return emptyList()
            }

            val results = response.mapNotNull { item ->
                try {
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val name = map["name"] as? String ?: return@mapNotNull null
                    val latStr = map["latitude"] as? String ?: return@mapNotNull null
                    val lonStr = map["longitude"] as? String ?: return@mapNotNull null
                    val affiliation = map["affiliation"] as? String ?: ""

                    val lat = latStr.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = lonStr.toDoubleOrNull() ?: return@mapNotNull null

                    // 从 affiliation 字段提取省份信息
                    // 格式: "温州市, 浙江, 中国" 或 "浙江, 中国"
                    val province = extractProvince(affiliation)

                    CityEntry(name = name, province = province, lat = lat, lon = lon)
                } catch (e: Exception) {
                    Log.w("GeocodingService", "Failed to parse Xiaomi city result", e)
                    null
                }
            }

            results
        } catch (e: Exception) {
            Log.e("GeocodingService", "Xiaomi city search failed", e)
            emptyList()
        }
    }

    private fun extractProvince(affiliation: String): String {
        // affiliation 格式: "温州市, 浙江, 中国" 或 "浙江, 中国"
        val parts = affiliation.split(",").map { it.trim() }

        return when {
            parts.size >= 3 -> {
                // "温州市, 浙江, 中国" -> 取中间的省份
                parts[1]
            }
            parts.size == 2 -> {
                // "浙江, 中国" -> 取第一个
                parts[0]
            }
            else -> ""
        }
    }
}
