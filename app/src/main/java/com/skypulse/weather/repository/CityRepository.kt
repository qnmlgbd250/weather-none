package com.skypulse.weather.repository

import android.content.Context
import com.skypulse.weather.data.local.database.CityDao
import com.skypulse.weather.data.local.database.CityEntity
import com.skypulse.weather.model.City
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 城市数据的唯一入口（SSOT = Room）。
 */
@Singleton
class CityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cityDao: CityDao
) {

    // ============ Read ============

    suspend fun getCities(): List<City> {
        return cityDao.getAll().map { it.toDomain() }
    }

    fun observeCities(): Flow<List<City>> {
        return cityDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getCurrentLocationCity(): City? {
        return cityDao.getAll().firstOrNull { it.isCurrentLocation }?.toDomain()
    }

    // ============ Write ============

    suspend fun saveCities(cities: List<City>) {
        cityDao.replaceAll(cities.mapIndexed { index, city -> city.toEntity(index) })
    }

    suspend fun addCity(city: City) {
        val current = cityDao.getAll()
        if (current.any { it.id == city.id }) return
        cityDao.upsert(city.toEntity(current.size))
    }

    suspend fun removeCity(cityId: String) {
        cityDao.deleteAndReorder(cityId)
    }

    suspend fun updateCity(city: City) {
        val current = cityDao.getAll()
        val existing = current.find { it.id == city.id }
        if (existing != null) {
            cityDao.upsert(city.toEntity(existing.sortOrder))
        }
    }

    // ============ Migration Helper ============

    suspend fun migrateFromSharedPreferences(json: String) {
        if (json.isBlank() || json == "[]") return
        try {
            val cities = parseCityJson(json)
            if (cities.isNotEmpty()) {
                saveCities(cities)
            }
        } catch (_: Exception) {}
    }

    // ============ Mapping ============

    private fun CityEntity.toDomain(): City = City(
        id = id,
        name = name,
        longitude = longitude,
        latitude = latitude,
        isCurrentLocation = isCurrentLocation
    )

    private fun City.toEntity(sortOrder: Int): CityEntity = CityEntity(
        id = id,
        name = name,
        longitude = longitude,
        latitude = latitude,
        isCurrentLocation = isCurrentLocation,
        sortOrder = sortOrder
    )

    private fun parseCityJson(json: String): List<City> {
        val adapter = com.squareup.moshi.Moshi.Builder().build()
            .adapter(City::class.java)
        val cities = mutableListOf<City>()
        val reader = com.squareup.moshi.JsonReader.of(okio.Buffer().writeUtf8(json))
        reader.beginArray()
        while (reader.hasNext()) {
            adapter.fromJson(reader)?.let { cities.add(it) }
        }
        reader.endArray()
        return cities
    }
}
