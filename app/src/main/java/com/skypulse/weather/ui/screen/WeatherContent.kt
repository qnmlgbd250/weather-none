package com.skypulse.weather.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.components.*
import com.skypulse.weather.ui.theme.TextSecondary
import com.skypulse.weather.viewmodel.RefreshPhase
import com.skypulse.weather.viewmodel.WeatherUiState

internal data class AlertItem(val title: String, val level: String?)

@Composable
internal fun WeatherContent(
    state: WeatherUiState.Success,
    isLocating: Boolean = false,
    refreshPhase: RefreshPhase = RefreshPhase.Idle,
    onLocationClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onListClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAlertClick: (Int) -> Unit = {}
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

    val haptic = LocalHapticFeedback.current
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
