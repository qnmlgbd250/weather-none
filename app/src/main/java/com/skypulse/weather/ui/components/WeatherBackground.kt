package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
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

    val infiniteTransition = rememberInfiniteTransition(label = "bg")

    // Animate gradient shift
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_shift"
    )

    // Interpolate colors
    val color1 = animateColor(targetColors.getOrElse(0) { Color(0xFF1976D2) }, animProgress)
    val color2 = animateColor(targetColors.getOrElse(1) { Color(0xFF64B5F6) }, animProgress)

    val rainIntensity = remember(skycon) {
        when {
            skycon == null -> 0f
            skycon.contains("STORM_RAIN") -> 1.0f
            skycon.contains("HEAVY_RAIN") -> 0.75f
            skycon.contains("MODERATE_RAIN") -> 0.5f
            skycon.contains("RAIN") -> 0.25f
            else -> 0f
        }
    }

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
        if (rainIntensity > 0f) {
            RainEffect(
                intensity = rainIntensity,
                modifier = Modifier.fillMaxSize()
            )
        }
        content()
    }
}

private fun animateColor(base: Color, progress: Float): Color {
    // Subtle brightness shift
    val brightnessShift = sin(progress * Math.PI).toFloat() * 0.05f
    return Color(
        red = (base.red + brightnessShift).coerceIn(0f, 1f),
        green = (base.green + brightnessShift).coerceIn(0f, 1f),
        blue = (base.blue + brightnessShift).coerceIn(0f, 1f),
        alpha = base.alpha
    )
}

private fun sin(value: Double): Float = kotlin.math.sin(value).toFloat()
