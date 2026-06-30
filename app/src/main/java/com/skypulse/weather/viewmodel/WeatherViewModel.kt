package com.skypulse.weather.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.data.PermissionDataStore
import com.skypulse.weather.domain.CheckUpdateUseCase
import com.skypulse.weather.domain.ManageCityUseCase
import com.skypulse.weather.domain.RefreshWeatherUseCase
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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
    private val refreshWeatherUseCase: RefreshWeatherUseCase,
    private val manageCityUseCase: ManageCityUseCase,
    private val locationManager: LocationManager,
    private val permissionDataStore: PermissionDataStore,
    private val checkUpdateUseCase: CheckUpdateUseCase,
) : ViewModel() {

    companion object {
        private const val TAG = "WeatherVM"
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

    // --- Update check ---
    private val _updateState = MutableStateFlow<UpdateCheckResult?>(null)
    val updateState: StateFlow<UpdateCheckResult?> = _updateState.asStateFlow()

    private val refreshingCityIds = mutableSetOf<String>()
    private val weatherMapMutex = Mutex()
    private val refreshingCityIdsMutex = Mutex()
    private val apiSemaphore = Semaphore(3)
    private var citiesLoadJob: Job? = null
    private var weatherObservationJob: Job? = null

    init {
        citiesLoadJob = viewModelScope.launch {
            // 首次升级时从 SharedPreferences 迁移城市数据到 Room
            var cities = manageCityUseCase.getCities()
            if (cities.isEmpty() && permissionDataStore.isOnboardingCompleted()) {
                try {
                    val prefs = appContext.getSharedPreferences("sky_pulse_cities", android.content.Context.MODE_PRIVATE)
                    val json = prefs.getString("cities_json", null)
                    if (!json.isNullOrEmpty()) {
                        manageCityUseCase.migrateFromSharedPreferences(json)
                        cities = manageCityUseCase.getCities()
                    }
                } catch (_: Exception) {}
            }
            _savedCities.value = cities

            // Load cached weather from Room (SSOT) so city list shows immediately
            val cachedMap = mutableMapOf<String, CityWeatherData>()
            for (city in cities) {
                val cached = repository.getWeatherFromCache(city.id)
                if (cached != null) {
                    cachedMap[city.id] = CityWeatherData(weather = cached)
                }
            }
            if (cachedMap.isNotEmpty()) {
                _cityWeatherMap.value = cachedMap
            }

            // If onboarding completed but no cities, go to city list to add one
            if (cities.isEmpty() && permissionDataStore.isOnboardingCompleted()) {
                _currentScreen.value = AppScreen.CityList
            }

            // Initialize detail screen with cached data
            val initialCity = cities.find { it.isCurrentLocation } ?: cities.firstOrNull()
            if (initialCity != null) {
                _selectedCityId.value = initialCity.id
                val cachedWeather = repository.getWeatherFromCache(initialCity.id)
                if (cachedWeather != null) {
                    _uiState.value = WeatherUiState.Success(
                        weather = cachedWeather,
                        locationName = initialCity.name
                    )
                }
                // Refresh initial city's data in background
                refreshCityAfterSwitch(initialCity)
            }

            // Start observing Room for real-time updates
            startWeatherObservation()
        }
    }

    /**
     * 观察 Room 中所有城市的天气变化，自动更新 UI 状态。
     * 这是 SSOT 的核心：所有数据变更通过 Room Flow 传播。
     */
    private fun startWeatherObservation() {
        weatherObservationJob?.cancel()
        weatherObservationJob = viewModelScope.launch {
            repository.observeAllWeather().collect { entities ->
                weatherMapMutex.withLock {
                    val updatedMap = _cityWeatherMap.value.toMutableMap()
                    var changed = false
                    for (entity in entities) {
                        val existing = updatedMap[entity.cityId]
                        // Only update if we don't have in-memory data (in-memory takes priority during active fetch)
                        if (existing == null || existing.weather == null) {
                            val weather = repository.getWeatherFromCache(entity.cityId)
                            if (weather != null) {
                                updatedMap[entity.cityId] = CityWeatherData(weather = weather)
                                changed = true
                            }
                        }
                    }
                    if (changed) {
                        _cityWeatherMap.value = updatedMap
                    }
                }
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
        val city = _savedCities.value.find { it.id == cityId }
        if (city != null) {
            val cached = _cityWeatherMap.value[cityId]
            if (cached?.weather != null) {
                _uiState.value = WeatherUiState.Success(
                    weather = cached.weather,
                    locationName = city.name
                )
            } else {
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
            _uiState.value = WeatherUiState.Success(
                weather = cached.weather,
                locationName = city.name
            )
            refreshCityAfterSwitch(city)
        } else {
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
                        val updatedCities = manageCityUseCase.ensureCurrentLocationCity(_savedCities.value)
                        _savedCities.value = updatedCities
                        val defaultCity = updatedCities.firstOrNull { it.isCurrentLocation } ?: updatedCities.firstOrNull()
                        if (defaultCity != null) {
                            _selectedCityId.value = defaultCity.id
                            val cachedLocation = locationManager.getCachedLocation()
                            val result = if (cachedLocation != null) {
                                refreshWeatherUseCase.refreshCity(
                                    defaultCity.id,
                                    cachedLocation.longitude,
                                    cachedLocation.latitude
                                )
                            } else {
                                refreshWeatherUseCase.refreshDefault()
                            }
                            handleSyncResult(result, defaultCity)
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
            val (city, updatedCities) = manageCityUseCase.addCity(name, longitude, latitude)
            _savedCities.value = updatedCities
            loadWeatherForCity(city)
            if (_selectedCityId.value == null) {
                _selectedCityId.value = city.id
            }
        }
    }

    fun removeCity(cityId: String) {
        viewModelScope.launch {
            val updatedCities = manageCityUseCase.removeCity(cityId)
            _savedCities.value = updatedCities
            removeWeatherMap(cityId)
            repository.deleteWeatherCache(cityId)
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
            val updatedCities = manageCityUseCase.ensureCurrentLocationCity(_savedCities.value)
            _savedCities.value = updatedCities
        }
    }

    fun updateCurrentLocationCityName(name: String) {
        viewModelScope.launch {
            val updatedCities = manageCityUseCase.updateCurrentLocationCityName(_savedCities.value, name)
            _savedCities.value = updatedCities
        }
    }

    fun updateCurrentLocationCityCoords(lon: Double, lat: Double) {
        viewModelScope.launch {
            val updatedCities = manageCityUseCase.updateCurrentLocationCityCoords(_savedCities.value, lon, lat)
            _savedCities.value = updatedCities
        }
    }

    // ============ Multi-city Weather Loading ============

    private suspend fun loadWeatherForCity(city: City) {
        if (refreshWeatherUseCase.isRecentlyFetched(city.id)) return
        val result = refreshWeatherUseCase.refreshCity(city.id, city.longitude, city.latitude)
        handleSyncResultForMap(city, result)
    }

    private fun fetchWeatherForCitySilent(city: City) {
        viewModelScope.launch {
            if (refreshWeatherUseCase.isRecentlyFetched(city.id)) return@launch
            val result = refreshWeatherUseCase.refreshCity(city.id, city.longitude, city.latitude)
            val response = result.getOrNull()
            if (response != null) {
                if (_selectedCityId.value == city.id) {
                    _uiState.value = WeatherUiState.Success(
                        weather = response,
                        locationName = city.name
                    )
                }
                updateWeatherMap(city.id, CityWeatherData(weather = response))
            }
        }
    }

    private fun refreshCityAfterSwitch(city: City) {
        if (refreshWeatherUseCase.isRecentlyFetched(city.id)) return
        viewModelScope.launch {
            refreshingCityIdsMutex.withLock {
                if (refreshingCityIds.contains(city.id)) return@launch
                refreshingCityIds.add(city.id)
            }
            try {
                if (city.isCurrentLocation) {
                    refreshCurrentLocation(silent = true)
                } else {
                    val result = refreshWeatherUseCase.refreshCity(city.id, city.longitude, city.latitude)
                    handleSyncResult(result, city, silent = true)
                }
            } finally {
                refreshingCityIdsMutex.withLock {
                    refreshingCityIds.remove(city.id)
                }
            }
        }
    }

    private fun fetchWeatherForCity(city: City) {
        viewModelScope.launch {
            if (refreshWeatherUseCase.isRecentlyFetched(city.id)) return@launch
            val cached = _cityWeatherMap.value[city.id]?.weather
            if (cached == null) {
                _uiState.value = WeatherUiState.Loading
            }
            val result = refreshWeatherUseCase.refreshCity(city.id, city.longitude, city.latitude)
            result.fold(
                onSuccess = { response ->
                    _uiState.value = WeatherUiState.Success(
                        weather = response,
                        locationName = city.name
                    )
                },
                onFailure = { e ->
                    if (_uiState.value !is WeatherUiState.Success) {
                        _uiState.value = WeatherUiState.Error(e.message ?: "获取天气数据失败")
                    }
                }
            )
        }
    }

    /**
     * 线程安全地更新 cityWeatherMap。
     * 通过 Mutex 保证 read-modify-write 的原子性，防止并发更新丢失。
     */
    private suspend fun updateWeatherMap(cityId: String, data: CityWeatherData) {
        weatherMapMutex.withLock {
            _cityWeatherMap.value = _cityWeatherMap.value.toMutableMap().apply {
                put(cityId, data)
            }
        }
    }

    private suspend fun removeWeatherMap(cityId: String) {
        weatherMapMutex.withLock {
            val currentMap = _cityWeatherMap.value.toMutableMap()
            currentMap.remove(cityId)
            _cityWeatherMap.value = currentMap
        }
    }

    // ============ GPS-based Weather ============

    @Suppress("MissingPermission")
    fun relocateAndRefresh() {
        viewModelScope.launch {
            val currentCity = _savedCities.value.find { it.isCurrentLocation }
            if (currentCity != null && refreshWeatherUseCase.isRecentlyFetched(currentCity.id)) return@launch
            _isLocating.value = true
            try {
                val result = refreshWeatherUseCase.refreshWithLocation()
                val response = result.getOrNull()
                if (response != null) {
                    val city = _savedCities.value.find { it.isCurrentLocation }
                    if (city != null) {
                        updateWeatherMap(city.id, CityWeatherData(weather = response))
                    }
                    val viewingCityId = _selectedCityId.value
                    val isViewingGpsCity = viewingCityId == null || viewingCityId == city?.id
                    if (isViewingGpsCity) {
                        _uiState.value = WeatherUiState.Success(
                            weather = response,
                            locationName = locationManager.getCachedLocation()?.name ?: city?.name ?: "定位中..."
                        )
                    }
                } else {
                    val errorMsg = (result as? SyncResult.Error)?.message ?: "定位失败，请稍后重试"
                    _uiState.value = WeatherUiState.Error(errorMsg)
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
            if (_uiState.value is WeatherUiState.Success &&
                refreshWeatherUseCase.isRecentlyFetched(refreshCity?.id)) return@launch
            if (_uiState.value !is WeatherUiState.Success) {
                _uiState.value = WeatherUiState.Loading
            }
            refreshCurrentLocation()
        }
    }

    fun fetchDefaultWeather() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            val result = refreshWeatherUseCase.refreshDefault()
            handleSyncResultForCurrentLocation(result)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshPhase.value = RefreshPhase.Refreshing
            val startTime = System.currentTimeMillis()
            val refreshCity = selectedCityForRefresh()
            val refreshed = if (refreshWeatherUseCase.isRecentlyFetched(refreshCity?.id)) {
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

    fun onResume() {
        viewModelScope.launch {
            val gpsCity = _savedCities.value.find { it.isCurrentLocation }
            if (gpsCity != null) {
                _selectedCityId.value = gpsCity.id
                val cached = _cityWeatherMap.value[gpsCity.id]?.weather
                if (cached != null) {
                    _uiState.value = WeatherUiState.Success(
                        weather = cached,
                        locationName = gpsCity.name
                    )
                }
            }
            val refreshCity = selectedCityForRefresh()
            if (refreshWeatherUseCase.isRecentlyFetched(refreshCity?.id)) return@launch
            val refreshed = refreshSelectedWeather(refreshCity, silent = true)
            if (refreshed) {
                _refreshPhase.value = RefreshPhase.Success
                delay(2000)
                _refreshPhase.value = RefreshPhase.Idle
            }
        }
    }

    fun silentRefresh() {
        viewModelScope.launch {
            val refreshCity = selectedCityForRefresh()
            if (refreshWeatherUseCase.isRecentlyFetched(refreshCity?.id)) return@launch
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
            val result = refreshWeatherUseCase.refreshCity(city.id, city.longitude, city.latitude)
            handleSyncResult(result, city, silent)
            result is SyncResult.Success
        } else {
            refreshCurrentLocation(silent)
        }
    }

    /**
     * 刷新定位城市天气（通过 SyncManager 的完整定位+天气流程）。
     */
    private suspend fun refreshCurrentLocation(silent: Boolean = false): Boolean {
        val result = refreshWeatherUseCase.refreshWithLocation()
        val success = result is SyncResult.Success
        if (!success && !silent && _uiState.value !is WeatherUiState.Success) {
            val errorMsg = (result as? SyncResult.Error)?.message ?: "获取天气数据失败，请稍后重试"
            _uiState.value = WeatherUiState.Error(errorMsg)
        }
        // Update cityWeatherMap for current location city
        if (success) {
            val city = _savedCities.value.find { it.isCurrentLocation }
            if (city != null) {
                val weather = (result as SyncResult.Success).weather
                updateWeatherMap(city.id, CityWeatherData(weather = weather))
                val viewingCityId = _selectedCityId.value
                if (viewingCityId == null || viewingCityId == city.id) {
                    // 优先使用缓存中的定位名（GPS 成功后已更新），而不是 _savedCities 中的旧值
                    val locationName = locationManager.getCachedLocation()?.name
                        ?: city.name.takeIf { it != "当前定位" }
                        ?: "定位中..."
                    _uiState.value = WeatherUiState.Success(
                        weather = weather,
                        locationName = locationName
                    )
                }
            }
        }
        return success
    }

    // ============ Result Handlers ============

    private suspend fun handleSyncResult(
        result: SyncResult,
        city: City,
        silent: Boolean = false
    ) {
        val response = result.getOrNull()
        if (response != null) {
            updateWeatherMap(city.id, CityWeatherData(weather = response))
            if (_selectedCityId.value == city.id) {
                _uiState.value = WeatherUiState.Success(
                    weather = response,
                    locationName = city.name
                )
            }
        } else {
            val errorMsg = (result as? SyncResult.Error)?.message ?: "获取天气数据失败"
            updateWeatherMap(city.id, CityWeatherData(error = errorMsg))
            if (!silent && _uiState.value !is WeatherUiState.Success) {
                _uiState.value = WeatherUiState.Error(errorMsg)
            }
        }
    }

    private suspend fun handleSyncResultForMap(city: City, result: SyncResult) {
        val response = result.getOrNull()
        if (response != null) {
            updateWeatherMap(city.id, CityWeatherData(weather = response))
        } else {
            val errorMsg = (result as? SyncResult.Error)?.message ?: "获取天气数据失败"
            updateWeatherMap(city.id, CityWeatherData(error = errorMsg))
        }
    }

    private suspend fun handleSyncResultForCurrentLocation(result: SyncResult) {
        val response = result.getOrNull()
        if (response != null) {
            val city = _savedCities.value.find { it.isCurrentLocation }
            if (city != null) {
                updateWeatherMap(city.id, CityWeatherData(weather = response))
            }
            _uiState.value = WeatherUiState.Success(
                weather = response,
                locationName = locationManager.getCachedLocation()?.name ?: city?.name ?: "北京市"
            )
        } else {
            val errorMsg = (result as? SyncResult.Error)?.message ?: "获取天气数据失败，请稍后重试"
            _uiState.value = WeatherUiState.Error(errorMsg)
        }
    }

    private fun selectedCityForRefresh(): City? {
        val selectedId = _selectedCityId.value
        return if (selectedId == null) {
            _savedCities.value.find { it.isCurrentLocation }
        } else {
            _savedCities.value.find { it.id == selectedId }
        }
    }

    // ============ Update Check ============

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateCheckResult.Checking
            val result = checkUpdateUseCase.checkForUpdate()
            _updateState.value = when (result) {
                is CheckUpdateUseCase.Result.UpToDate -> UpdateCheckResult.UpToDate
                is CheckUpdateUseCase.Result.UpdateAvailable -> UpdateCheckResult.UpdateAvailable(result.version, result.url)
                is CheckUpdateUseCase.Result.Error -> UpdateCheckResult.Error(result.message)
            }
        }
    }

    fun clearUpdateState() {
        _updateState.value = null
    }
}
