package com.skypulse.weather.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.skypulse.weather.BuildConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.skypulse.weather.api.ApiClient
import com.skypulse.weather.data.CityDatabase
import com.skypulse.weather.data.CityManager
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
    CityList, CityDetail
}

data class CityWeatherData(
    val weather: WeatherResponse? = null,
    val isLoading: Boolean = false,
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
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val cityManager = CityManager(application)
    private val cityDatabase = CityDatabase(application)

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
    val lastFetchTime: StateFlow<Long> = _lastFetchTime.asStateFlow()

    init {
        _savedCities.value = cityManager.getCities()
    }

    // ============ Navigation ============

    fun navigateToCityList() {
        _currentScreen.value = AppScreen.CityList
        // Only load weather for cities that don't have data yet
        val existingData = _cityWeatherMap.value
        val citiesToLoad = _savedCities.value.filter { city ->
            val data = existingData[city.id]
            data == null || (data.weather == null && !data.isLoading)
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
        // Remove from weather map
        val currentMap = _cityWeatherMap.value.toMutableMap()
        currentMap.remove(cityId)
        _cityWeatherMap.value = currentMap
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
        // Only show loading spinner if there's no existing data
        val existingData = _cityWeatherMap.value[city.id]
        if (existingData?.weather == null) {
            val currentMap = _cityWeatherMap.value.toMutableMap()
            currentMap[city.id] = CityWeatherData(isLoading = true)
            _cityWeatherMap.value = currentMap
        }

        val result = repository.getWeather(city.longitude, city.latitude)
        val updatedMap = _cityWeatherMap.value.toMutableMap()
        result.fold(
            onSuccess = { response ->
                updatedMap[city.id] = CityWeatherData(weather = response)
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
                    ?: requestFreshLocation()
                    ?: getBestCachedSystemLocation(STALE_CACHE_MAX_AGE_MS)
                    ?: getLastLocation()
                val lon: Double
                val lat: Double
                val locationName: String
                if (location != null) {
                    lon = location.longitude
                    lat = location.latitude
                    locationName = getLocationName(location)
                } else {
                    val diagnostic = getLocationDiagnostic()
                    if (diagnostic != null) {
                        _uiState.value = WeatherUiState.Error(diagnostic)
                        return@launch
                    }
                    Log.e(TAG, "All location methods failed, diagnostic=null")
                    _uiState.value = WeatherUiState.Error("无法获取定位，请到室外空旷处重试。如持续失败，请检查手机\"位置信息\"设置是否开启")
                    return@launch
                }

                // Update current location city coords
                updateCurrentLocationCityCoords(lon, lat)
                updateCurrentLocationCityName(locationName)
                fetchWeatherForLocation(lon, lat, locationName)
            } catch (e: Exception) {
                _uiState.value = WeatherUiState.Error("定位失败，请稍后重试")
            } finally {
                _isLocating.value = false
            }
        }
    }

    fun fetchWeather() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
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
                doFetchWeather()
            }
            _refreshPhase.value = RefreshPhase.Success
            delay(2000)
            _refreshPhase.value = RefreshPhase.Idle
        }
    }

    private suspend fun doFetchWeather() {
        try {
            logLocationDiagnostics()
            val location = getBestCachedSystemLocation(FRESH_CACHE_MAX_AGE_MS)
                ?: requestAmapLocation()
                ?: requestFreshLocation()
                ?: getLastLocation()
            val lon: Double
            val lat: Double
            val locationName: String

            if (location != null) {
                lon = location.longitude
                lat = location.latitude
                locationName = getLocationName(location)
            } else {
                val diagnostic = getLocationDiagnostic()
                if (diagnostic != null) {
                    _uiState.value = WeatherUiState.Error(diagnostic)
                    return
                }
                Log.e(TAG, "All location methods failed, diagnostic=null")
                _uiState.value = WeatherUiState.Error("无法获取定位，请到室外或靠近窗户重试。如持续失败可使用\"刷新\"按钮重试")
                return
            }

            // Update current location city
            updateCurrentLocationCityCoords(lon, lat)
            updateCurrentLocationCityName(locationName)
            fetchWeatherForLocation(lon, lat, locationName)
        } catch (e: LocationFailure) {
            _uiState.value = WeatherUiState.Error(e.message ?: "定位失败")
        } catch (e: Exception) {
            _uiState.value = WeatherUiState.Error("获取天气数据失败，请稍后重试")
        }
    }

    private suspend fun fetchWeatherForLocation(
        lon: Double,
        lat: Double,
        locationName: String
    ) {
        val result = fetchWithRetry(lon, lat)
        result.fold(
            onSuccess = { response ->
                _lastFetchTime.value = System.currentTimeMillis()
                _uiState.value = WeatherUiState.Success(
                    weather = response,
                    locationName = locationName
                )
            },
            onFailure = { e ->
                _uiState.value = WeatherUiState.Error(mapError(e))
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

    private suspend fun getLastLocation(): Location? {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val gmsAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
        if (!gmsAvailable) {
            Log.w(TAG, "getLastLocation: GMS unavailable, skip FusedLocation cache")
            return null
        }

        return try {
            val task = fusedLocationClient.lastLocation
            val loc = kotlinx.coroutines.withTimeoutOrNull(1_500L) {
                kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                    task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    task.addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
            if (loc != null) {
                Log.d(TAG, "getLastLocation: ${loc.latitude}, ${loc.longitude}")
            } else {
                Log.d(TAG, "getLastLocation: null (no cached location)")
            }
            loc
        } catch (e: Exception) {
            Log.w(TAG, "getLastLocation exception", e)
            null
        }
    }

    @Suppress("MissingPermission")
    private fun getBestCachedSystemLocation(maxAgeMs: Long): Location? {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val lm = ctx.getSystemService(LocationManager::class.java)
        val now = System.currentTimeMillis()
        val providers = buildList {
            if (hasCoarse) add(LocationManager.NETWORK_PROVIDER)
            if (hasFine) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        return providers
            .mapNotNull { provider ->
                try {
                    if (!lm.allProviders.contains(provider)) return@mapNotNull null
                    lm.getLastKnownLocation(provider)?.takeIf { now - it.time <= maxAgeMs }
                } catch (e: Exception) {
                    Log.w(TAG, "getLastKnownLocation failed: $provider", e)
                    null
                }
            }
            .maxByOrNull { it.time }
            ?.also {
                Log.d(TAG, "System cached location: ${it.provider} ${it.latitude}, ${it.longitude}")
            }
    }

    private fun getLocationDiagnostic(): String? {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return "定位权限未授予，请在应用设置中允许定位权限"
        }

        val lm = ctx.getSystemService(LocationManager::class.java)
        val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return when {
            !gpsEnabled && !networkEnabled -> "定位服务未开启，请在系统设置中打开位置信息"
            else -> null
        }
    }

    private fun logLocationDiagnostics() {
        val ctx = getApplication<Application>()
        val lm = ctx.getSystemService(LocationManager::class.java)

        val gmsStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx)
        val gmsOk = gmsStatus == ConnectionResult.SUCCESS
        Log.d(TAG, "GMS available: $gmsOk (status=$gmsStatus)")

        val allProviders = lm.allProviders
        val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        Log.d(TAG, "All providers: $allProviders")
        Log.d(TAG, "GPS enabled=$gpsEnabled, NETWORK enabled=$networkEnabled")

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

    private suspend fun requestFreshLocation(): Location? {
        val ctx = getApplication<Application>()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "requestFreshLocation: no location permission")
            return null
        }
        val canUseCoarse = hasFine || hasCoarse

        val lm = ctx.getSystemService(LocationManager::class.java)
        val networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        Log.d(TAG, "requestFreshLocation: networkEnabled=$networkEnabled, gpsEnabled=$gpsEnabled")

        getBestCachedSystemLocation(FRESH_CACHE_MAX_AGE_MS)?.let {
            return it
        }

        if (canUseCoarse && networkEnabled) {
            Log.d(TAG, "Trying NETWORK_PROVIDER...")
            val loc = requestLocationFromProvider(lm, LocationManager.NETWORK_PROVIDER, timeoutMs = 5_000L)
            if (loc != null) {
                Log.d(TAG, "Got NETWORK_PROVIDER location: ${loc.latitude}, ${loc.longitude}")
                return loc
            }
            Log.w(TAG, "NETWORK_PROVIDER returned null")
        }

        if (hasFine && gpsEnabled) {
            Log.d(TAG, "Trying GPS_PROVIDER via requestLocationUpdates...")
            val loc = requestLocationFromProvider(lm, LocationManager.GPS_PROVIDER, timeoutMs = 10_000L)
            if (loc != null) {
                Log.d(TAG, "Got GPS_PROVIDER location: ${loc.latitude}, ${loc.longitude}")
                return loc
            }
            Log.w(TAG, "GPS_PROVIDER returned null after 10s")
        }

        val gmsAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
        if (hasFine && gmsAvailable) {
            Log.d(TAG, "Trying FusedLocation (BALANCED)...")
            val loc = requestLocationFromFused()
            if (loc != null) {
                Log.d(TAG, "Got FusedLocation location: ${loc.latitude}, ${loc.longitude}")
                return loc
            }
            Log.w(TAG, "FusedLocation (BALANCED) returned null")

            Log.d(TAG, "Trying FusedLocation (LOW_POWER) as last resort...")
            val coarseLoc = requestLocationFromFusedLowPower()
            if (coarseLoc != null) {
                Log.d(TAG, "Got FusedLocation LOW_POWER: ${coarseLoc.latitude}, ${coarseLoc.longitude}")
                return coarseLoc
            }
            Log.w(TAG, "All location methods exhausted")
        } else if (!gmsAvailable) {
            Log.w(TAG, "GMS not available, skipping FusedLocation fallback")
        }

        return null
    }

    @Suppress("MissingPermission")
    private suspend fun requestLocationFromProvider(lm: LocationManager, provider: String, timeoutMs: Long = 8_000L): Location? {
        return try {
            Log.d(TAG, "requestLocationFromProvider: $provider, timeout=${timeoutMs}ms")
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            Log.d(TAG, "onLocationChanged: $provider -> ${loc.latitude}, ${loc.longitude}")
                            if (cont.isActive) {
                                try { lm.removeUpdates(this) } catch (_: Exception) {}
                                cont.resume(loc)
                            }
                        }
                        @Deprecated("Deprecated in API")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                            Log.d(TAG, "onStatusChanged: $provider status=$status")
                        }
                        override fun onProviderEnabled(provider: String) {
                            Log.d(TAG, "onProviderEnabled: $provider")
                        }
                        override fun onProviderDisabled(provider: String) {
                            Log.d(TAG, "onProviderDisabled: $provider")
                        }
                    }
                    cont.invokeOnCancellation {
                        try { lm.removeUpdates(listener) } catch (_: Exception) {}
                    }
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestLocationFromProvider exception: $provider", e)
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun requestLocationFromFused(): Location? {
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .build()
            kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                    fusedLocationClient.getCurrentLocation(request, null)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun requestLocationFromFusedLowPower(): Location? {
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_LOW_POWER)
                .setMaxUpdateAgeMillis(60_000L)
                .build()
            kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                    fusedLocationClient.getCurrentLocation(request, null)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getLocationName(location: Location): String {
        if (location is AMapLocation) {
            val amapName = buildString {
                location.city?.takeIf { it.isNotBlank() }?.let { append(it) }
                location.district?.takeIf { it.isNotBlank() && it != location.city }?.let { append(it) }
                if (isEmpty()) {
                    location.poiName?.takeIf { it.isNotBlank() }?.let { append(it) }
                }
                if (isEmpty()) {
                    location.address?.takeIf { it.isNotBlank() }?.let { append(it) }
                }
            }
            if (amapName.isNotBlank()) {
                return amapName
            }
        }

        return try {
            val geocoder = Geocoder(getApplication(), Locale.CHINA)
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                buildString {
                    addr.locality?.let { append(it) }
                    addr.subLocality?.let { append(it) }
                }.ifEmpty { "未知位置" }
            } else {
                "未知位置"
            }
        } catch (e: Exception) {
            "未知位置"
        }
    }
}
