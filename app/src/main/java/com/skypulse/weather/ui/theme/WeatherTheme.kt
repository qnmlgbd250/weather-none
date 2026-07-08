package com.skypulse.weather.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class WeatherTheme(
    val isDay: Boolean,
    val backgroundGradient: List<Color>,
    val cardTintColor: Color,
    val cardFrostColor: Color,
    val cardTopAlpha: Float,
    val cardMidAlpha: Float,
    val cardBottomAlpha: Float,
    val cardBorderBrush: Brush,
    val cardBorderColor: Color,
    val chartColors: WeatherChartColors,
    val precipitationIconColor: Color = Color.White,
    val textPrimary: Color = Color.White,
    val textSecondary: Color = TextSecondary,
    val textTertiary: Color = TextTertiary,
    val pressedOverlay: Color = PressedOverlay,
    val disabledOverlay: Color = DisabledOverlay
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
        cardTintColor = Color(0xFF1A3A5C),
        cardFrostColor = Color.White,
        cardTopAlpha = 0.28f,
        cardMidAlpha = 0.18f,
        cardBottomAlpha = 0.08f,
        cardBorderBrush = Brush.linearGradient(listOf(Color.White, Color.Transparent)),
        cardBorderColor = CardBorderDay,
        chartColors = WeatherChartColors(
            clear = Color(0xFFFFF8E1) to Color(0xFFFFD54F),
            partlyCloudy = Color(0xFFFFF8E1) to Color(0xFFFFECB3),
            cloudy = Color(0xFF9DB5D0) to Color(0xFF7A9ABB),
            rain = Color(0xFF5090E0) to Color(0xFF3570C0),
            snow = Color(0xFF80B8F0) to Color(0xFF5090E0),
            wind = Color(0xFF60C0D0) to Color(0xFF40A0B0),
            haze = Color(0xFFAA9A86) to Color(0xFF8A7A66),
            storm = Color(0xFF2A50A0) to Color(0xFF103070)
        )
    )
}
