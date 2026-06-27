package com.skypulse.weather.ui.screen

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.skypulse.weather.ui.components.*
import com.skypulse.weather.ui.screen.PermissionOnboardingScreen
import com.skypulse.weather.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import com.skypulse.weather.util.WeatherUtils
import com.skypulse.weather.viewmodel.CityWeatherData
import com.skypulse.weather.viewmodel.AppScreen
import com.skypulse.weather.viewmodel.WeatherUiState
import com.skypulse.weather.viewmodel.CitySearchViewModel
import com.skypulse.weather.viewmodel.WeatherViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

val LocalSkipCardAnimation = compositionLocalOf { false }

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
    val selectedCityId by viewModel.selectedCityId.collectAsState()
    val swipeDirection by viewModel.swipeDirection.collectAsState()

    val currentCityIndex by remember {
        derivedStateOf {
            val cities = savedCities
            val selId = selectedCityId
            if (selId == null) {
                cities.indexOfFirst { it.isCurrentLocation }.coerceAtLeast(0)
            } else {
                cities.indexOfFirst { it.id == selId }.coerceAtLeast(0)
            }
        }
    }

    val contentScrollState = rememberScrollState()
    val isScrolled by remember {
        derivedStateOf { contentScrollState.value > 0 }
    }

    var previousScreen by remember { mutableStateOf(currentScreen) }
    val justEnteredCityDetail = remember { mutableStateOf(true) }
    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.CityDetail && previousScreen != AppScreen.CityDetail) {
            justEnteredCityDetail.value = true
            delay(600)
            justEnteredCityDetail.value = false
        }
        previousScreen = currentScreen
    }

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

    val showOnboarding by viewModel.showOnboarding.collectAsState()
    var allPermissionsHandled by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(hasLocationPermission, allPermissionsHandled) {
        if (hasLocationPermission) {
            useDefaultLocation = false
            viewModel.ensureCurrentLocationCity()
            viewModel.fetchWeather()
            viewModel.completeOnboarding()
        } else if (allPermissionsHandled) {
            // Location denied - go back to onboarding
            allPermissionsHandled = false
        }
    }

    val skycon = when (val s = uiState) {
        is WeatherUiState.Success -> s.weather.result?.realtime?.skycon
        else -> null
    }
    val isDay = WeatherUtils.isCurrentlyDay()
    val weatherTheme = remember(skycon, isDay) {
        WeatherUtils.getWeatherTheme(skycon, isDay)
    }

    BackHandler(enabled = currentScreen != AppScreen.CityDetail) {
        viewModel.navigateBack()
    }

    if (showOnboarding && !allPermissionsHandled) {
        PermissionOnboardingScreen(
            onFinished = {
                allPermissionsHandled = true
            }
        )
        return
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
                    onClearSearch = { searchViewModel.clearSearchResults() },
                    onBack = { viewModel.navigateBack() }
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
                                // Swipe state — shared between gesture and animation
                                var swiping by remember { mutableStateOf(false) }
                                val swipingUpdated by rememberUpdatedState(swiping)
                                var lastTriggerTime by remember { mutableLongStateOf(0L) }

                                LaunchedEffect(selectedCityId) {
                                    swiping = true
                                    delay(350)
                                    swiping = false
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {},
                                                onDragCancel = {},
                                                onHorizontalDrag = { change, dragAmount ->
                                                    val now = System.currentTimeMillis()
                                                    if (!swipingUpdated
                                                        && abs(dragAmount) > 30f
                                                        && (now - lastTriggerTime) > 500
                                                    ) {
                                                        change.consume()
                                                        lastTriggerTime = now
                                                        if (dragAmount < 0f) viewModel.switchToNextCity()
                                                        else viewModel.switchToPreviousCity()
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    LocationHeader(
                                        locationName = state.locationName,
                                        isLocating = isLocating,
                                        refreshPhase = refreshPhase,
                                        onLocationClick = { viewModel.relocateAndRefresh() },
                                        onListClick = { viewModel.navigateToCityList() },
                                        onSettingsClick = { viewModel.navigateToSettings() }
                                    )

                                    if (savedCities.size > 1) {
                                        CityDotBar(
                                            cityCount = savedCities.size,
                                            currentIndex = currentCityIndex,
                                            isScrolled = isScrolled
                                        )
                                    }

                                    CompositionLocalProvider(
                                        LocalSkipCardAnimation provides (swiping || justEnteredCityDetail.value)
                                    ) {
                                        AnimatedContent(
                                            targetState = selectedCityId ?: "current_location",
                                            transitionSpec = {
                                                slideInHorizontally(tween(250)) { fullWidth -> fullWidth * swipeDirection } togetherWith
                                                    slideOutHorizontally(tween(250)) { fullWidth -> -fullWidth * swipeDirection }
                                            },
                                            label = "city_switch"
                                        ) { targetCityId ->
                                            val contentState = weatherStateForCityKey(
                                                cityKey = targetCityId,
                                                cities = savedCities,
                                                cityWeatherMap = cityWeatherMap,
                                                fallback = state
                                            )
                                            WeatherContentBody(
                                                state = contentState,
                                                scrollState = contentScrollState,
                                                onRefresh = { viewModel.refresh() },
                                                onAlertClick = { viewModel.navigateToAlertDetail(0) }
                                            )
                                        }
                                    }
                                }
                            }
                            is WeatherUiState.Error -> {
                                // No location permission - go back to onboarding
                                allPermissionsHandled = false
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

private fun weatherStateForCityKey(
    cityKey: String,
    cities: List<com.skypulse.weather.model.City>,
    cityWeatherMap: Map<String, CityWeatherData>,
    fallback: WeatherUiState.Success
): WeatherUiState.Success {
    val city = if (cityKey == "current_location") {
        cities.find { it.isCurrentLocation }
    } else {
        cities.find { it.id == cityKey }
    } ?: return fallback

    val weather = cityWeatherMap[city.id]?.weather ?: return fallback
    return WeatherUiState.Success(weather = weather, locationName = city.name)
}

// ==================== Helper Composables ====================

@Composable
private fun CityDotBar(
    cityCount: Int,
    currentIndex: Int,
    isScrolled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dividerAlpha = animateFloatAsState(
        targetValue = if (isScrolled) 0.35f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "dividerAlpha"
    )
    val dotsAlpha = animateFloatAsState(
        targetValue = if (isScrolled) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "dotsAlpha"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .align(Alignment.Center)
                .alpha(dividerAlpha.value)
        ) {
            drawLine(
                color = TextPrimary,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = size.height
            )
        }
        if (cityCount > 1) {
            val dotColor = TextPrimary.copy(alpha = 0.5f)
            val activeDotColor = TextPrimary
            val dotRadius = 2.5.dp
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 26.dp)
                    .alpha(dotsAlpha.value),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until cityCount) {
                    val isActive = i == currentIndex
                    Canvas(
                        modifier = Modifier
                            .size(dotRadius * 2)
                            .alpha(if (isActive) 1f else 0.5f)
                    ) {
                        drawCircle(
                            color = if (isActive) activeDotColor else dotColor,
                            radius = dotRadius.toPx()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherContentBody(
    state: WeatherUiState.Success,
    scrollState: ScrollState,
    onRefresh: () -> Unit = {},
    onAlertClick: (Int) -> Unit = {}
) {
    val result = state.weather.result
    val realtime = result?.realtime
    val todayTemp = WeatherUtils.todayTemperature(result?.daily)
    val alerts = result?.alert?.content?.mapNotNull { content ->
        val title = content.title
            ?.replace(Regex("\\[.*?\\]"), "")
            ?.replace(Regex("^.*(?:\u53D1\u5E03|\u53D8\u66F4|\u89E3\u9664|\u7EE7\u7EED|\u66F4\u65B0)"), "")
            ?.replace(Regex("\u9884\u8B66.*$"), "\u9884\u8B66")
            ?.trim()
        if (!title.isNullOrBlank()) AlertItem(title, content.level) else null
    }.orEmpty()

    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
    ) {
        if (alerts.isEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (alerts.isNotEmpty()) {
            AlertBanner(alerts = alerts, onClick = { idx ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onAlertClick(idx)
            })
        }

        CurrentWeather(
            realtime = realtime,
            todayHigh = todayTemp?.max,
            todayLow = todayTemp?.min,
            onRefresh = onRefresh
        )

        Spacer(modifier = Modifier.height(32.dp))

        result?.forecastKeypoint?.let { keypoint ->
            GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = keypoint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
        }

        HourlyForecastCard(
            hourly = result?.hourly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        DailyForecastCard(
            daily = result?.daily,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        WeatherDetailCards(
            realtime = realtime,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "\u6570\u636e\u6765\u6e90\uff1a\u5f69\u4e91\u5929\u6c14 \u00b7 \u5b9a\u4f4d\u670d\u52a1\uff1a\u9ad8\u5fb7\u5730\u56fe",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 22.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
