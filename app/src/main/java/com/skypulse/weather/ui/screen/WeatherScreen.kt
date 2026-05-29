package com.skypulse.weather.ui.screen

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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
import com.skypulse.weather.viewmodel.UpdateCheckResult
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
    val updateState by viewModel.updateState.collectAsState()

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
                    onAddCity = { result -> viewModel.addCity(result) },
                    onRemoveCity = { cityId -> viewModel.removeCity(cityId) },
                    onSearch = { query -> viewModel.searchCities(query) },
                    onClearSearch = { viewModel.clearSearchResults() }
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
                                    onListClick = { viewModel.navigateToCityList() },
                                    onSettingsClick = { viewModel.navigateToSettings() }
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

            AppScreen.Settings -> {
                SettingsScreen(
                    onBack = { viewModel.navigateBack() },
                    onCheckUpdate = { viewModel.checkForUpdates() },
                    updateState = updateState,
                    onClearUpdateState = { viewModel.clearUpdateState() }
                )
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
    onListClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val result = state.weather.result
    val realtime = result?.realtime
    val todayTemp = result?.daily?.temperature?.firstOrNull()
    val alerts = result?.alert?.content?.mapNotNull { content ->
        val title = content.title
            ?.replace(Regex("\\[.*?\\]"), "")
            ?.replace(Regex("^.*发布"), "")
            ?.trim()
        if (!title.isNullOrBlank()) AlertItem(title, content.level) else null
    }.orEmpty()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Spacer(modifier = Modifier.height(12.dp))

        LocationHeader(
            locationName = state.locationName,
            isLocating = isLocating,
            refreshPhase = refreshPhase,
            onLocationClick = onLocationClick,
            onListClick = onListClick,
            onSettingsClick = onSettingsClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            if (alerts.isEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (alerts.isNotEmpty()) {
                AlertBanner(alerts = alerts)
            }

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

private data class AlertItem(val title: String, val level: String?)

@Composable
private fun AlertBanner(alerts: List<AlertItem>) {
    val alertColor = { level: String? ->
        when {
            level?.contains("红") == true -> Color(0xFFFF4444)
            level?.contains("橙") == true -> Color(0xFFFF8C00)
            level?.contains("黄") == true -> WarmGold
            level?.contains("蓝") == true -> Color(0xFF4488FF)
            else -> WarmGold
        }
    }

    val iconTint = if (alerts.size == 1) alertColor(alerts[0].level) else alertColor(alerts.first().level)
    val itemHeightDp = 20.dp

    val rawPainter = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Outlined.Notifications)
    val iconSizeDp = 14.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val iconSizePx = with(density) { iconSizeDp.toPx() }
    val croppedPainter = remember(rawPainter, iconSizePx) {
        object : androidx.compose.ui.graphics.painter.Painter() {
            override val intrinsicSize = androidx.compose.ui.geometry.Size(iconSizePx, iconSizePx)
            override fun DrawScope.onDraw() {
                val scale = iconSizePx / 20f
                val offsetPx = -2f * scale
                translate(left = offsetPx, top = offsetPx) {
                    with(rawPainter) { draw(androidx.compose.ui.geometry.Size(24f * scale, 24f * scale)) }
                }
            }
        }
    }

    Surface(
        onClick = {},
        modifier = Modifier.padding(start = 20.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 3.dp)
        ) {
            Image(
                painter = croppedPainter,
                contentDescription = "预警",
                modifier = Modifier.size(iconSizeDp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconTint)
            )
            Spacer(modifier = Modifier.width(4.dp))

            if (alerts.size == 1) {
                Text(
                    text = alerts[0].title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = alertColor(alerts[0].level)
                )
            } else {
                val scrollDensity = androidx.compose.ui.platform.LocalDensity.current
                val itemHeightPx = with(scrollDensity) { itemHeightDp.toPx() }
                val scrollOffset = remember { Animatable(0f) }
                var currentIndex by remember { mutableIntStateOf(0) }
                val listState = rememberLazyListState()

                LaunchedEffect(alerts) {
                    while (true) {
                        kotlinx.coroutines.delay(4000)
                        val nextIndex = if (currentIndex < alerts.size - 1) currentIndex + 1 else 0
                        scrollOffset.snapTo(0f)
                        scrollOffset.animateTo(
                            targetValue = itemHeightPx,
                            animationSpec = tween(durationMillis = 600, easing = EaseOut)
                        )
                        currentIndex = nextIndex
                        listState.scrollToItem(currentIndex)
                        scrollOffset.snapTo(0f)
                    }
                }

                Box(modifier = Modifier.height(itemHeightDp).clipToBounds()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.offset(
                            y = with(scrollDensity) { (-scrollOffset.value).toDp() }
                        ),
                        userScrollEnabled = false
                    ) {
                        items(alerts.size, key = { alerts[it].title }) { index ->
                            val alert = alerts[index]
                            Text(
                                text = alert.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = alertColor(alert.level),
                                modifier = Modifier.height(itemHeightDp)
                            )
                        }
                    }
                }
            }
        }
    }
}
