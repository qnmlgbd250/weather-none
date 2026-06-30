package com.skypulse.weather.domain

import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.model.City
import com.skypulse.weather.repository.CityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 城市管理的业务逻辑封装。
 *
 * 所有写操作从 Room 读取最新数据，避免用过时内存列表覆盖 GPS 已更新的名字。
 */
@Singleton
class ManageCityUseCase @Inject constructor(
    private val cityRepository: CityRepository,
    private val locationManager: LocationManager
) {

    suspend fun getCities(): List<City> {
        return cityRepository.getCities()
    }

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

    suspend fun removeCity(cityId: String): List<City> {
        cityRepository.removeCity(cityId)
        val updatedCities = cityRepository.getCities()
        return updatedCities
    }

    suspend fun updateCity(city: City) {
        cityRepository.updateCity(city)
    }

    /**
     * 确保存在定位城市。始终从 Room 读取最新数据。
     */
    suspend fun ensureCurrentLocationCity(): List<City> {
        val cities = cityRepository.getCities()
        val currentCity = cities.find { it.isCurrentLocation }
        if (currentCity != null) return cities

        val cachedLocation = locationManager.getCachedLocation()
        val currentLocationCity = City(
            id = "current_location",
            name = cachedLocation?.name ?: "定位中...",
            longitude = cachedLocation?.longitude ?: LocationManager.DEFAULT_LONGITUDE,
            latitude = cachedLocation?.latitude ?: LocationManager.DEFAULT_LATITUDE,
            isCurrentLocation = true
        )
        val updatedCities = cities.toMutableList().apply {
            add(0, currentLocationCity)
        }
        cityRepository.saveCities(updatedCities)
        return updatedCities
    }

    suspend fun saveCities(cities: List<City>) {
        cityRepository.saveCities(cities)
    }

    /**
     * 更新定位城市名称。从 Room 读取最新列表，避免覆盖其他并发修改。
     */
    suspend fun updateCurrentLocationCityName(name: String): List<City> {
        val cities = cityRepository.getCities()
        val index = cities.indexOfFirst { it.isCurrentLocation }
        if (index < 0) return cities
        val updatedCities = cities.toMutableList().apply {
            this[index] = this[index].copy(name = name)
        }
        cityRepository.saveCities(updatedCities)
        return updatedCities
    }

    /**
     * 更新定位城市坐标。从 Room 读取最新列表，避免覆盖其他并发修改。
     */
    suspend fun updateCurrentLocationCityCoords(lon: Double, lat: Double): List<City> {
        val cities = cityRepository.getCities()
        val index = cities.indexOfFirst { it.isCurrentLocation }
        if (index < 0) return cities
        val updatedCities = cities.toMutableList().apply {
            this[index] = this[index].copy(longitude = lon, latitude = lat)
        }
        cityRepository.saveCities(updatedCities)
        return updatedCities
    }

    suspend fun migrateFromSharedPreferences(json: String) {
        cityRepository.migrateFromSharedPreferences(json)
    }
}
