package com.skypulse.weather.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.skypulse.weather.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skypulse.weather.api.ApiClient
import com.skypulse.weather.data.CityDatabase
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.model.City
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Success(
        val weather: WeatherResponse,
        val locationName: String = "定位中..."
    ) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

private class LocationFailure(message: String) : Exception(message)

enum class RefreshPhase {
    Idle, Refreshing, Success
}

enum class AppScreen {
    CityList, CityDetail, Settings
}

data class CityWeatherData(
    val weather: WeatherResponse? = null,
    val error: String? = null
)

data class CitySearchResult(
    val name: String,
    val district: String,
    val longitude: Double,
    val latitude: Double
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "WeatherVM"
        private const val API_COOLDOWN_MS = 30_000L
        private const val DEFAULT_LOCATION_NAME = "北京市"
        private const val DEFAULT_LONGITUDE = 116.4074
        private const val DEFAULT_LATITUDE = 39.9042
        private const val FRESH_CACHE_MAX_AGE_MS = 5 * 60 * 1000L
        private const val STALE_CACHE_MAX_AGE_MS = 30 * 60 * 1000L
    }

    private val repository = WeatherRepository()
    private val cityManager = CityManager(application)
    private val cityDatabase = CityDatabase(application)
    private val weatherCache = WeatherCache(application)

    // --- Screen navigation ---
    private val _currentScreen = MutableStateFlow(AppScreen.CityDetail)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // --- Saved cities ---
    private val _savedCities = MutableStateFlow<List<City>>(emptyList())
    val savedCities: StateFlow<List<City>> = _savedCities.asStateFlow()

    // --- Weather data for each city ---
    private val _cityWeatherMap = MutableStateFlow<Map<String, CityWeatherData>>(emptyMap())
    val cityWeatherMap: StateFlow<Map<String, CityWeatherData>> = _cityWeatherMap.asStateFlow()

    // --- Selected city for detail view ---
    private val _selectedCityId = MutableStateFlow<String?>(null)
    val selectedCityId: StateFlow<String?> = _selectedCityId.asStateFlow()

    // --- City search ---
    private val _searchResults = MutableStateFlow<List<CitySearchResult>>(emptyList())
    val searchResults: StateFlow<List<CitySearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    // --- Original GPS-based state (kept for detail view compatibility) ---
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshPhase = MutableStateFlow(RefreshPhase.Idle)
    val refreshPhase: StateFlow<RefreshPhase> = _refreshPhase.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _lastFetchTime = MutableStateFlow(0L)

    init {
        _savedCities.value = cityManager.getCities()
        // Load cached weather data so city list has data immediately
        val cachedMap = mutableMapOf<String, CityWeatherData>()
        for (city in _savedCities.value) {
            val cached = weatherCache.load(city.id)
            if (cached != null) {
                cachedMap[city.id] = CityWeatherData(weather = cached)
            }
        }
        if (cachedMap.isNotEmpty()) {
            _cityWeatherMap.value = cachedMap
        }
        // Initialize detail screen with cached data for current location
        val currentCity = _savedCities.value.find { it.isCurrentLocation }
        if (currentCity != null) {
            val cachedWeather = weatherCache.load(currentCity.id)
            if (cachedWeather != null) {
                _uiState.value = WeatherUiState.Success(
                    weather = cachedWeather,
                    locationName = currentCity.name
                )
            }
        }
    }

    // ============ Navigation ============

    fun navigateToCityList() {
        _currentScreen.value = AppScreen.CityList
        // Only load weather for cities that don't have data yet
        val existingData = _cityWeatherMap.value
        val citiesToLoad = _savedCities.value.filter { city ->
            val data = existingData[city.id]
            data == null || data.weather == null
        }
        if (citiesToLoad.isNotEmpty()) {
            viewModelScope.launch {
                citiesToLoad.map { city ->
                    async { loadWeatherForCity(city) }
                }.awaitAll()
            }
        }
    }

    fun navigateToCityDetail(cityId: String) {
        _selectedCityId.value = cityId
        _currentScreen.value = AppScreen.CityDetail

        val city = _savedCities.value.find { it.id == cityId } ?: return
        if (city.isCurrentLocation) {
            fetchWeather()
        } else {
            viewModelScope.launch { fetchWeatherForCity(city) }
        }
    }

    fun navigateToSettings() {
        _currentScreen.value = AppScreen.Settings
    }

    fun navigateBack() {
        when (_currentScreen.value) {
            AppScreen.Settings, AppScreen.CityList -> _currentScreen.value = AppScreen.CityDetail
            else -> {}
        }
    }

    private val _updateState = MutableStateFlow<UpdateCheckResult?>(null)
    val updateState: StateFlow<UpdateCheckResult?> = _updateState.asStateFlow()

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

    // ============ City Management ============

    fun addCity(searchResult: CitySearchResult) {
        val city = City(
            id = UUID.randomUUID().toString(),
            name = searchResult.name,
            longitude = searchResult.longitude,
            latitude = searchResult.latitude,
            isCurrentLocation = false
        )
        cityManager.addCity(city)
        _savedCities.value = cityManager.getCities()
        _searchResults.value = emptyList()
        viewModelScope.launch { loadWeatherForCity(city) }
    }

    fun removeCity(cityId: String) {
        cityManager.removeCity(cityId)
        _savedCities.value = cityManager.getCities()
        val currentMap = _cityWeatherMap.value.toMutableMap()
        currentMap.remove(cityId)
        _cityWeatherMap.value = currentMap
        weatherCache.remove(cityId)
    }

    // ============ City Search ============

    fun searchCities(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200)
            _isSearching.value = true
            try {
                val results = withContext(Dispatchers.IO) {
                    cityDatabase.search(query).map { city ->
                        CitySearchResult(
                            name = city.name,
                            district = city.province,
                            longitude = city.lon,
                            latitude = city.lat
                        )
                    }
                }
                _searchResults.value = results
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "City search failed", e)
                    _searchResults.value = emptyList()
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    // ============ Multi-city Weather Loading ============

    private suspend fun loadWeatherForCity(city: City) {
        val result = repository.getWeather(city.longitude, city.latitude)
        val updatedMap = _cityWeatherMap.value.toMutableMap()
        result.fold(
            onSuccess = { response ->
                updatedMap[city.id] = CityWeatherData(weather = response)
                weatherCache.save(city.id, response)
            },
            onFailure = { e ->
                updatedMap[city.id] = CityWeatherData(error = mapError(e))
            }
        )
        _cityWeatherMap.value = updatedMap
    }

    private fun fetchWeatherForCity(city: City) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            val result = fetchWithRetry(city.longitude, city.latitude)
            result.fold(
                onSuccess = { response ->
                    _lastFetchTime.value = System.currentTimeMillis()
                    _uiState.value = WeatherUiState.Success(
                        weather = response,
                        locationName = city.name
                    )
                    weatherCache.save(city.id, response)
                },
                onFailure = { e ->
                    _uiState.value = WeatherUiState.Error(mapError(e))
                }
            )
        }
    }

    // Ensure current location city exists in saved cities
    fun ensureCurrentLocationCity() {
        val cities = _savedCities.value.toMutableList()
        val hasCurrentLocation = cities.any { it.isCurrentLocation }
        if (!hasCurrentLocation) {
            val currentLocationCity = City(
                id = "current_location",
                name = DEFAULT_LOCATION_NAME,
                longitude = DEFAULT_LONGITUDE,
                latitude = DEFAULT_LATITUDE,
                isCurrentLocation = true
            )
            cities.add(0, currentLocationCity)
            cityManager.saveCities(cities)
            _savedCities.value = cities
        }
    }

    fun updateCurrentLocationCityName(name: String) {
        val cities = _savedCities.value.toMutableList()
        val index = cities.indexOfFirst { it.isCurrentLocation }
        if (index >= 0) {
            cities[index] = cities[index].copy(name = name)
            cityManager.saveCities(cities)
            _savedCities.value = cities
        }
    }

    fun updateCurrentLocationCityCoords(lon: Double, lat: Double) {
        val cities = _savedCities.value.toMutableList()
        val index = cities.indexOfFirst { it.isCurrentLocation }
        if (index >= 0) {
            cities[index] = cities[index].copy(longitude = lon, latitude = lat)
            cityManager.saveCities(cities)
            _savedCities.value = cities
        }
    }

    // ============ Original GPS-based methods (unchanged) ============

    @Suppress("MissingPermission")
    fun relocateAndRefresh() {
        viewModelScope.launch {
            _isLocating.value = true
            try {
                logLocationDiagnostics()
                val location = requestAmapLocation()
                if (location != null) {
                    val lon = location.longitude
                    val lat = location.latitude
                    val locationName = getLocationName(location)
                    updateCurrentLocationCityCoords(lon, lat)
                    updateCurrentLocationCityName(locationName)
                    fetchWeatherForLocation(lon, lat, locationName)
                } else {
                    val diagnostic = getLocationDiagnostic()
                    if (diagnostic != null) {
                        _uiState.value = WeatherUiState.Error(diagnostic)
                    } else {
                        _uiState.value = WeatherUiState.Error("无法获取定位，请到室外空旷处重试。如持续失败，请检查手机\"位置信息\"设置是否开启")
                    }
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
                lon = DEFAULT_LONGITUDE,
                lat = DEFAULT_LATITUDE,
                locationName = DEFAULT_LOCATION_NAME
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshPhase.value = RefreshPhase.Refreshing
            val startTime = System.currentTimeMillis()
            val sinceLast = System.currentTimeMillis() - _lastFetchTime.value
            if (sinceLast < API_COOLDOWN_MS) {
                delay(800)
            } else {
                doFetchWeather()
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 1500) delay(1500 - elapsed)
            }
            _isRefreshing.value = false
            _refreshPhase.value = RefreshPhase.Success
            delay(2000)
            _refreshPhase.value = RefreshPhase.Idle
        }
    }

    fun silentRefresh() {
        viewModelScope.launch {
            val sinceLast = System.currentTimeMillis() - _lastFetchTime.value
            if (sinceLast >= API_COOLDOWN_MS) {
                doFetchWeather(silent = true)
            }
            _refreshPhase.value = RefreshPhase.Success
            delay(2000)
            _refreshPhase.value = RefreshPhase.Idle
        }
    }

    private suspend fun doFetchWeather(silent: Boolean = false) {
        try {
            logLocationDiagnostics()
            val location = requestAmapLocation()

            if (location != null) {
                val lon = location.longitude
                val lat = location.latitude
                val locationName = getLocationName(location)
                updateCurrentLocationCityCoords(lon, lat)
                updateCurrentLocationCityName(locationName)
                fetchWeatherForLocation(lon, lat, locationName, silent)
            } else {
                if (silent && _uiState.value is WeatherUiState.Success) {
                    Log.w(TAG, "Silent refresh location failed, keeping cached data")
                    return
                }
                val diagnostic = getLocationDiagnostic()
                if (diagnostic != null) {
                    _uiState.value = WeatherUiState.Error(diagnostic)
                } else {
                    _uiState.value = WeatherUiState.Error("无法获取定位，请到室外或靠近窗户重试。如持续失败可使用\"刷新\"按钮重试")
                }
            }
        } catch (e: LocationFailure) {
            if (!silent || _uiState.value !is WeatherUiState.Success) {
                _uiState.value = WeatherUiState.Error(e.message ?: "定位失败")
            }
        } catch (e: Exception) {
            if (!silent || _uiState.value !is WeatherUiState.Success) {
                _uiState.value = WeatherUiState.Error("获取天气数据失败，请稍后重试")
            }
        }
    }

    private suspend fun fetchWeatherForLocation(
        lon: Double,
        lat: Double,
        locationName: String,
        silent: Boolean = false
    ) {
        val result = fetchWithRetry(lon, lat)
        result.fold(
            onSuccess = { response ->
                _lastFetchTime.value = System.currentTimeMillis()
                _uiState.value = WeatherUiState.Success(
                    weather = response,
                    locationName = locationName
                )
                // Cache for current location city
                val currentCity = _savedCities.value.find { it.isCurrentLocation }
                if (currentCity != null) {
                    weatherCache.save(currentCity.id, response)
                }
            },
            onFailure = { e ->
                if (!silent || _uiState.value !is WeatherUiState.Success) {
                    _uiState.value = WeatherUiState.Error(mapError(e))
                }
            }
        )
    }

    private suspend fun fetchWithRetry(
        lon: Double,
        lat: Double,
        maxRetries: Int = 2
    ): Result<WeatherResponse> {
        var lastException: Exception? = null
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) delay(1000L * attempt)
            val result = repository.getWeather(lon, lat)
            result.fold(
                onSuccess = { return Result.success(it) },
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
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return "定位权限未授予，请在应用设置中允许定位权限"
        }
        val apiKey = BuildConfig.AMAP_API_KEY.trim()
        if (apiKey.isEmpty()) {
            return "未配置高德定位 Key"
        }
        return null
    }

    private fun logLocationDiagnostics() {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Permissions: FINE=$hasFine, COARSE=$hasCoarse")
    }

    private suspend fun requestAmapLocation(): Location? {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return null
        }

        val apiKey = BuildConfig.AMAP_API_KEY.trim()
        if (apiKey.isEmpty()) {
            throw LocationFailure("未配置高德定位 Key。请在 local.properties 中添加 AMAP_API_KEY=你的高德Android定位Key 后重新打包")
        }

        return try {
            AMapLocationClient.updatePrivacyShow(ctx, true, true)
            AMapLocationClient.updatePrivacyAgree(ctx, true)
            AMapLocationClient.setApiKey(apiKey)

            kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                    val client = AMapLocationClient(ctx)
                    val option = AMapLocationClientOption().apply {
                        locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                        isOnceLocation = true
                        isOnceLocationLatest = true
                        isNeedAddress = true
                        isLocationCacheEnable = true
                        httpTimeOut = 8_000L
                        setGpsFirst(false)
                    }
                    val listener = com.amap.api.location.AMapLocationListener { location ->
                        client.stopLocation()
                        client.onDestroy()

                        if (!cont.isActive) return@AMapLocationListener

                        if (location != null && location.errorCode == AMapLocation.LOCATION_SUCCESS) {
                            Log.d(TAG, "AMap location: ${location.latitude}, ${location.longitude}, type=${location.locationType}, city=${location.city}, district=${location.district}")
                            cont.resume(location)
                        } else {
                            val code = location?.errorCode
                            val detail = location?.locationDetail.orEmpty()
                            Log.w(TAG, "AMap failed: code=$code detail=$detail")
                            cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation {
                        try {
                            client.stopLocation()
                            client.onDestroy()
                        } catch (_: Exception) {
                        }
                    }
                    client.setLocationOption(option)
                    client.setLocationListener(listener)
                    client.startLocation()
                }
            }
        } catch (e: LocationFailure) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "requestAmapLocation exception", e)
            null
        }
    }

    private suspend fun getLocationName(location: Location): String {
        // AMap location already has POI/street data attached
        if (location is AMapLocation) {
            val amapName = buildAmapLocationName(location)
            if (amapName.isNotBlank()) return amapName
        }

        // Non-AMapLocation (e.g. cached system location): try AMap reverse geocoding for POI
        val amapReverse = amapReverseGeocode(location.latitude, location.longitude)
        if (amapReverse != null) return amapReverse

        // Fallback: Android Geocoder
        return geocoderFallback(location.latitude, location.longitude)
    }

    private fun buildAmapLocationName(location: AMapLocation): String = buildString {
        location.city?.takeIf { it.isNotBlank() }?.let { append(it) }
        location.district?.takeIf { it.isNotBlank() && it != location.city }?.let { append(it) }
        val poi = location.poiName?.takeIf { it.isNotBlank() }
        val street = location.street?.takeIf { it.isNotBlank() }
        val streetNum = location.streetNum?.takeIf { it.isNotBlank() }
        when {
            poi != null -> append(" $poi")
            street != null -> {
                append(street)
                streetNum?.let { append(it) }
            }
        }
        if (isEmpty()) {
            location.address?.takeIf { it.isNotBlank() }?.let { append(it) }
        }
    }

    private suspend fun amapReverseGeocode(lat: Double, lon: Double): String? {
        return try {
            val ctx = getApplication<Application>()
            val search = GeocodeSearch(ctx)
            val query = RegeocodeQuery(
                LatLonPoint(lat, lon),
                200f,
                GeocodeSearch.AMAP
            )
            val result = kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                        override fun onRegeocodeSearched(result: com.amap.api.services.geocoder.RegeocodeResult?, rCode: Int) {
                            if (cont.isActive) {
                                if (rCode == 1000 && result?.regeocodeAddress != null) {
                                    val addr = result.regeocodeAddress
                                    val name = buildString {
                                        addr.city?.takeIf { it.isNotBlank() }?.let { append(it) }
                                        addr.district?.takeIf { it.isNotBlank() && it != addr.city }?.let { append(it) }
                                        val pois = addr.pois
                                        val poi = pois?.firstOrNull { !it.title.isNullOrBlank() }
                                        val street = addr.streetNumber?.street?.takeIf { it.isNotBlank() }
                                        val streetNum = addr.streetNumber?.number?.takeIf { it.isNotBlank() }
                                        when {
                                            poi != null -> append(" ${poi.title}")
                                            street != null -> {
                                                append(street)
                                                streetNum?.let { append(it) }
                                            }
                                        }
                                        if (isEmpty()) {
                                            addr.formatAddress?.takeIf { it.isNotBlank() }?.let { append(it) }
                                        }
                                    }
                                    cont.resume(name.takeIf { it.isNotBlank() }, null)
                                } else {
                                    cont.resume(null, null)
                                }
                            }
                        }
                        override fun onGeocodeSearched(result: com.amap.api.services.geocoder.GeocodeResult?, rCode: Int) {}
                    })
                    search.getFromLocationAsyn(query)
                }
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun geocoderFallback(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(getApplication(), Locale.CHINA)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                buildString {
                    addr.locality?.let { append(it) }
                    addr.subLocality?.let { append(it) }
                    val thoroughfare = addr.thoroughfare?.takeIf { it.isNotBlank() }
                    val subThoroughfare = addr.subThoroughfare?.takeIf { it.isNotBlank() }
                    if (thoroughfare != null) {
                        append(" $thoroughfare")
                        subThoroughfare?.let { append(it) }
                    }
                }.ifEmpty { "未知位置" }
            } else {
                "未知位置"
            }
        } catch (_: Exception) {
            "未知位置"
        }
    }
}

sealed class UpdateCheckResult {
    data object Checking : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val version: String, val url: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
