package com.skypulse.weather.domain

import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.model.City
import com.skypulse.weather.repository.CityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 城市管理的业务逻辑封装。
 *
 * 封装城市 CRUD，ViewModel 不再直接操作 CityRepository。
 * 职责：协调城市数据变更。
 */
@Singleton
class ManageCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val locationManager: LocationManager
) {

    /**
     * 获取所有城市。
     */
    suspend fun getCities(): List<City> {
        return cityRepository.getCities()
    }

    /**
     * 添加新城市。返回更新后的完整城市列表。
     */
    suspend fun addCity(name: String, longitude: Double, latitude: Double): Pair<City, List<City>> {
        val city = City(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            longitude = longitude,
            latitude = latitude,
            isCurrentLocation = false
        )
        cityRepository.addCity(city)
        val updatedCities = cityRepository.getCities()
        return city to updatedCities
    }

    /**
     * 删除城市（定位城市不可删除）。返回更新后的完整城市列表。
     */
    suspend fun removeCity(cityId: String): List<City> {
        cityRepository.removeCity(cityId)
        val updatedCities = cityRepository.getCities()
        return updatedCities
    }

    /**
     * 更新城市信息。
     */
    suspend fun updateCity(city: City) {
        cityRepository.updateCity(city)
    }

    /**
     * 确保存在定位城市。如果不存在，创建一个默认的。
     * 返回更新后的完整城市列表。
     */
    suspend fun ensureCurrentLocationCity(existingCities: List<City>): List<City> {
        val hasCurrentLocation = existingCities.any { it.isCurrentLocation }
        if (hasCurrentLocation) return existingCities

        val cachedLocation = locationManager.getCachedLocation()
        val currentLocationCity = City(
            id = "current_location",
            name = cachedLocation?.name ?: "定位中...",
            longitude = cachedLocation?.longitude ?: LocationManager.DEFAULT_LONGITUDE,
            latitude = cachedLocation?.latitude ?: LocationManager.DEFAULT_LATITUDE,
            isCurrentLocation = true
        )
        val updatedCities = existingCities.toMutableList().apply {
            add(0, currentLocationCity)
        }
        cityRepository.saveCities(updatedCities)
        return updatedCities
    }

    /**
     * 保存城市列表（全量替换）。
     */
    suspend fun saveCities(cities: List<City>) {
        cityRepository.saveCities(cities)
    }

    /**
     * 更新定位城市名称。返回更新后的完整城市列表。
     */
    suspend fun updateCurrentLocationCityName(cities: List<City>, name: String): List<City> {
        val index = cities.indexOfFirst { it.isCurrentLocation }
        if (index < 0) return cities
        val updatedCities = cities.toMutableList().apply {
            this[index] = this[index].copy(name = name)
        }
        cityRepository.saveCities(updatedCities)
        return updatedCities
    }

    /**
     * 更新定位城市坐标。返回更新后的完整城市列表。
     */
    suspend fun updateCurrentLocationCityCoords(cities: List<City>, lon: Double, lat: Double): List<City> {
        val index = cities.indexOfFirst { it.isCurrentLocation }
        if (index < 0) return cities
        val updatedCities = cities.toMutableList().apply {
            this[index] = this[index].copy(longitude = lon, latitude = lat)
        }
        cityRepository.saveCities(updatedCities)
        return updatedCities
    }

    /**
     * 从 SharedPreferences 迁移城市数据到 Room。
     */
    suspend fun migrateFromSharedPreferences(json: String) {
        cityRepository.migrateFromSharedPreferences(json)
    }
}
