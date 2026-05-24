package com.skypulse.weather.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class WeatherTheme(
    val isDay: Boolean,
    val backgroundGradient: List<Color>,
    val cardTopAlpha: Float,
    val cardBottomAlpha: Float,
    val cardBorderBrush: Brush,
    val chartColors: WeatherChartColors,
    val textPrimary: Color = Color.White,
    val textSecondary: Color = Color(0xD9FFFFFF)
)

@Immutable
data class WeatherChartColors(
    val clear: Pair<Color, Color>,
    val partlyCloudy: Pair<Color, Color>,
    val cloudy: Pair<Color, Color>,
    val rain: Pair<Color, Color>,
    val snow: Pair<Color, Color>,
    val wind: Pair<Color, Color>,
    val haze: Pair<Color, Color>,
    val storm: Pair<Color, Color>
)

val LocalWeatherTheme = staticCompositionLocalOf {
    WeatherTheme(
        isDay = true,
        backgroundGradient = SunnyGradient,
        cardTopAlpha = 0.28f,
        cardBottomAlpha = 0.12f,
        cardBorderBrush = Brush.linearGradient(listOf(Color.White, Color.Transparent)),
        chartColors = WeatherChartColors(
            clear = Color(0xFFFFFFF0) to Color(0xFFFFF9C4),
            partlyCloudy = Color(0xFFFFF8E1) to Color(0xFFFFECB3),
            cloudy = Color(0xFF8AA4C4) to Color(0xFF6A8AAA),
            rain = Color(0xFF467CD6) to Color(0xFF2E5AAC),
            snow = Color(0xFF6FA0E8) to Color(0xFF467CD6),
            wind = Color(0xFF5AACB8) to Color(0xFF3A8A98),
            haze = Color(0xFF9A8A76) to Color(0xFF7A6A56),
            storm = Color(0xFF1A3A7A) to Color(0xFF0D1F4A)
        )
    )
}
