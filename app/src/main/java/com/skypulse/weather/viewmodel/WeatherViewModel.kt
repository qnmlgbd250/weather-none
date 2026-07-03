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
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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
    private val moshi: Moshi,
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

    private val _onboardingReady = MutableStateFlow(false)
    val onboardingReady: StateFlow<Boolean> = _onboardingReady.asStateFlow()

    // --- Saved cities ---
    private val _savedCities = MutableStateFlow<List<City>>(emptyList())
    val savedCities: StateFlow<List<City>> = _savedCities.asStateFlow()

    // --- Weather data for each city (SSOT derived from Room) ---
    val cityWeatherMap: StateFlow<Map<String, CityWeatherData>> = repository.observeAllWeather()
        .map { entities ->
            entities.associate { entity ->
                val weather = try {
                    moshi.adapter(WeatherResponse::class.java).fromJson(entity.responseJson)
                } catch (_: Exception) {
                    null
                }
                entity.cityId to CityWeatherData(weather = weather)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // --- Selected city for detail view ---
    private val _selectedCityId = MutableStateFlow<String?>(null)
    val selectedCityId: StateFlow<String?> = _selectedCityId.asStateFlow()

    // --- Alert detail selection ---
    private val _selectedAlertIndex = MutableStateFlow(0)
    val selectedAlertIndex: StateFlow<Int> = _selectedAlertIndex.asStateFlow()

    private val _swipeDirection = MutableStateFlow(1)
    val swipeDirection: StateFlow<Int> = _swipeDirection.asStateFlow()

    // --- Transient Error for detailed view offline handling ---
    private val transientError = MutableStateFlow<String?>(null)

    // --- GPS-based state (detail view, reactively driven from Room & selected city) ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WeatherUiState> = combine(
        _selectedCityId,
        _savedCities,
        transientError
    ) { selectedId, cities, errorMsg ->
        val cityId = selectedId ?: cities.find { it.isCurrentLocation }?.id ?: cities.firstOrNull()?.id
        cityId to errorMsg
    }.flatMapLatest { (cityId, errorMsg) ->
        if (cityId == null) {
            flowOf(WeatherUiState.Loading)
        } else {
            repository.observeWeather(cityId).map { entity ->
                if (entity != null) {
                    val weather = try {
                        moshi.adapter(WeatherResponse::class.java).fromJson(entity.responseJson)
                    } catch (_: Exception) {
                        null
                    }
                    if (weather != null) {
                        val city = _savedCities.value.find { it.id == cityId }
                        val locationName = if (city?.isCurrentLocation == true) {
                            locationManager.getCachedLocation()?.name
                                ?: city.name.takeIf { it != "当前定位" }
                                ?: "定位中..."
                        } else {
                            city?.name ?: "未知位置"
                        }
                        WeatherUiState.Success(weather, locationName)
                    } else {
                        WeatherUiState.Error("数据解析失败")
                    }
                } else if (errorMsg != null) {
                    WeatherUiState.Error(errorMsg)
                } else {
                    WeatherUiState.Loading
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WeatherUiState.Loading
    )

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
    private val refreshingCityIdsMutex = Mutex()
    private val apiSemaphore = Semaphore(3)
    private var citiesLoadJob: Job? = null

    init {
        // Observe saved onboarding status
        viewModelScope.launch {
            val completed = permissionDataStore.isOnboardingCompleted()
            _showOnboarding.value = !completed
            _onboardingReady.value = true
        }

        // Observe saved cities from use case reactively
        viewModelScope.launch {
            manageCityUseCase.observeCities().collect { cities ->
                _savedCities.value = cities
            }
        }

        citiesLoadJob = viewModelScope.launch {
            // 首次升级时从 SharedPreferences 迁移城市数据到 Room
            if (manageCityUseCase.getCities().isEmpty() && permissionDataStore.isOnboardingCompleted()) {
                try {
                    val prefs = appContext.getSharedPreferences("sky_pulse_cities", android.content.Context.MODE_PRIVATE)
                    val json = prefs.getString("cities_json", null)
                    if (!json.isNullOrEmpty()) {
                        manageCityUseCase.migrateFromSharedPreferences(json)
                    }
                } catch (_: Exception) {}
            }

            val cities = manageCityUseCase.getCities()
            // Initialize detail screen selected city
            val initialCity = cities.find { it.isCurrentLocation } ?: cities.firstOrNull()
            if (initialCity != null) {
                _selectedCityId.value = initialCity.id
                // 不在 init 中触发天气请求，由 LaunchedEffect/onResume 统一负责
            }
        }

        // Observe database changes to trigger Widget updates (no FileCache, just direct call to refresh)
        viewModelScope.launch {
            repository.observeAllWeather().collect {
                try {
                    val freshCities = manageCityUseCase.getCities()
                    val firstCity = freshCities.firstOrNull { it.isCurrentLocation } ?: freshCities.firstOrNull()
                    if (firstCity != null) {
                        val weather = repository.getWeatherFromCache(firstCity.id)
                        com.skypulse.weather.widget.WeatherWidgetProvider.refresh(appContext, weather, firstCity.name)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ============ Navigation ============

    fun navigateToCityList() {
        _currentScreen.value = AppScreen.CityList
        val existingData = cityWeatherMap.value
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
        transientError.value = null
        _currentScreen.value = AppScreen.CityDetail
        val city = _savedCities.value.find { it.id == cityId }
        if (city != null) {
            refreshCityAfterSwitch(city)
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
        transientError.value = null
        refreshCityAfterSwitch(city)
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
                    if (city != null) {
                        fetchWeatherForCity(city)
                    }
                } else {
                    viewModelScope.launch {
                        citiesLoadJob?.join()
                        val freshCities = manageCityUseCase.getCities()
                        _savedCities.value = freshCities
                        val updatedCities = manageCityUseCase.ensureCurrentLocationCity()
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
            repository.deleteWeatherCache(cityId)
            if (_selectedCityId.value == cityId) {
                val nextCity = updatedCities.firstOrNull()
                _selectedCityId.value = nextCity?.id
                transientError.value = null
                if (nextCity != null) {
                    fetchWeatherForCity(nextCity)
                }
            }
        }
    }

    /**
     * 确保存在定位城市（挂起版本，等待完成后再执行天气刷新）。
     */
    suspend fun ensureCurrentLocationCitySync() {
        citiesLoadJob?.join()
        val freshCities = manageCityUseCase.getCities()
        _savedCities.value = freshCities
        val updatedCities = manageCityUseCase.ensureCurrentLocationCity()
        _savedCities.value = updatedCities
        val currentCity = updatedCities.find { it.isCurrentLocation }
        if (currentCity != null) {
            if (_selectedCityId.value == null) {
                _selectedCityId.value = currentCity.id
            }
            if (currentCity.isUnresolvedLocationPlaceholder()) {
                repository.deleteWeatherCache(currentCity.id)
            }
        }
    }

    fun ensureCurrentLocationCity() {
        viewModelScope.launch {
            ensureCurrentLocationCitySync()
        }
    }

    fun updateCurrentLocationCityName(name: String) {
        viewModelScope.launch {
            val updatedCities = manageCityUseCase.updateCurrentLocationCityName(name)
            _savedCities.value = updatedCities
        }
    }

    fun updateCurrentLocationCityCoords(lon: Double, lat: Double) {
        viewModelScope.launch {
            val updatedCities = manageCityUseCase.updateCurrentLocationCityCoords(lon, lat)
            _savedCities.value = updatedCities
        }
    }

    // ============ Multi-city Weather Loading ============

    private suspend fun loadWeatherForCity(city: City) {
        if (shouldSkipRefresh(city)) return
        if (city.isCurrentLocation) {
            refreshWeatherUseCase.refreshWithLocation()
        } else {
            refreshWeatherUseCase.refreshCity(city.id, city.longitude, city.latitude)
        }
    }

    private fun refreshCityAfterSwitch(city: City) {
        viewModelScope.launch {
            if (shouldSkipRefresh(city)) return@launch
            refreshingCityIdsMutex.withLock {
                if (refreshingCityIds.contains(city.id)) return@launch
                refreshingCityIds.add(city.id)
            }
            try {
                _refreshPhase.value = RefreshPhase.Refreshing
                val startTime = System.currentTimeMillis()
                if (city.isCurrentLocation) {
                    refreshCurrentLocation(silent = true)
                } else {
                    val result = refreshWeatherUseCase.refreshCity(city.id, city.longitude, city.latitude)
                    handleSyncResult(result, city, silent = true)
                }
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 1000) delay(1000 - elapsed)
                _refreshPhase.value = RefreshPhase.Success
                delay(1000)
                _refreshPhase.value = RefreshPhase.Idle
            } finally {
                refreshingCityIdsMutex.withLock {
                    refreshingCityIds.remove(city.id)
                }
            }
        }
    }

    private fun fetchWeatherForCity(city: City) {
        viewModelScope.launch {
            if (shouldSkipRefresh(city)) return@launch
            if (repository.getWeatherFromCache(city.id) == null) {
                transientError.value = null
            }
            val result = if (city.isCurrentLocation) {
                refreshWeatherUseCase.refreshWithLocation()
            } else {
                refreshWeatherUseCase.refreshCity(city.id, city.longitude, city.latitude)
            }
            result.onFailure { e ->
                if (repository.getWeatherFromCache(city.id) == null) {
                    transientError.value = e.message ?: "获取天气数据失败"
                }
            }
        }
    }

    // ============ GPS-based Weather ============

    @Suppress("MissingPermission")
    fun relocateAndRefresh() {
        viewModelScope.launch {
            val currentCity = _savedCities.value.find { it.isCurrentLocation }
            _isLocating.value = true
            transientError.value = null
            try {
                val result = refreshWeatherUseCase.refreshWithLocation()
                val response = result.getOrNull()
                if (response != null) {
                    // Automatically updated via flow
                } else {
                    val errorMsg = (result as? SyncResult.Error)?.message ?: "定位失败，请稍后重试"
                    val cityId = currentCity?.id ?: "current_location"
                    if (repository.getWeatherFromCache(cityId) == null) {
                        transientError.value = errorMsg
                    }
                }
            } catch (e: Exception) {
                val cityId = currentCity?.id ?: "current_location"
                if (repository.getWeatherFromCache(cityId) == null) {
                    transientError.value = "定位失败，请稍后重试"
                }
            } finally {
                _isLocating.value = false
            }
        }
    }

    fun fetchWeather() {
        viewModelScope.launch {
            val refreshCity = selectedCityForRefresh()
            if (refreshCity != null && shouldSkipRefresh(refreshCity)) return@launch
            performRefreshWithAnimation(refreshCity)
        }
    }

    fun fetchDefaultWeather() {
        viewModelScope.launch {
            transientError.value = null
            val result = refreshWeatherUseCase.refreshDefault()
            result.onFailure { e ->
                if (repository.getWeatherFromCache("current_location") == null) {
                    transientError.value = e.message ?: "获取天气数据失败"
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val refreshCity = selectedCityForRefresh()
            _isRefreshing.value = true
            _refreshPhase.value = RefreshPhase.Refreshing
            val startTime = System.currentTimeMillis()

            val isLimited = refreshCity != null && shouldSkipRefresh(refreshCity)
            if (!isLimited) {
                refreshSelectedWeather(refreshCity)
            } else {
                Log.d(TAG, "refresh(): skip actual refresh due to rate limiting/fresh cache, but show animation")
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1000) delay(1000 - elapsed)
            _isRefreshing.value = false
            _refreshPhase.value = RefreshPhase.Success
            delay(1000)
            _refreshPhase.value = RefreshPhase.Idle
        }
    }

    fun onResume() {
        viewModelScope.launch {
            val cities = manageCityUseCase.getCities()
            _savedCities.value = cities
            val gpsCity = cities.find { it.isCurrentLocation }
            if (gpsCity != null) {
                _selectedCityId.value = gpsCity.id
            }
            val refreshCity = selectedCityForRefresh()
            if (refreshCity != null && shouldSkipRefresh(refreshCity)) return@launch
            performRefreshWithAnimation(refreshCity)
        }
    }

    fun silentRefresh() {
        viewModelScope.launch {
            val refreshCity = selectedCityForRefresh()
            if (refreshCity != null && shouldSkipRefresh(refreshCity)) return@launch
            performRefreshWithAnimation(refreshCity, minElapsedMs = 1000L, successDelayMs = 1000L)
        }
    }

    private suspend fun performRefreshWithAnimation(
        city: City?,
        minElapsedMs: Long = 500L,
        successDelayMs: Long = 300L
    ) {
        _refreshPhase.value = RefreshPhase.Refreshing
        val startTime = System.currentTimeMillis()
        val refreshed = refreshSelectedWeather(city, silent = true)
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed < minElapsedMs) delay(minElapsedMs - elapsed)
        if (refreshed) {
            _refreshPhase.value = RefreshPhase.Success
            delay(successDelayMs)
        }
        _refreshPhase.value = RefreshPhase.Idle
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
        transientError.value = null
        val result = refreshWeatherUseCase.refreshWithLocation()
        val success = result is SyncResult.Success
        if (!success && !silent) {
            val errorMsg = (result as? SyncResult.Error)?.message ?: "获取天气数据失败，请稍后重试"
            val city = _savedCities.value.find { it.isCurrentLocation }
            if (city != null && repository.getWeatherFromCache(city.id) == null) {
                transientError.value = errorMsg
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
        if (response == null) {
            val errorMsg = (result as? SyncResult.Error)?.message ?: "获取天气数据失败"
            if (!silent && repository.getWeatherFromCache(city.id) == null) {
                transientError.value = errorMsg
            }
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

    private fun City.isUnresolvedLocationPlaceholder(): Boolean {
        if (!isCurrentLocation) return false
        if (locationManager.getCachedLocation() != null) return false
        return kotlin.math.abs(longitude - LocationManager.DEFAULT_LONGITUDE) < 0.0001 &&
            kotlin.math.abs(latitude - LocationManager.DEFAULT_LATITUDE) < 0.0001
    }

    private suspend fun shouldSkipRefresh(city: City): Boolean {
        if (city.isUnresolvedLocationPlaceholder()) return false
        if (repository.getWeatherFromCache(city.id) == null) return false
        return refreshWeatherUseCase.isFreshEnough(city.id)
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
