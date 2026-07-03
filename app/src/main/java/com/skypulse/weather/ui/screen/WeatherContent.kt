package com.skypulse.weather.ui.screen

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.components.*
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.util.WeatherUtils
import com.skypulse.weather.viewmodel.RefreshPhase
import com.skypulse.weather.viewmodel.WeatherUiState

internal data class AlertItem(val title: String, val level: String?)

@Composable
internal fun WeatherContent(
    state: WeatherUiState.Success,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    cityCount: Int = 1,
    currentCityIndex: Int = 0,
    onRefresh: () -> Unit = {},
    onListClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAlertClick: (Int) -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {}
) {
    val result = state.weather.result
    val realtime = result?.realtime
    val todayTemp = WeatherUtils.todayTemperature(result?.daily)
    val alerts = result?.alert?.content?.mapNotNull { content ->
        val title = content.title
            ?.replace(Regex("\\[.*?\\]"), "")
            ?.replace(Regex("^.*(?:发布|变更|解除|继续|更新)"), "")
            ?.replace(Regex("预警.*$"), "预警")
            ?.trim()
        if (!title.isNullOrBlank()) AlertItem(title, content.level) else null
    }.orEmpty()

    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val isScrolled by remember {
        derivedStateOf { scrollState.value > 0 }
    }

    // Track Y ranges of hourly/daily cards to exclude from full-screen swipe
    // Using window coordinates consistently
    var hourlyYStart by remember { mutableFloatStateOf(-1f) }
    var hourlyYEnd by remember { mutableFloatStateOf(-1f) }
    var dailyYStart by remember { mutableFloatStateOf(-1f) }
    var dailyYEnd by remember { mutableFloatStateOf(-1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {},
                    onDragCancel = {},
                    onHorizontalDrag = { change, dragAmount ->
                        if (kotlin.math.abs(dragAmount) > 30f) {
                            change.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (dragAmount < 0f) onSwipeLeft()
                            else onSwipeRight()
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
            onListClick = onListClick,
            onSettingsClick = onSettingsClick
        )

        // City switch bar (divider when scrolled, dots when at top)
        CitySwitchBar(
            cityCount = cityCount,
            currentIndex = currentCityIndex,
            isScrolled = isScrolled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)
        )

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

            Spacer(modifier = Modifier.height(20.dp))

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
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        hourlyYStart = pos.y
                        hourlyYEnd = pos.y + coords.size.height
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DailyForecastCard(
                daily = result?.daily,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        dailyYStart = pos.y
                        dailyYEnd = pos.y + coords.size.height
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            WeatherDetailCards(
                realtime = realtime,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "\u6c14\u8c61\u6570\u636e\u6765\u81ea\u5f69\u4e91\u5929\u6c14",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp, bottom = 22.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
