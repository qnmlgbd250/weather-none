package com.skypulse.weather.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.skypulse.weather.data.WeatherSettings
import com.skypulse.weather.domain.CitySelectionPolicy
import com.skypulse.weather.util.WeatherUtils
import com.skypulse.weather.ui.components.*
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.viewmodel.AppScreen
import com.skypulse.weather.viewmodel.CitySearchViewModel
import com.skypulse.weather.viewmodel.RefreshPhase
import com.skypulse.weather.viewmodel.SettingsViewModel
import com.skypulse.weather.viewmodel.WeatherUiState
import com.skypulse.weather.viewmodel.WeatherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalSkipCardAnimation = compositionLocalOf { false }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = hiltViewModel(),
    searchViewModel: CitySearchViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshPhase by viewModel.refreshPhase.collectAsStateWithLifecycle()
    val isLocating by viewModel.isLocating.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val savedCities by viewModel.savedCities.collectAsStateWithLifecycle()
    val cityWeatherMap by viewModel.cityWeatherMap.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by searchViewModel.isSearching.collectAsStateWithLifecycle()
    val isSearchActive by searchViewModel.isSearchActive.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val selectedAlertIndex by viewModel.selectedAlertIndex.collectAsStateWithLifecycle()
    val selectedCityId by viewModel.selectedCityId.collectAsStateWithLifecycle()
    val onboardingReady by viewModel.onboardingReady.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val currentCityIndex by remember {
        derivedStateOf {
            CitySelectionPolicy.currentIndex(savedCities, selectedCityId)
        }
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

    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
    var allPermissionsHandled by rememberSaveable { mutableStateOf(false) }
    var locationSkipped by rememberSaveable { mutableStateOf(false) }
    var homeBootstrapStarted by rememberSaveable { mutableStateOf(false) }

    var backgroundTimestamp by remember { mutableLongStateOf(0L) }
    var skipLifecycleCardAnimation by remember { mutableStateOf(false) }
    val lifecycleAnimationScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> backgroundTimestamp = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    if (backgroundTimestamp > 0L) {
                        skipLifecycleCardAnimation = true
                        lifecycleAnimationScope.launch {
                            delay(SkyPulseDesignSystem.Motion.lifecycleSkipMillis)
                            skipLifecycleCardAnimation = false
                        }
                        viewModel.onResume()
                        backgroundTimestamp = 0L
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(onboardingReady, allPermissionsHandled, locationSkipped) {
        if (!onboardingReady || homeBootstrapStarted || locationSkipped) return@LaunchedEffect
        if (showOnboarding) {
            if (!allPermissionsHandled) return@LaunchedEffect
            viewModel.completeOnboarding()
        }
        homeBootstrapStarted = true
        viewModel.ensureCurrentLocationCitySync()
        viewModel.fetchWeather()
    }

    LaunchedEffect(locationSkipped, savedCities) {
        if (locationSkipped && savedCities.isEmpty()) {
            viewModel.navigateToCityList()
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
        searchViewModel.clearSearchResults()
        viewModel.navigateBack()
    }

    if (!onboardingReady) {
        LoadingShimmer(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        )
        return
    }

    if (showOnboarding && !allPermissionsHandled && !locationSkipped) {
        PermissionOnboardingScreen(
            onFinished = { allPermissionsHandled = true },
            onSkip = {
                locationSkipped = true
                viewModel.completeOnboarding()
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
                    isSearchActive = isSearchActive,
                    onCityClick = { cityId -> viewModel.navigateToCityDetail(cityId) },
                    onAddCity = { result -> viewModel.addCity(result.name, result.longitude, result.latitude) },
                    onRemoveCity = { cityId -> viewModel.removeCity(cityId) },
                    onSearch = { query -> searchViewModel.searchCities(query) },
                    onClearSearch = { searchViewModel.clearSearchResults() },
                    onBack = {
                        searchViewModel.clearSearchResults()
                        viewModel.navigateBack()
                    }
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
                                val pagerState = rememberPagerState(
                                    initialPage = currentCityIndex,
                                    pageCount = { savedCities.size }
                                )

                                // Sync from ViewModel selection to PagerState
                                LaunchedEffect(selectedCityId, savedCities) {
                                    val targetIndex = savedCities.indexOfFirst { it.id == selectedCityId }
                                    if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                                        pagerState.scrollToPage(targetIndex)
                                    }
                                }

                                // Sync from PagerState swiping to ViewModel
                                LaunchedEffect(pagerState.currentPage) {
                                    if (pagerState.currentPage < savedCities.size) {
                                        val targetCity = savedCities[pagerState.currentPage]
                                        if (targetCity.id != selectedCityId) {
                                            viewModel.navigateToCityDetail(targetCity.id)
                                        }
                                    }
                                }

                                val scrollStates = remember { mutableStateMapOf<String, ScrollState>() }
                                val activeCityId = savedCities.getOrNull(pagerState.currentPage)?.id ?: "current_location"
                                val activeScrollState = scrollStates[activeCityId]
                                val isScrolled by remember(activeScrollState) {
                                    derivedStateOf { (activeScrollState?.value ?: 0) > 0 }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                ) {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    LocationHeader(
                                        locationName = state.locationName,
                                        isLocating = isLocating,
                                        refreshPhase = refreshPhase,
                                        onListClick = { viewModel.navigateToCityList() },
                                        onSettingsClick = { viewModel.navigateToSettings() }
                                    )

                                    CityDotBar(
                                        cityCount = savedCities.size,
                                        currentIndex = pagerState.currentPage,
                                        isScrolled = isScrolled
                                    )

                                    CompositionLocalProvider(
                                        LocalSkipCardAnimation provides (
                                            pagerState.isScrollInProgress ||
                                                justEnteredCityDetail.value ||
                                                skipLifecycleCardAnimation
                                            )
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            key = { page -> savedCities.getOrNull(page)?.id ?: page.toString() }
                                        ) { page ->
                                            val city = savedCities.getOrNull(page)
                                            val contentState = remember(city, cityWeatherMap, state.locationName) {
                                                val weather = city?.let { cityWeatherMap[it.id]?.weather }
                                                if (city != null && weather != null) {
                                                    WeatherUiState.Success(
                                                        weather = weather,
                                                        locationName = if (city.isCurrentLocation) state.locationName else city.name
                                                    )
                                                } else {
                                                    null
                                                }
                                            }
                                            val pageScrollState = scrollStates.getOrPut(city?.id ?: "current_location") { ScrollState(0) }
                                            if (contentState != null) {
                                                WeatherContentBody(
                                                    state = contentState,
                                                    scrollState = pageScrollState,
                                                    settings = settings,
                                                    onRefresh = { viewModel.refresh() },
                                                    onAlertClick = { viewModel.navigateToAlertDetail(0) }
                                                )
                                            } else {
                                                LoadingShimmer(modifier = Modifier.fillMaxSize())
                                            }
                                        }
                                    }
                                }
                            }
                            is WeatherUiState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    LocationHeader(
                                        locationName = "加载失败",
                                        isLocating = false,
                                        refreshPhase = RefreshPhase.Idle,
                                        onListClick = { viewModel.navigateToCityList() },
                                        onSettingsClick = { viewModel.navigateToSettings() }
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.fetchWeather() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("重试定位")
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                }
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
                    onClearUpdateState = { viewModel.clearUpdateState() },
                    settings = settings,
                    onRainAlertChange = { settingsViewModel.setRainAlert(it) },
                    onWarningAlertChange = { settingsViewModel.setWarningAlert(it) },
                    onTempChangeAlertChange = { settingsViewModel.setTempChangeAlert(it) },
                    onWindAlertChange = { settingsViewModel.setWindAlert(it) },
                    onTyphoonAlertChange = { settingsViewModel.setTyphoonAlert(it) },
                    onShowHourlyAqiChange = { settingsViewModel.setShowHourlyAqi(it) },
                    onShowHourlyUvChange = { settingsViewModel.setShowHourlyUv(it) },
                    onShowHourlyWindChange = { settingsViewModel.setShowHourlyWind(it) },
                    onShowHourlyWindGustChange = { settingsViewModel.setShowHourlyWindGust(it) },
                    onShowCardDetailChange = { settingsViewModel.setShowCardDetail(it) },
                    onShowCardSunriseSunsetChange = { settingsViewModel.setShowCardSunriseSunset(it) }
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
                .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
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
    settings: WeatherSettings,
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
            GlassCard(modifier = Modifier.padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)) {
                Text(
                    text = keypoint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
        }

        val minutelyData = result?.minutely?.precipitation_2h
        val showMinutely = !minutelyData.isNullOrEmpty() && minutelyData.any { it != 0.0 }

        if (showMinutely) {
            MinutelyPrecipitationCard(
                minutely = result?.minutely,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            )
            Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
        }

        HourlyForecastCard(
            hourly = result?.hourly,
            showAqi = settings.showHourlyAqi,
            showUv = settings.showHourlyUv,
            showWind = settings.showHourlyWind,
            showWindGust = settings.showHourlyWindGust,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
        )

        Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))

        DailyForecastCard(
            daily = result?.daily,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
        )

        Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))

        if (settings.showCardDetail) {
            WeatherDetailCards(
                realtime = realtime,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
        }

        if (settings.showCardSunriseSunset) {
            SunriseSunsetCard(
                astro = result?.daily?.astro,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            )
        }

        Text(
            text = "\u6c14\u8c61\u6570\u636e\u6765\u81ea\u5f69\u4e91\u5929\u6c14",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 22.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SkyPulseDesignSystem.Spacing.sectionGap))
    }
}
