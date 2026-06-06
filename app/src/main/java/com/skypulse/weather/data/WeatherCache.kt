package com.skypulse.weather.data

import android.content.Context
import android.content.SharedPreferences
import com.skypulse.weather.model.WeatherResponse
import com.squareup.moshi.Moshi

/**
 * Lightweight SharedPreferences-based cache for weather data.
 * Used by the widget for reliable cross-process data sharing.
 */
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class WeatherCache @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val PREFS_NAME = "sky_pulse_weather_cache"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val adapter = Moshi.Builder().build().adapter(WeatherResponse::class.java)

    fun save(cityId: String, weather: WeatherResponse) {
        prefs.edit()
            .putString(cityId, adapter.toJson(weather))
            .apply()
    }

    fun load(cityId: String): WeatherResponse? {
        val json = prefs.getString(cityId, null) ?: return null
        return try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
}