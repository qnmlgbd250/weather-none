const fs = require('fs');
const path = require('path');
const filePath = path.join('C:', 'Users', 'phil', 'weather-none', 'app', 'src', 'main', 'java', 'com', 'skypulse', 'weather', 'viewmodel', 'WeatherViewModel.kt');
let content = fs.readFileSync(filePath, 'utf-8').replace(/\r\n/g, '\n');

const old = `            // Show cached data immediately, then refresh in background
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
            }`;

const fixed = `            // Always show cached data immediately when switching to this city
            val cached = weatherCache.load(city.id)
            if (cached != null) {
                _uiState.value = WeatherUiState.Success(
                    weather = cached,
                    locationName = city.name
                )
            }
            // Silent refresh in background
            viewModelScope.launch {
                doFetchWeather(silent = true)
            }`;

if (content.includes(old)) {
    content = content.replace(old, fixed);
    fs.writeFileSync(filePath, content.replace(/\n/g, '\r\n'), 'utf-8');
    console.log('Fixed: always replace UI with cached data');
} else {
    console.log('ERROR: pattern not found');
}
