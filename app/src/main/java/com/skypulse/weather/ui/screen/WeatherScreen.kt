package com.skypulse.weather.ui.screen

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.skypulse.weather.ui.components.*
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.util.WeatherUtils
import com.skypulse.weather.viewmodel.AppScreen
import com.skypulse.weather.viewmodel.RefreshPhase
import com.skypulse.weather.viewmodel.WeatherUiState
import com.skypulse.weather.viewmodel.WeatherViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val refreshPhase by viewModel.refreshPhase.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val savedCities by viewModel.savedCities.collectAsState()
    val cityWeatherMap by viewModel.cityWeatherMap.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    val hasLocationPermission = locationPermissions.permissions.any { it.status.isGranted }
    var useDefaultLocation by rememberSaveable { mutableStateOf(false) }

    // Track when app went to background for auto-refresh
    var backgroundTimestamp by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Silent refresh every time app returns from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    backgroundTimestamp = System.currentTimeMillis()
                }
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            useDefaultLocation = false
            viewModel.ensureCurrentLocationCity()
            viewModel.fetchWeather()
        } else {
            viewModel.ensureCurrentLocationCity()
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

    CompositionLocalProvider(LocalWeatherTheme provides weatherTheme) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState == AppScreen.CityList) {
                    slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) togetherWith
                        slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(200))
                } else {
                    slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300)) togetherWith
                        slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200))
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                AppScreen.CityList -> {
                    CityListScreen(
                        cities = savedCities,
                        cityWeatherMap = cityWeatherMap,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        onCityClick = { cityId -> viewModel.navigateToCityDetail(cityId) },
                        onAddCity = { result -> viewModel.addCity(result) },
                        onRemoveCity = { cityId -> viewModel.removeCity(cityId) },
                        onSearch = { query -> viewModel.searchCities(query) },
                        onClearSearch = { viewModel.clearSearchResults() },
                        onClose = {
                            // Navigate back to the first city or current location
                            val firstCity = savedCities.firstOrNull()
                            if (firstCity != null) {
                                viewModel.navigateToCityDetail(firstCity.id)
                            }
                        }
                    )
                }

                AppScreen.CityDetail -> {
                    WeatherBackground(skycon = skycon) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (val state = uiState) {
                                is WeatherUiState.Loading -> {
                                    LoadingShimmer(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .statusBarsPadding()
                                    )
                                }

                                is WeatherUiState.Success -> {
                                    WeatherContent(
                                        state = state,
                                        isLocating = isLocating,
                                        refreshPhase = refreshPhase,
                                        onLocationClick = { viewModel.relocateAndRefresh() },
                                        onRefresh = { viewModel.refresh() },
                                        onListClick = { viewModel.navigateToCityList() }
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

                            // Permission request overlay
                            if (!hasLocationPermission && !useDefaultLocation) {
                                PermissionRequestContent(
                                    shouldShowRationale = locationPermissions.permissions.any { it.status.shouldShowRationale },
                                    onRequestPermission = { locationPermissions.launchMultiplePermissionRequest() },
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
        }
    }
}

@Composable
private fun WeatherContent(
    state: WeatherUiState.Success,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    onLocationClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onListClick: () -> Unit = {}
) {
    val result = state.weather.result
    val realtime = result?.realtime
    val todayTemp = result?.daily?.temperature?.firstOrNull()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Spacer(modifier = Modifier.height(12.dp))

        LocationHeader(
            locationName = state.locationName,
            isLocating = isLocating,
            refreshPhase = refreshPhase,
            onLocationClick = onLocationClick,
            onListClick = onListClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            CurrentWeather(
                realtime = realtime,
                todayHigh = todayTemp?.max,
                todayLow = todayTemp?.min,
                onRefresh = onRefresh
            )

            Spacer(modifier = Modifier.height(24.dp))

            result?.forecastKeypoint?.let { keypoint ->
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = keypoint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val minutelyData = result?.minutely?.precipitation_2h
            val showMinutely = !minutelyData.isNullOrEmpty() && minutelyData.any { it != 0.0 }

            if (showMinutely) {
                MinutelyPrecipitationCard(
                    minutely = result?.minutely,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            HourlyForecastCard(
                hourly = result?.hourly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            DailyForecastCard(
                daily = result?.daily,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
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
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("重试")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onUseDefault) {
            Text("使用默认位置", color = TextSecondary)
        }
    }
}

@Composable
private fun PermissionRequestContent(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onUseDefault: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "需要定位权限",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )

                Text(
                    text = if (shouldShowRationale) {
                        "为了获取您当前位置的天气信息，需要授予定位权限。"
                    } else {
                        "点击下方按钮授权定位，获取精准天气。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("授权定位")
                }

                TextButton(
                    onClick = onUseDefault,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("使用默认位置", color = TextSecondary)
                }
            }
        }
    }
}
