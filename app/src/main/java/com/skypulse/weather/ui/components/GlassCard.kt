package com.skypulse.weather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skypulse.weather.ui.theme.WeatherTheme
import com.skypulse.weather.ui.theme.LocalWeatherTheme
import com.skypulse.weather.ui.theme.SunnyGradient
import com.skypulse.weather.ui.theme.PartialCloudGradient

fun GlassCardBg(theme: WeatherTheme): Color {
    val alpha = if (theme.isDay) {
        when (theme.backgroundGradient.first()) {
            SunnyGradient.first() -> 0.15f
            PartialCloudGradient.first() -> 0.13f
            else -> 0.10f
        }
    } else 0.08f
    return Color.White.copy(alpha = alpha)
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalWeatherTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(theme.cardTintColor),
        content = content
    )
}