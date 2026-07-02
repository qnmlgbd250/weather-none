package com.skypulse.weather.ui.screen

import androidx.compose.ui.graphics.Color
import com.skypulse.weather.ui.theme.AlertBlue
import com.skypulse.weather.ui.theme.AlertOrange
import com.skypulse.weather.ui.theme.AlertRed
import com.skypulse.weather.ui.theme.AlertYellow

internal fun alertLevelColor(
    level: String?,
    title: String? = null,
    fallback: Color = AlertYellow
): Color {
    val signal = listOfNotNull(level, title)
        .joinToString(separator = " ")
        .lowercase()

    return when {
        signal.contains("红") || signal.contains("red") -> AlertRed
        signal.contains("橙") || signal.contains("orange") -> AlertOrange
        signal.contains("黄") || signal.contains("yellow") -> AlertYellow
        signal.contains("蓝") || signal.contains("blue") -> AlertBlue
        signal.contains("白") || signal.contains("white") -> Color.Black
        else -> fallback
    }
}
