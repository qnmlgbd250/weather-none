package com.skypulse.weather.repository

import android.content.Context
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
 * 替代 CityDataStore（DataStore）+ CityManager（SharedPreferences）双重存储。
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
        // 事务：先删后插，确保 Room 数据与传入列表完全一致
        cityDao.deleteAll()
        cityDao.upsertAll(cities.mapIndexed { index, city -> city.toEntity(index) })
        // 同步写入文件缓存供 Widget 读取
        CityFileCache.save(context, cities)
    }

    suspend fun addCity(city: City) {
        val current = cityDao.getAll().toMutableList()
        if (current.any { it.id == city.id }) return
        current.add(city.toEntity(current.size))
        cityDao.deleteAll()
        cityDao.upsertAll(current)
    }

    suspend fun removeCity(cityId: String) {
        val current = cityDao.getAll().toMutableList()
        current.removeAll { it.id == cityId && !it.isCurrentLocation }
        cityDao.deleteAll()
        cityDao.upsertAll(current)
    }

    suspend fun updateCity(city: City) {
        val current = cityDao.getAll().toMutableList()
        val index = current.indexOfFirst { it.id == city.id }
        if (index >= 0) {
            current[index] = city.toEntity(index)
            cityDao.deleteAll()
            cityDao.upsertAll(current)
        }
    }

    // ============ Migration Helper ============

    /**
     * 从 SharedPreferences 迁移城市数据到 Room。
     * 只在首次升级时调用。
     */
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
