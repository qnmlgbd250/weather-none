package com.skypulse.weather.repository

import android.content.Context
import android.util.Log
import com.skypulse.weather.data.local.database.CityDao
import com.skypulse.weather.data.local.database.CityEntity
import com.skypulse.weather.model.City
import com.skypulse.weather.util.CityFileCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 城市数据的唯一入口（SSOT = Room）。
 *
 * 所有写操作自动同步 FileCache，确保 Widget 读到最新数据。
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
        cityDao.deleteAll()
        cityDao.upsertAll(cities.mapIndexed { index, city -> city.toEntity(index) })
        syncFileCache()
    }

    suspend fun addCity(city: City) {
        val current = cityDao.getAll().toMutableList()
        if (current.any { it.id == city.id }) return
        current.add(city.toEntity(current.size))
        cityDao.deleteAll()
        cityDao.upsertAll(current)
        syncFileCache()
    }

    suspend fun removeCity(cityId: String) {
        val current = cityDao.getAll().toMutableList()
        current.removeAll { it.id == cityId && !it.isCurrentLocation }
        cityDao.deleteAll()
        cityDao.upsertAll(current)
        syncFileCache()
    }

    suspend fun updateCity(city: City) {
        val current = cityDao.getAll().toMutableList()
        val index = current.indexOfFirst { it.id == city.id }
        if (index >= 0) {
            current[index] = city.toEntity(index)
            cityDao.deleteAll()
            cityDao.upsertAll(current)
            syncFileCache()
        }
    }

    /**
     * 从 Room 读取最新城市列表并写入 FileCache。
     * 确保 Widget（读 FileCache）与 Room 数据一致。
     */
    private suspend fun syncFileCache() {
        try {
            val cities = cityDao.getAll().map { it.toDomain() }
            CityFileCache.save(context, cities)
        } catch (e: Exception) {
            Log.w("CityRepo", "syncFileCache failed", e)
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
