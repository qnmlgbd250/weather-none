package com.skypulse.weather.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.CityDataStore
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.data.PermissionDataStore
import com.skypulse.weather.data.WeatherDataStore
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject

// ============ UI State ============

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Success(
        val weather: WeatherResponse,
        val locationName: String = "定位中..."
    ) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

enum class RefreshPhase {
    Idle, Refreshing, Success
}

enum class AppScreen {
    CityList, CityDetail, Settings, AlertDetail
}

data class CityWeatherData(
    val weather: WeatherResponse? = null,
    val error: String? = null
)

sealed class UpdateCheckResult {
    data object Checking : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val version: String, val url: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

// ============ ViewModel ============

@HiltViewModel
class WeatherViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: WeatherRepository,
    private val cityDataStore: CityDataStore,
    private val weatherDataStore: WeatherDataStore,
    private val weatherCache: WeatherCache,
    private val cityManager: CityManager,
    private val locationManager: LocationManager,
    private val permissionDataStore: PermissionDataStore,
) : ViewModel() {

    companion object {
        private const val TAG = "WeatherVM"
        private const val REFRESH_DEDUP_MS = 60_000L
    }

    // --- Screen navigation ---
    private val _currentScreen = MutableStateFlow(AppScreen.CityDetail)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // --- Permission onboarding ---
    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    init {
        viewModelScope.launch {
            val completed = permissionDataStore.isOnboardingCompleted()
            _showOnboarding.value = !completed
        }
    }

    // --- Saved cities ---
    private val _savedCities = MutableStateFlow<List<City>>(emptyList())
    val savedCities: StateFlow<List<City>> = _savedCities.asStateFlow()

    // --- Weather data for each city ---
    private val _cityWeatherMap = MutableStateFlow<Map<String, CityWeatherData>>(emptyMap())
    val cityWeatherMap: StateFlow<Map<String, CityWeatherData>> = _cityWeatherMap.asStateFlow()

    // --- Selected city for detail view ---
    private val _selectedCityId = MutableStateFlow<String?>(null)
    val selectedCityId: StateFlow<String?> = _selectedCityId.asStateFlow()

    // --- Alert detail selection ---
    private val _selectedAlertIndex = MutableStateFlow(0)
    val selectedAlertIndex: StateFlow<Int> = _selectedAlertIndex.asStateFlow()

    private val _swipeDirection = MutableStateFlow(1)
    val swipeDirection: StateFlow<Int> = _swipeDirection.asStateFlow()

    // --- GPS-based state (detail view) ---
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshPhase = MutableStateFlow(RefreshPhase.Idle)
    val refreshPhase: StateFlow<RefreshPhase> = _refreshPhase.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _lastFetchTime = MutableStateFlow(0L)
    private val lastFetchTimesByCityId = mutableMapOf<String, Long>()
    private val refreshingCityIds = mutableSetOf<String>()

    // --- Update check ---
    private val _updateState = MutableStateFlow<UpdateCheckResult?>(null)
    val updateState: StateFlow<UpdateCheckResult?> = _updateState.asStateFlow()

    private val weatherMapMutex = Mutex()
    private val apiSemaphore = Semaphore(3)
    private var citiesLoadJob: Job? = null

    init {
        citiesLoadJob = viewModelScope.launch {
            val cities = cityDataStore.getCities()
            _savedCities.value = cities
            syncCitiesToWidget()
             // Load cached weather data (including expired) so city list shows immediately
            val cachedMap = mutableMapOf<String, CityWeatherData>()
            for (city in cities) {
                 val cached = weatherDataStore.loadCached(city.id)
                if (cached != null) {
                    cachedMap[city.id] = CityWeatherData(weather = cached)
                }
            }
            if (cachedMap.isNotEmpty()) {
                _cityWeatherMap.value = cachedMap
                for ((cityId, data) in cachedMap) {
                    data.weather?.let { weatherCache.save(cityId, it) }
                }
            }
            // If onboarding completed but no cities, go to city list to add one
            if (cities.isEmpty() && permissionDataStore.isOnboardingCompleted()) {
                _currentScreen.value = AppScreen.CityList
            }

            // Initialize detail screen with cached data
            val initialCity = cities.find { it.isCurrentLocation } ?: cities.firstOrNull()
            if (initialCity != null) {
                _selectedCityId.value = initialCity.id
                val cachedWeather = weatherDataStore.loadCached(initialCity.id)
                if (cachedWeather != null) {
                    _uiState.value = WeatherUiState.Success(
                        weather = cachedWeather,
                        locationName = initialCity.name
                    )
                }
                // Refresh initial city's data in background
                refreshCityAfterSwitch(initialCity)
            }
        }
    }

    // ============ Navigation ============

    fun navigateToCityList() {
        _currentScreen.value = AppScreen.CityList
        val existingData = _cityWeatherMap.value
        val citiesToLoad = _savedCities.value.filter { city ->
            val data = existingData[city.id]
            data == null || data.weather == null
        }
        if (citiesToLoad.isNotEmpty()) {
            viewModelScope.launch {
                citiesToLoad.map { city ->
                    async {
                        apiSemaphore.withPermit {
                            loadWeatherForCity(city)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun navigateToCityDetail(cityId: String) {
        _selectedCityId.value = cityId
        _currentScreen.value = AppScreen.CityDetail
        // Load selected city's weather into uiState
        val city = _savedCities.value.find { it.id == cityId }
        if (city != null) {
            val cached = _cityWeatherMap.value[cityId]
            if (cached?.weather != null) {
                _uiState.value = WeatherUiState.Success(
                    weather = cached.weather,
                    locationName = city.name
                )
            } else {
                // Fetch if not cached
                fetchWeatherForCity(city)
            }
        }
    }

    fun switchToNextCity() {
        _swipeDirection.value = 1
        val cities = _savedCities.value
        if (cities.size <= 1) return
        val currentId = _selectedCityId.value
        val currentIndex = if (currentId == null) {
            cities.indexOfFirst { it.isCurrentLocation }
        } else {
            cities.indexOfFirst { it.id == currentId }
        }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % cities.size
        switchToCity(cities[nextIndex])
    }

    fun switchToPreviousCity() {
        _swipeDirection.value = -1
        val cities = _savedCities.value
        if (cities.size <= 1) return
        val currentId = _selectedCityId.value
        val currentIndex = if (currentId == null) {
            cities.indexOfFirst { it.isCurrentLocation }
        } else {
            cities.indexOfFirst { it.id == currentId }
        }
        val prevIndex = if (currentIndex < 0) 0 else {
            (currentIndex - 1 + cities.size) % cities.size
        }
        switchToCity(cities[prevIndex])
    }

    private fun switchToCity(city: City) {
        _selectedCityId.value = city.id
        val cached = _cityWeatherMap.value[city.id]
        if (cached?.weather != null) {
            // Instant switch — no Loading state, no flash
            _uiState.value = WeatherUiState.Success(
                weather = cached.weather,
                locationName = city.name
            )
            refreshCityAfterSwitch(city)
        } else {
            // No cache — keep old data visible, fetch silently
            fetchWeatherForCitySilent(city)
        }
    }

    fun completeOnboarding() {
        _showOnboarding.value = false
        viewModelScope.launch {
            permissionDataStore.setOnboardingCompleted()
        }
    }

    fun navigateToSettings() {
        _currentScreen.value = AppScreen.Settings
    }

    fun navigateToAlertDetail(alertIndex: Int = 0) {
        _selectedAlertIndex.value = alertIndex
        _currentScreen.value = AppScreen.AlertDetail
    }

    fun navigateBack() {
        when (_currentScreen.value) {
            AppScreen.Settings, AppScreen.AlertDetail -> {
                _currentScreen.value = AppScreen.CityDetail
            }
            AppScreen.CityList -> {
                _currentScreen.value = AppScreen.CityDetail
                val cityId = _selectedCityId.value ?: _savedCities.value.firstOrNull()?.id
                if (cityId != null) {
                    _selectedCityId.value = cityId
                    val city = _savedCities.value.find { it.id == cityId }
                    val cached = _cityWeatherMap.value[cityId]
                    if (city != null && cached?.weather != null) {
                        _uiState.value = WeatherUiState.Success(
                            weather = cached.weather,
                            locationName = city.name
                        )
                    } else if (city != null) {
                        fetchWeatherForCity(city)
                    }
                } else {
                    viewModelScope.launch {
                        citiesLoadJob?.join()
                        val cachedLocation = locationManager.getCachedLocation()
                        val defaultCity = City(
                            id = "current_location",
                            name = cachedLocation?.name ?: "定位中...",
                            longitude = cachedLocation?.longitude ?: LocationManager.DEFAULT_LONGITUDE,
                            latitude = cachedLocation?.latitude ?: LocationManager.DEFAULT_LATITUDE,
                            isCurrentLocation = true
                        )
                        cityDataStore.saveCities(listOf(defaultCity))
                        _savedCities.value = listOf(defaultCity)
                        syncCitiesToWidget()
                        _selectedCityId.value = defaultCity.id
                        if (cachedLocation != null) {
                            fetchWeatherForLocation(cachedLocation.longitude, cachedLocation.latitude, cachedLocation.name)
                        } else {
                            fetchDefaultWeather()
                        }
                    }
                }
            }
            else -> {}
        }
    }

    // ============ City Management ============

    fun addCity(name: String, longitude: Double, latitude: Double) {
        viewModelScope.launch {
            val city = City(
                id = UUID.randomUUID().toString(),
                name = name,
                longitude = longitude,
                latitude = latitude,
                isCurrentLocation = false
            )
            cityDataStore.addCity(city)
            _savedCities.value = cityDataStore.getCities()
            syncCitiesToWidget()
            loadWeatherForCity(city)
            if (_selectedCityId.value == null) {
                _selectedCityId.value = city.id
            }
        }
    }

    fun removeCity(cityId: String) {
        viewModelScope.launch {
            cityDataStore.removeCity(cityId)
            val updatedCities = cityDataStore.getCities()
            _savedCities.value = updatedCities
            syncCitiesToWidget()
            val currentMap = _cityWeatherMap.value.toMutableMap()
            currentMap.remove(cityId)
            _cityWeatherMap.value = currentMap
            weatherDataStore.remove(cityId)
            // 如果删除的是当前选中的城市，切换到第一个城市
            if (_selectedCityId.value == cityId) {
                val nextCity = updatedCities.firstOrNull()
                _selectedCityId.value = nextCity?.id
                if (nextCity != null) {
                    val cached = _cityWeatherMap.value[nextCity.id]
                    if (cached?.weather != null) {
                        _uiState.value = WeatherUiState.Success(
                            weather = cached.weather,
                            locationName = nextCity.name
                        )
                    } else {
                        fetchWeatherForCity(nextCity)
                    }
                }
            }
        }
    }

    fun ensureCurrentLocationCity() {
        viewModelScope.launch {
            citiesLoadJob?.join()
            val cities = _savedCities.value.toMutableList()
            val hasCurrentLocation = cities.any { it.isCurrentLocation }
            if (!hasCurrentLocation) {
                val cachedLocation = locationManager.getCachedLocation()
                val currentLocationCity = City(
                    id = "current_location",
                    name = cachedLocation?.name ?: "定位中...",
                    longitude = cachedLocation?.longitude ?: LocationManager.DEFAULT_LONGITUDE,
                    latitude = cachedLocation?.latitude ?: LocationManager.DEFAULT_LATITUDE,
                    isCurrentLocation = true
                )
                cities.add(0, currentLocationCity)
                cityDataStore.saveCities(cities)
                _savedCities.value = cities
            syncCitiesToWidget()
            }
        }
    }

    fun updateCurrentLocationCityName(name: String) {
        viewModelScope.launch {
            val cities = _savedCities.value.toMutableList()
            val index = cities.indexOfFirst { it.isCurrentLocation }
            if (index >= 0) {
                cities[index] = cities[index].copy(name = name)
                cityDataStore.saveCities(cities)
                _savedCities.value = cities
            syncCitiesToWidget()
            }
        }
    }

    fun updateCurrentLocationCityCoords(lon: Double, lat: Double) {
        viewModelScope.launch {
            val cities = _savedCities.value.toMutableList()
            val index = cities.indexOfFirst { it.isCurrentLocation }
            if (index >= 0) {
                cities[index] = cities[index].copy(longitude = lon, latitude = lat)
                cityDataStore.saveCities(cities)
                _savedCities.value = cities
            syncCitiesToWidget()
            }
        }
    }

    // ============ Multi-city Weather Loading ============

    private suspend fun loadWeatherForCity(city: City) {
        var lastException: Throwable? = null
        var result: Result<WeatherResponse>? = null
        repeat(3) { attempt ->
            if (attempt > 0) delay(1000L * attempt)
            val r = repository.getWeather(city.longitude, city.latitude, includeYesterday = true)
            r.fold(
                onSuccess = { _ ->
                    result = r
                    return@repeat
                },
                onFailure = { e ->
                    lastException = e
                    if (e is HttpException && e.code() == 429) return@repeat
                }
            )
        }
        weatherMapMutex.withLock {
            val updatedMap = _cityWeatherMap.value.toMutableMap()
            (result ?: Result.failure(lastException ?: Exception("未知错误"))).fold(
                onSuccess = { response ->
                    markFetched(city.id)
                    updatedMap[city.id] = CityWeatherData(weather = response)
                    viewModelScope.launch(Dispatchers.IO) {
                        weatherDataStore.save(city.id, response)
                        weatherCache.save(city.id, response)
                    }
                },
                onFailure = { e ->
                    updatedMap[city.id] = CityWeatherData(error = mapError(e))
                }
            )
            _cityWeatherMap.value = updatedMap
        }
    }

    private fun fetchWeatherForCitySilent(city: City) {
        viewModelScope.launch {
            val result = fetchWithRetry(city.longitude, city.latitude)
            result.fold(
                onSuccess = { response ->
                    markFetched(city.id)
                    // Only update if still viewing this city
                    if (_selectedCityId.value == city.id) {
                        _uiState.value = WeatherUiState.Success(
                            weather = response,
                            locationName = city.name
                        )
                    }
                    _cityWeatherMap.value = _cityWeatherMap.value.toMutableMap().apply {
                        put(city.id, CityWeatherData(weather = response))
                    }
                    weatherDataStore.save(city.id, response)
                    weatherCache.save(city.id, response)
                },
                onFailure = { _ -> }
            )
        }
    }

    private fun isRecentlyFetched(city: City?): Boolean {
        val lastFetch = lastFetchTimeFor(city)
        return lastFetch > 0 && System.currentTimeMillis() - lastFetch < REFRESH_DEDUP_MS
    }

    private fun refreshCityAfterSwitch(city: City) {
        if (isRecentlyFetched(city) || refreshingCityIds.contains(city.id)) return
        viewModelScope.launch {
            refreshingCityIds.add(city.id)
            try {
                if (city.isCurrentLocation) {
                    doFetchWeather(silent = true)
                } else {
                    refreshSavedCity(city, silent = true)
                }
            } finally {
                refreshingCityIds.remove(city.id)
            }
        }
    }

    private fun fetchWeatherForCity(city: City) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            val result = fetchWithRetry(city.longitude, city.latitude)
            result.fold(
                onSuccess = { response ->
                    markFetched(city.id)
                    _uiState.value = WeatherUiState.Success(
                        weather = response,
                        locationName = city.name
                    )
                    weatherDataStore.save(city.id, response)
                    weatherCache.save(city.id, response)
                },
                onFailure = { e ->
                    _uiState.value = WeatherUiState.Error(mapError(e))
                }
            )
        }
    }

    private fun syncCitiesToWidget() {
        try {
            cityManager.saveCities(_savedCities.value)
        } catch (_: Exception) {}
    }
    // ============ GPS-based Weather ============

    @Suppress("MissingPermission")
    fun relocateAndRefresh() {
        viewModelScope.launch {
            val currentCity = _savedCities.value.find { it.isCurrentLocation }
            if (currentCity != null && isRecentlyFetched(currentCity)) return@launch
            _isLocating.value = true
            try {
                val amapLocation = locationManager.requestAmapLocation()
                if (amapLocation != null) {
                    val lon = amapLocation.longitude
                    val lat = amapLocation.latitude
                    val locationName = locationManager.resolveLocationName(amapLocation)
                    locationManager.saveCachedLocation(locationName, lon, lat)
                    updateCurrentLocationCityCoords(lon, lat)
                    updateCurrentLocationCityName(locationName)
                    fetchWeatherForLocation(lon, lat, locationName)
                } else {
                    val diagnostic = getLocationDiagnostic()
                    _uiState.value = WeatherUiState.Error(
                        diagnostic ?: "无法获取定位，请到室外空旷处重试。如持续失败，请检查手机\"位置信息\"设置是否开启"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("定位失败，请稍后重试")
            } finally {
                _isLocating.value = false
            }
        }
    }

    fun fetchWeather() {
        viewModelScope.launch {
            val refreshCity = selectedCityForRefresh()
            if (_uiState.value is WeatherUiState.Success && isRecentlyFetched(refreshCity)) return@launch
            if (_uiState.value !is WeatherUiState.Success) {
                _uiState.value = WeatherUiState.Loading
            }
            doFetchWeather()
        }
    }

    fun fetchDefaultWeather() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            fetchWeatherForLocation(
                lon = LocationManager.DEFAULT_LONGITUDE,
                lat = LocationManager.DEFAULT_LATITUDE,
                locationName = "北京市"
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshPhase.value = RefreshPhase.Refreshing
            val startTime = System.currentTimeMillis()
            val refreshCity = selectedCityForRefresh()
            val refreshed = if (isRecentlyFetched(refreshCity)) {
                false
            } else {
                refreshSelectedWeather(refreshCity)
            }
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1500) delay(1500 - elapsed)
            _isRefreshing.value = false
            _refreshPhase.value = RefreshPhase.Success
            delay(2000)
            _refreshPhase.value = RefreshPhase.Idle
        }
    }

    fun silentRefresh() {
        viewModelScope.launch {
            val refreshCity = selectedCityForRefresh()
            if (isRecentlyFetched(refreshCity)) return@launch
            val refreshed = refreshSelectedWeather(refreshCity, silent = true)
            if (refreshed) {
                _refreshPhase.value = RefreshPhase.Success
                delay(2000)
                _refreshPhase.value = RefreshPhase.Idle
            }
        }
    }

    private suspend fun refreshSelectedWeather(
        city: City?,
        silent: Boolean = false
    ): Boolean {
        return if (city != null && !city.isCurrentLocation) {
            refreshSavedCity(city, silent)
        } else {
            doFetchWeather(silent)
        }
    }

    private suspend fun refreshSavedCity(
        city: City,
        silent: Boolean
    ): Boolean {
        val result = fetchWithRetry(city.longitude, city.latitude)
        return result.fold(
            onSuccess = { response ->
                markFetched(city.id)
                _cityWeatherMap.value = _cityWeatherMap.value.toMutableMap().apply {
                    put(city.id, CityWeatherData(weather = response))
                }
                weatherDataStore.save(city.id, response)
                weatherCache.save(city.id, response)
                if (_selectedCityId.value == city.id) {
                    _uiState.value = WeatherUiState.Success(
                        weather = response,
                        locationName = city.name
                    )
                }
                true
            },
            onFailure = { e ->
                _cityWeatherMap.value = _cityWeatherMap.value.toMutableMap().apply {
                    put(city.id, CityWeatherData(error = mapError(e)))
                }
                if (!silent || _uiState.value !is WeatherUiState.Success) {
                    _uiState.value = WeatherUiState.Error(mapError(e))
                }
                false
            }
        )
    }

    private suspend fun doFetchWeather(silent: Boolean = false): Boolean {
        // 无定位权限时，使用用户添加的城市（或北京兜底）更新天气，不弹错误
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            val fallback = _savedCities.value.firstOrNull() ?: run {
                val cached = locationManager.getCachedLocation()
                City("current_location", cached?.name ?: "北京市", cached?.longitude ?: LocationManager.DEFAULT_LONGITUDE, cached?.latitude ?: LocationManager.DEFAULT_LATITUDE, true)
            }
            return fetchWeatherForLocation(fallback.longitude, fallback.latitude, fallback.name, silent)
        }

        return try {
            val amapLocation = locationManager.requestAmapLocation()

            if (amapLocation != null) {
                val lon = amapLocation.longitude
                val lat = amapLocation.latitude
                val locationName = locationManager.resolveLocationName(amapLocation)
                locationManager.saveCachedLocation(locationName, lon, lat)
                updateCurrentLocationCityCoords(lon, lat)
                updateCurrentLocationCityName(locationName)
                fetchWeatherForLocation(lon, lat, locationName, silent)
            } else {
                if (silent && _uiState.value is WeatherUiState.Success) {
                    Log.w(TAG, "Silent refresh location failed, keeping cached data")
                    return false
                }
                val diagnostic = getLocationDiagnostic()
                _uiState.value = WeatherUiState.Error(
                    diagnostic ?: "无法获取定位，请到室外或靠近窗户重试。如持续失败可使用\"刷新\"按钮重试"
                )
                false
            }
        } catch (e: Exception) {
            if (!silent || _uiState.value !is WeatherUiState.Success) {
                _uiState.value = WeatherUiState.Error("获取天气数据失败，请稍后重试")
            }
            false
        }
    }

    private suspend fun fetchWeatherForLocation(
        lon: Double,
        lat: Double,
        locationName: String,
        silent: Boolean = false
    ): Boolean {
        val result = fetchWithRetry(lon, lat)
        return result.fold(
            onSuccess = { response ->
                val currentCity = _savedCities.value.find { it.isCurrentLocation }
                markFetched(currentCity?.id)
                if (currentCity != null) {
                    weatherDataStore.save(currentCity.id, response)
                    weatherCache.save(currentCity.id, response)
                    com.skypulse.weather.widget.WeatherWidgetProvider.refresh(appContext)
                    _cityWeatherMap.value = _cityWeatherMap.value.toMutableMap().apply {
                        put(currentCity.id, CityWeatherData(weather = response))
                    }
                }
                val viewingCityId = _selectedCityId.value
                val isViewingGpsCity = viewingCityId == null || viewingCityId == currentCity?.id
                if (isViewingGpsCity) {
                    _uiState.value = WeatherUiState.Success(
                        weather = response,
                        locationName = locationName
                    )
                }
                true
            },
            onFailure = { e ->
                if (!silent || _uiState.value !is WeatherUiState.Success) {
                    _uiState.value = WeatherUiState.Error(mapError(e))
                }
                false
            }
        )
    }

    private fun selectedCityForRefresh(): City? {
        val selectedId = _selectedCityId.value
        return if (selectedId == null) {
            _savedCities.value.find { it.isCurrentLocation }
        } else {
            _savedCities.value.find { it.id == selectedId }
        }
    }

    private fun lastFetchTimeFor(city: City?): Long {
        val cityId = city?.id ?: return _lastFetchTime.value
        return lastFetchTimesByCityId[cityId] ?: 0L
    }

    private fun markFetched(cityId: String?) {
        val now = System.currentTimeMillis()
        _lastFetchTime.value = now
        if (cityId != null) {
            lastFetchTimesByCityId[cityId] = now
        }
    }

    private suspend fun fetchWithRetry(
        lon: Double,
        lat: Double,
        maxRetries: Int = 2
    ): Result<WeatherResponse> {
        var lastException: Exception? = null
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) delay(1000L * attempt)
            val result = repository.getWeather(lon, lat, includeYesterday = true)
            result.fold(
                onSuccess = { response ->
                    val hourly = response.result?.hourly
                    if (hourly == null || hourly.temperature.isNullOrEmpty()) {
                        lastException = Exception("empty_hourly")
                    } else {
                        return Result.success(response)
                    }
                },
                onFailure = { e ->
                    lastException = e as? Exception ?: Exception(e)
                    if (e is HttpException && e.code() == 429) {
                        return Result.failure(e)
                    }
                }
            )
        }
        return Result.failure(lastException ?: Exception("未知错误"))
    }

    private fun mapError(e: Throwable): String = when {
        e is HttpException && e.code() == 429 -> "天气服务繁忙，请稍后再试"
        e is HttpException -> "网络请求失败，请检查网络连接"
        e.message?.contains("timeout", true) == true -> "网络连接超时，请检查网络"
        e.message?.contains("resolve", true) == true -> "无法连接到服务器，请检查网络"
        else -> "获取天气数据失败，请稍后重试"
    }

    private fun getLocationDiagnostic(): String? {
        val hasFine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return "定位权限未授予，请在应用设置中允许定位权限"
        }
        val apiKey = BuildConfig.AMAP_API_KEY.trim()
        if (apiKey.isEmpty()) {
            return "未配置高德定位 Key"
        }
        return null
    }

    // ============ Update Check ============

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateCheckResult.Checking
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = java.net.URL("https://api.github.com/repos/qnmlgbd250/weather-none/releases/latest")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    val body = connection.inputStream.bufferedReader().readText()
                    connection.disconnect()
                    body
                }
                val json = org.json.JSONObject(result)
                val tagName = json.getString("tag_name").removePrefix("v")
                val current = BuildConfig.VERSION_NAME
                if (isNewerVersion(tagName, current)) {
                    val htmlUrl = json.getString("html_url")
                    _updateState.value = UpdateCheckResult.UpdateAvailable(tagName, htmlUrl)
                } else {
                    _updateState.value = UpdateCheckResult.UpToDate
                }
            } catch (e: Exception) {
                _updateState.value = UpdateCheckResult.Error("检查更新失败，请稍后重试")
            }
        }
    }

    fun clearUpdateState() {
        _updateState.value = null
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
