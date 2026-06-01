package com.skypulse.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.skypulse.weather.model.City
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cityDataStore: DataStore<Preferences> by preferencesDataStore(name = "sky_pulse_cities")

@Singleton
class CityDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {

    companion object {
        private const val KEY_CITIES = "cities_json"
    }

    private val cityAdapter = moshi.adapter(City::class.java)

    suspend fun getCities(): List<City> {
        val key = stringPreferencesKey(KEY_CITIES)
        val prefs = context.cityDataStore.data.first()
        val json = prefs[key] ?: return emptyList()
        return try {
            parseCityArray(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveCities(cities: List<City>) {
        val key = stringPreferencesKey(KEY_CITIES)
        val json = buildCityArrayJson(cities)
        context.cityDataStore.edit { prefs ->
            prefs[key] = json
        }
    }

    suspend fun addCity(city: City) {
        val current = getCities().toMutableList()
        if (current.any { it.id == city.id }) return
        current.add(city)
        saveCities(current)
    }

    suspend fun removeCity(cityId: String) {
        val current = getCities().toMutableList()
        current.removeAll { it.id == cityId && !it.isCurrentLocation }
        saveCities(current)
    }

    private fun buildCityArrayJson(cities: List<City>): String {
        if (cities.isEmpty()) return "[]"
        val sb = StringBuilder("[")
        cities.forEachIndexed { index, city ->
            if (index > 0) sb.append(",")
            sb.append(cityAdapter.toJson(city))
        }
        sb.append("]")
        return sb.toString()
    }

    private fun parseCityArray(json: String): List<City> {
        if (json.isBlank() || json == "[]") return emptyList()
        val cities = mutableListOf<City>()
        val reader = com.squareup.moshi.JsonReader.of(okio.Buffer().writeUtf8(json))
        reader.beginArray()
        while (reader.hasNext()) {
            cityAdapter.fromJson(reader)?.let { cities.add(it) }
        }
        reader.endArray()
        return cities
    }
}
