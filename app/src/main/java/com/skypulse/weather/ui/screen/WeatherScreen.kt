package com.skypulse.weather.ui.screen

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.skypulse.weather.ui.components.*
import com.skypulse.weather.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ErrorOutline
import com.skypulse.weather.util.WeatherUtils
import com.skypulse.weather.viewmodel.AppScreen
import com.skypulse.weather.viewmodel.WeatherUiState
import com.skypulse.weather.viewmodel.CitySearchViewModel
import com.skypulse.weather.viewmodel.WeatherViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = hiltViewModel(),
    searchViewModel: CitySearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val refreshPhase by viewModel.refreshPhase.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val savedCities by viewModel.savedCities.collectAsState()
    val cityWeatherMap by viewModel.cityWeatherMap.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val selectedAlertIndex by viewModel.selectedAlertIndex.collectAsState()

    // Permissions
    val notificationPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else null
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    val hasLocationPermission = locationPermissions.permissions.any { it.status.isGranted }
    var useDefaultLocation by rememberSaveable { mutableStateOf(false) }

    // Request all permissions on first launch
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            if (notificationPermission != null && !notificationPermission.status.isGranted) {
                notificationPermission.launchPermissionRequest()
            }
            if (!hasLocationPermission) {
                locationPermissions.launchMultiplePermissionRequest()
            }
            permissionsRequested = true
        }
    }

    // Auto-refresh on resume
    var backgroundTimestamp by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> backgroundTimestamp = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    if (backgroundTimestamp > 0L) {
                        viewModel.silentRefresh()
                        backgroundTimestamp = 0L
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasLocationPermission, permissionsRequested) {
        if (hasLocationPermission) {
            useDefaultLocation = false
            viewModel.ensureCurrentLocationCity()
            viewModel.fetchWeather()
        } else if (permissionsRequested) {
            useDefaultLocation = true
            viewModel.ensureCurrentLocationCity()
            viewModel.fetchDefaultWeather()
        }
    }

    val skycon = when (val state = uiState) {
        is WeatherUiState.Success -> state.weather.result?.realtime?.skycon
        else -> null
    }
    val isDay = WeatherUtils.isCurrentlyDay()
    val weatherTheme = remember(skycon, isDay) {
        WeatherUtils.getWeatherTheme(skycon, isDay)
    }

    BackHandler(enabled = currentScreen != AppScreen.CityDetail) {
        viewModel.navigateBack()
    }

    CompositionLocalProvider(LocalWeatherTheme provides weatherTheme) {
        when (currentScreen) {
            AppScreen.CityList -> {
                CityListScreen(
                    cities = savedCities,
                    cityWeatherMap = cityWeatherMap,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onCityClick = { cityId -> viewModel.navigateToCityDetail(cityId) },
                    onAddCity = { result -> viewModel.addCity(result.name, result.longitude, result.latitude) },
                    onRemoveCity = { cityId -> viewModel.removeCity(cityId) },
                    onSearch = { query -> searchViewModel.searchCities(query) },
                    onClearSearch = { searchViewModel.clearSearchResults() }
                )
            }

            AppScreen.CityDetail -> {
                WeatherBackground(skycon = skycon) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (val state = uiState) {
                            is WeatherUiState.Loading -> {
                                LoadingShimmer(
                                    modifier = Modifier.fillMaxSize().statusBarsPadding()
                                )
                            }
                            is WeatherUiState.Success -> {
                                WeatherContent(
                                    state = state,
                                    isLocating = isLocating,
                                    refreshPhase = refreshPhase,
                                    onLocationClick = { viewModel.relocateAndRefresh() },
                                    onRefresh = { viewModel.refresh() },
                                    onListClick = { viewModel.navigateToCityList() },
                                    onSettingsClick = { viewModel.navigateToSettings() },
                                    onAlertClick = { viewModel.navigateToAlertDetail(0) }
                                )
                            }
                            is WeatherUiState.Error -> {
                                ErrorContent(
                                    message = state.message,
                                    onRetry = {
                                        if (hasLocationPermission && !useDefaultLocation) {
                                            viewModel.fetchWeather()
                                        } else {
                                            viewModel.fetchDefaultWeather()
                                        }
                                    },
                                    onUseDefault = {
                                        useDefaultLocation = true
                                        viewModel.fetchDefaultWeather()
                                    }
                                )
                            }
                        }

                    }
                }
            }

            AppScreen.Settings -> {
                SettingsScreen(
                    onBack = { viewModel.navigateBack() },
                    onCheckUpdate = { viewModel.checkForUpdates() },
                    updateState = updateState,
                    onClearUpdateState = { viewModel.clearUpdateState() }
                )
            }

            AppScreen.AlertDetail -> {
                val contents = when (val s = uiState) {
                    is WeatherUiState.Success -> s.weather.result?.alert?.content.orEmpty()
                    else -> emptyList()
                }
                AlertDetailScreen(
                    alerts = contents,
                    initialSelectedIndex = selectedAlertIndex,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onUseDefault: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "加载失败",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("重试") }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onUseDefault) {
            Text("使用默认位置", color = TextSecondary)
        }
    }
}


