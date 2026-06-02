package com.skypulse.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.skypulse.weather.model.WeatherResponse
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "sky_pulse_weather")

@Singleton
class WeatherDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {

    companion object {
        private const val CACHE_MAX_AGE_MS = 30 * 60 * 1000L // 30 minutes
    }

    private val adapter = moshi.adapter(WeatherResponse::class.java)

    suspend fun save(cityId: String, weather: WeatherResponse) {
        val key = stringPreferencesKey(cityId)
        val timeKey = longPreferencesKey("${cityId}_time")
        context.weatherDataStore.edit { prefs ->
            prefs[key] = adapter.toJson(weather)
            prefs[timeKey] = System.currentTimeMillis()
        }
    }

    suspend fun load(cityId: String): WeatherResponse? {
        val key = stringPreferencesKey(cityId)
        val timeKey = longPreferencesKey("${cityId}_time")
        val prefs = context.weatherDataStore.data.first()
        val json = prefs[key] ?: return null
        val time = prefs[timeKey] ?: return null
        if (System.currentTimeMillis() - time > CACHE_MAX_AGE_MS) return null
        return try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadCached(cityId: String): WeatherResponse? {
        val key = stringPreferencesKey(cityId)
        val prefs = context.weatherDataStore.data.first()
        val json = prefs[key] ?: return null
        return try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }
    suspend fun remove(cityId: String) {
        val key = stringPreferencesKey(cityId)
        val timeKey = longPreferencesKey("${cityId}_time")
        context.weatherDataStore.edit { prefs ->
            prefs.remove(key)
            prefs.remove(timeKey)
        }
    }
}
