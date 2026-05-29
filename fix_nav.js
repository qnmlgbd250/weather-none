const fs = require('fs');
const path = require('path');
const filePath = path.join('C:', 'Users', 'phil', 'weather-none', 'app', 'src', 'main', 'java', 'com', 'skypulse', 'weather', 'viewmodel', 'WeatherViewModel.kt');
let content = fs.readFileSync(filePath, 'utf-8').replace(/\r\n/g, '\n');

// Fix navigateToCityDetail: use cache for current location, then silent refresh
const old = `    fun navigateToCityDetail(cityId: String) {
        _selectedCityId.value = cityId
        _currentScreen.value = AppScreen.CityDetail

        val city = _savedCities.value.find { it.id == cityId } ?: return
        if (city.isCurrentLocation) {
            fetchWeather()
        } else {
            viewModelScope.launch { fetchWeatherForCity(city) }
        }
    }`;

const fixed = `    fun navigateToCityDetail(cityId: String) {
        _selectedCityId.value = cityId
        _currentScreen.value = AppScreen.CityDetail

        val city = _savedCities.value.find { it.id == cityId } ?: return
        if (city.isCurrentLocation) {
            // Show cached data immediately, then refresh in background
            val cached = weatherCache.load(city.id)
            if (cached != null && _uiState.value !is WeatherUiState.Success) {
                _uiState.value = WeatherUiState.Success(
                    weather = cached,
                    locationName = city.name
                )
            }
            // Silent refresh: no Loading state, updates UI when done
            viewModelScope.launch {
                val sinceLast = System.currentTimeMillis() - _lastFetchTime.value
                if (sinceLast >= API_COOLDOWN_MS) {
                    doFetchWeather(silent = true)
                }
            }
        } else {
            viewModelScope.launch { fetchWeatherForCity(city) }
        }
    }`;

if (content.includes(old)) {
    content = content.replace(old, fixed);
    fs.writeFileSync(filePath, content.replace(/\n/g, '\r\n'), 'utf-8');
    console.log('Fixed: navigateToCityDetail now uses cache + silent refresh');
} else {
    console.log('ERROR: pattern not found');
}
