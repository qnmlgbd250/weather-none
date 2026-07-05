package com.skypulse.weather.domain

import com.skypulse.weather.model.City

object CitySelectionPolicy {
    fun currentIndex(cities: List<City>, selectedCityId: String?): Int {
        if (cities.isEmpty()) return 0
        return rawIndex(cities, selectedCityId).coerceAtLeast(0)
    }

    fun defaultCity(cities: List<City>): City? {
        return cities.find { it.isCurrentLocation } ?: cities.firstOrNull()
    }

    fun selectedCity(cities: List<City>, selectedCityId: String?): City? {
        return if (selectedCityId == null) {
            cities.find { it.isCurrentLocation }
        } else {
            cities.find { it.id == selectedCityId }
        }
    }

    fun nextCity(cities: List<City>, selectedCityId: String?): City? {
        if (cities.size <= 1) return null
        val currentIndex = rawIndex(cities, selectedCityId)
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % cities.size
        return cities[nextIndex]
    }

    fun previousCity(cities: List<City>, selectedCityId: String?): City? {
        if (cities.size <= 1) return null
        val currentIndex = rawIndex(cities, selectedCityId)
        val previousIndex = if (currentIndex < 0) 0 else {
            (currentIndex - 1 + cities.size) % cities.size
        }
        return cities[previousIndex]
    }

    private fun rawIndex(cities: List<City>, selectedCityId: String?): Int {
        return if (selectedCityId == null) {
            cities.indexOfFirst { it.isCurrentLocation }
        } else {
            cities.indexOfFirst { it.id == selectedCityId }
        }
    }
}
