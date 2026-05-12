package com.skypulse.weather.data

import android.content.Context
import android.content.SharedPreferences
import com.skypulse.weather.model.City
import com.squareup.moshi.Moshi

class CityManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "sky_pulse_cities"
        private const val KEY_CITIES = "cities_json"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder().build()
    private val cityAdapter = moshi.adapter(City::class.java)

    fun getCities(): List<City> {
        val json = prefs.getString(KEY_CITIES, null) ?: return emptyList()
        return try {
            parseCityArray(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCities(cities: List<City>) {
        val json = buildCityArrayJson(cities)
        prefs.edit().putString(KEY_CITIES, json).apply()
    }

    fun addCity(city: City) {
        val current = getCities().toMutableList()
        if (current.any { it.id == city.id }) return
        current.add(city)
        saveCities(current)
    }

    fun removeCity(cityId: String) {
        val current = getCities().toMutableList()
        current.removeAll { it.id == cityId && !it.isCurrentLocation }
        saveCities(current)
    }

    fun hasCity(cityId: String): Boolean {
        return getCities().any { it.id == cityId }
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
        val reader = com.squareup.moshi.JsonReader.of(
            okio.Buffer().writeUtf8(json)
        )
        reader.beginArray()
        while (reader.hasNext()) {
            cityAdapter.fromJson(reader)?.let { cities.add(it) }
        }
        reader.endArray()
        return cities
    }
}
