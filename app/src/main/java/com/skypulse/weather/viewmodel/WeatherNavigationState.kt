package com.skypulse.weather.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WeatherNavigationState {
    private val _currentScreen = MutableStateFlow(AppScreen.CityDetail)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _selectedCityId = MutableStateFlow<String?>(null)
    val selectedCityId: StateFlow<String?> = _selectedCityId.asStateFlow()

    private val _selectedAlertIndex = MutableStateFlow(0)
    val selectedAlertIndex: StateFlow<Int> = _selectedAlertIndex.asStateFlow()

    private val _swipeDirection = MutableStateFlow(1)
    val swipeDirection: StateFlow<Int> = _swipeDirection.asStateFlow()

    fun showCityList() {
        _currentScreen.value = AppScreen.CityList
    }

    fun showCityDetail(cityId: String? = _selectedCityId.value) {
        if (cityId != null) {
            _selectedCityId.value = cityId
        }
        _currentScreen.value = AppScreen.CityDetail
    }

    fun showSettings() {
        _currentScreen.value = AppScreen.Settings
    }

    fun showAlertDetail(alertIndex: Int) {
        _selectedAlertIndex.value = alertIndex
        _currentScreen.value = AppScreen.AlertDetail
    }

    fun selectCity(cityId: String?) {
        _selectedCityId.value = cityId
    }

    fun markNextSwipe() {
        _swipeDirection.value = 1
    }

    fun markPreviousSwipe() {
        _swipeDirection.value = -1
    }
}
