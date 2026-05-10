package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.skypulse.weather.util.WeatherUtils

@Composable
fun WeatherBackground(
    skycon: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDay = WeatherUtils.isCurrentlyDay()
    val targetColors = WeatherUtils.getWeatherGradient(skycon, isDay)

    val color1 = targetColors.getOrElse(0) { Color(0xFF1976D2) }
    val color2 = targetColors.getOrElse(1) { Color(0xFF64B5F6) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(color1, color2),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        content()
    }
}
