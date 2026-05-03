package com.skypulse.weather.viewmodel

import android.app.Application
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.repository.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.Locale

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Success(
        val weather: WeatherResponse,
        val locationName: String = "定位中..."
    ) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Only true during manual pull-to-refresh — drives the spinner/checkmark UI
    private val _isManualRefreshing = MutableStateFlow(false)
    val isManualRefreshing: StateFlow<Boolean> = _isManualRefreshing.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _lastFetchTime = MutableStateFlow(0L)
    val lastFetchTime: StateFlow<Long> = _lastFetchTime.asStateFlow()

    /** Force re-acquire GPS location, then refresh weather */
    @Suppress("MissingPermission")
    fun relocateAndRefresh() {
        viewModelScope.launch {
            _isLocating.value = true
            try {
                // Try fresh GPS location with 10s timeout, fallback to cached location
                val location = try {
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .setMaxUpdateAgeMillis(0)
                        .build()
                    kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                            fusedLocationClient.getCurrentLocation(request, null)
                                .addOnSuccessListener { cont.resume(it) {} }
                                .addOnFailureListener { cont.resume(null) {} }
                        }
                    } ?: getLastLocation() // timeout -> fallback to cached
                } catch (_: Exception) {
                    getLastLocation()
                }
                val lon: Double
                val lat: Double
                val locationName: String
                if (location != null) {
                    lon = location.longitude
                    lat = location.latitude
                    locationName = getLocationName(location.latitude, location.longitude)
                } else {
                    lon = 116.4074
                    lat = 39.9042
                    locationName = "北京市 (默认)"
                }
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

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _isManualRefreshing.value = true
            doFetchWeather()
            _isRefreshing.value = false
            _isManualRefreshing.value = false
        }
    }

    /** Silent refresh — no UI indicators (for background/auto refresh) */
    fun silentRefresh() {
        viewModelScope.launch {
            doFetchWeather()
        }
    }

    private suspend fun doFetchWeather() {
        try {
            val location = getLastLocation()
            val lon: Double
            val lat: Double
            val locationName: String

            if (location != null) {
                lon = location.longitude
                lat = location.latitude
                locationName = getLocationName(location.latitude, location.longitude)
            } else {
                lon = 116.4074
                lat = 39.9042
                locationName = "北京市 (默认)"
            }

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
        } catch (e: Exception) {
            _uiState.value = WeatherUiState.Error("获取天气数据失败，请稍后重试")
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

    @Suppress("MissingPermission")
    private suspend fun getLastLocation(): Location? {
        return try {
            val task = fusedLocationClient.lastLocation
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                task.addOnSuccessListener { cont.resume(it) {} }
                task.addOnFailureListener { cont.resume(null) {} }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getLocationName(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(getApplication(), Locale.CHINA)
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
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
