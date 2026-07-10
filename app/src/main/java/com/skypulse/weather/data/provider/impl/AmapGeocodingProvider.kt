package com.skypulse.weather.data.provider.impl

import android.util.Log
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.LocationNameNormalizer
import com.skypulse.weather.data.provider.IGeocodingProvider
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmapGeocodingProvider @Inject constructor(
    private val okHttpClient: OkHttpClient
) : IGeocodingProvider {

    companion object {
        private const val TAG = "AmapGeocodingProvider"
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.AMAP_WEB_API_KEY
        if (apiKey.isBlank()) {
            Log.e(TAG, "AMAP_WEB_API_KEY is blank, skip Web Geocoding")
            return@withContext null
        }

        try {
            // 构造高德 REST API 逆地理编码请求 URL
            val url = String.format(
                Locale.US,
                "https://restapi.amap.com/v3/geocode/regeo?key=%s&location=%.6f,%.6f&output=json",
                apiKey, longitude, latitude
            )

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SkyPulseWeatherApp/1.0 (com.skypulse.weather)")
                .build()

            val shortTimeoutClient = okHttpClient.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()

            shortTimeoutClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "AMap Regeo API error: ${response.code}")
                    FileLogger.w(TAG, "reverseGeocode[amap_regeo]: HTTP error code ${response.code}")
                    return@withContext null
                }

                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)

                val status = json.optString("status")
                if (status != "1") {
                    val info = json.optString("info")
                    Log.w(TAG, "AMap Regeo API status != 1: $info")
                    FileLogger.w(TAG, "reverseGeocode[amap_regeo]: status=$status, info=$info")
                    return@withContext null
                }

                val regeocode = json.optJSONObject("regeocode") ?: return@withContext null
                val addressComponent = regeocode.optJSONObject("addressComponent")

                val province = addressComponent?.let { optStringOrNull(it, "province") }
                val city = addressComponent?.let { optStringOrNull(it, "city") }
                val district = addressComponent?.let { optStringOrNull(it, "district") }
                val township = addressComponent?.let { optStringOrNull(it, "township") }

                // 获取 AOI 名字 (一般在 aois 数组里首位)
                var aoiName: String? = null
                val aois = regeocode.optJSONArray("aois")
                if (aois != null && aois.length() > 0) {
                    val firstAoi = aois.optJSONObject(0)
                    if (firstAoi != null) {
                        aoiName = optStringOrNull(firstAoi, "name")
                    }
                }

                // 街道信息
                val streetNumber = addressComponent?.optJSONObject("streetNumber")
                val street = streetNumber?.let { optStringOrNull(it, "street") }
                val streetNum = streetNumber?.let { optStringOrNull(it, "number") }

                // 解析地名组合
                val targetCity = city ?: province
                val cleanCity = targetCity?.takeIf { it.isNotBlank() }
                val cleanDistrict = district?.takeIf { it.isNotBlank() && it != cleanCity }
                val cleanAoi = aoiName?.takeIf { it.isNotBlank() }
                val cleanStreet = street?.takeIf { it.isNotBlank() }
                val cleanStreetNum = streetNum?.takeIf { it.isNotBlank() }

                val result = buildString {
                    when {
                        cleanDistrict != null -> append(cleanDistrict)
                        cleanCity != null -> append(cleanCity)
                    }
                    when {
                        cleanAoi != null -> append(" $cleanAoi")
                        cleanStreet != null -> {
                            if (isNotEmpty()) append(" ")
                            append(cleanStreet)
                            cleanStreetNum?.let { append(it) }
                        }
                    }
                }.trim()

                val finalResult = if (result.isBlank()) {
                    val formatted = regeocode.optString("formatted_address")
                    formatted.takeIf { it.isNotBlank() } ?: cleanCity
                } else {
                    result
                }

                val normalizedResult = finalResult?.let { LocationNameNormalizer.normalizeAddressDetail(it) ?: it }

                val details = "province=$province, city=$city, district=$district, township=$township, aoi=$aoiName, street=$street"
                FileLogger.i(TAG, "reverseGeocode[amap_regeo]: coord=${latitude},${longitude}, result=$normalizedResult, $details")

                return@withContext normalizedResult?.takeIf { it.isNotBlank() && it != "未知位置" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AMap Web Regeo failed", e)
            FileLogger.e(TAG, "reverseGeocode[amap_regeo] exception: ${e.message}", e)
            null
        }
    }

    private fun optStringOrNull(jsonObject: JSONObject, key: String): String? {
        val value = jsonObject.opt(key) ?: return null
        if (value is String) {
            return value.takeIf { it.isNotBlank() && it != "[]" }
        }
        return null
    }
}
