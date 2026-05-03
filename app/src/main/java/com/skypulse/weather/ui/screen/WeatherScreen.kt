package com.skypulse.weather.ui.screen

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.skypulse.weather.ui.components.*
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.viewmodel.WeatherUiState
import com.skypulse.weather.viewmodel.WeatherViewModel

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterialApi::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefreshing by viewModel.isManualRefreshing.collectAsState()
    val isLocating by viewModel.isLocating.collectAsState()
    val lastFetchTime by viewModel.lastFetchTime.collectAsState()
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Track when app went to background for auto-refresh
    var backgroundTimestamp by remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-refresh when returning from background if 10+ minutes have passed
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    backgroundTimestamp = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (backgroundTimestamp > 0L) {
                        val elapsed = System.currentTimeMillis() - backgroundTimestamp
                        val timeSinceLastFetch = System.currentTimeMillis() - lastFetchTime
                        if (elapsed >= 10 * 60 * 1000L || timeSinceLastFetch >= 10 * 60 * 1000L) {
                            viewModel.silentRefresh()
                        }
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

    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) {
            viewModel.fetchWeather()
        }
    }

    val skycon = when (val state = uiState) {
        is WeatherUiState.Success -> state.weather.result?.realtime?.skycon
        else -> null
    }

    WeatherBackground(skycon = skycon) {
        // Pull-to-refresh wrapper
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
                    val pullRefreshState = rememberPullRefreshState(
                        refreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullRefresh(pullRefreshState)
                    ) {
                        WeatherContent(
                            state = state,
                            isLocating = isLocating,
                            onLocationClick = { viewModel.relocateAndRefresh() }
                        )
                        CustomPullRefreshIndicator(
                            refreshing = isManualRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }

                is WeatherUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.fetchWeather() }
                    )
                }
            }

            // Permission request overlay
            if (!locationPermission.status.isGranted) {
                PermissionRequestContent(
                    shouldShowRationale = locationPermission.status.shouldShowRationale,
                    onRequestPermission = { locationPermission.launchPermissionRequest() },
                    onUseDefault = { viewModel.fetchWeather() }
                )
            }
        }
    }
}

@Composable
private fun WeatherContent(
    state: WeatherUiState.Success,
    isLocating: Boolean = false,
    onLocationClick: () -> Unit = {}
) {
    val result = state.weather.result
    val realtime = result?.realtime
    val todayTemp = result?.daily?.temperature?.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Current weather hero
        CurrentWeather(
            realtime = realtime,
            locationName = state.locationName,
            todayHigh = todayTemp?.max,
            todayLow = todayTemp?.min,
            isLocating = isLocating,
            onLocationClick = onLocationClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Forecast keypoint (weather summary) - moved above hourly
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

        // Hourly forecast with temperature curve
        HourlyForecastCard(
            hourly = result?.hourly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Daily forecast
        DailyForecastCard(
            daily = result?.daily,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
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
