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

class CityDatabase() {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(TiandituResponse::class.java)

    companion object {
        private const val ENDPOINT = "https://api.tianditu.gov.cn/geocoder"
        private const val TIMEOUT_MS = 10_000
    }

    suspend fun search(query: String): List<CityEntry> {
        if (query.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val key = BuildConfig.T_MAP_KEY
                if (key.isBlank()) {
                    Log.e("CityDatabase", "T_MAP_KEY is not configured")
                    return@withContext emptyList()
                }

                val ds = """{"keyWord":"$query"}"""
                val encodedDs = URLEncoder.encode(ds, "UTF-8")
                val url = "$ENDPOINT?ds=$encodedDs&tk=$key"

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
                    Log.w("CityDatabase", "Tianditu error: status=${response?.status} msg=${response?.msg}")
                    return@withContext emptyList()
                }

                val loc = response.location ?: return@withContext emptyList()
                val lon = loc.lon.toDoubleOrNull() ?: return@withContext emptyList()
                val lat = loc.lat.toDoubleOrNull() ?: return@withContext emptyList()

                val name = query.trim()
                Log.d("CityDatabase", "Tianditu: found '$name' at ($lat, $lon)")

                listOf(
                    CityEntry(name = name, province = "", lat = lat, lon = lon)
                )
            } catch (e: Exception) {
                Log.e("CityDatabase", "Tianditu search failed", e)
                emptyList()
            }
        }
    }
}
