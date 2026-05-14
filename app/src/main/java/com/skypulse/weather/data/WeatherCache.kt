package com.skypulse.weather.data

import android.content.Context
import com.skypulse.weather.api.ApiClient
import com.skypulse.weather.model.WeatherResponse

class WeatherCache(context: Context) {

    companion object {
        private const val PREFS_NAME = "sky_pulse_weather_cache"
        private const val CACHE_MAX_AGE_MS = 30 * 60 * 1000L // 30 minutes
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val adapter = ApiClient.moshi.adapter(WeatherResponse::class.java)

    fun save(cityId: String, weather: WeatherResponse) {
        prefs.edit()
            .putString(cityId, adapter.toJson(weather))
            .putLong("${cityId}_time", System.currentTimeMillis())
            .apply()
    }

    fun load(cityId: String): WeatherResponse? {
        val json = prefs.getString(cityId, null) ?: return null
        val time = prefs.getLong("${cityId}_time", 0)
        if (System.currentTimeMillis() - time > CACHE_MAX_AGE_MS) return null
        return try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun remove(cityId: String) {
        prefs.edit()
            .remove(cityId)
            .remove("${cityId}_time")
            .apply()
    }
}
