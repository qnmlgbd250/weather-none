package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.util.WeatherUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DailyForecastCard(
    daily: DailyForecast?,
    modifier: Modifier = Modifier,
    isSunnyDay: Boolean = false
) {
    if (daily?.temperature.isNullOrEmpty()) return
    val forecast = daily ?: return
    val temperatures = forecast.temperature ?: return

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400),
        label = "card_fade"
    )

    LaunchedEffect(Unit) { visible = true }

    GlassCard(
        modifier = modifier.alpha(alpha),
        isSunnyDay = isSunnyDay
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "多日预报",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Calculate global min/max for temperature range bar
            val allTemps = temperatures.flatMap { temp ->
                val values = mutableListOf<Double>()
                temp.max?.let { values.add(it) }
                temp.min?.let { values.add(it) }
                values
            }
            val globalMin = allTemps.minOrNull() ?: 0.0
            val globalMax = allTemps.maxOrNull() ?: 1.0
            val globalRange = (globalMax - globalMin).coerceAtLeast(1.0)

            temperatures.forEachIndexed { index, temp ->
                val skycon = forecast.skycon?.getOrNull(index)?.value

                DailyForecastItem(
                    dateStr = temp.date,
                    skycon = skycon,
                    maxTemp = temp.max,
                    minTemp = temp.min,
                    globalMin = globalMin,
                    globalRange = globalRange,
                    isFirst = index == 0,
                    isSunnyDay = isSunnyDay
                )

                if (index < temperatures.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyForecastItem(
    dateStr: String?,
    skycon: String?,
    maxTemp: Double?,
    minTemp: Double?,
    globalMin: Double,
    globalRange: Double,
    isFirst: Boolean,
    isSunnyDay: Boolean = false
) {
    val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
    val weekday = if (isFirst) "今天" else WeatherUtils.formatWeekday(dateStr)
    val dateLabel = formatMonthDay(dateStr)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day name + date
        Column(modifier = Modifier.width(48.dp)) {
            Text(
                text = weekday,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        // Weather icon - clipped to prevent animation overflow
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(28.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            WeatherIcon(
                iconType = weatherInfo.icon,
                size = 32.dp
            )
        }

        // Min temp
        Text(
            text = WeatherUtils.formatTemperature(minTemp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )

        // Temperature range bar
        TemperatureRangeBar(
            minTemp = minTemp,
            maxTemp = maxTemp,
            globalMin = globalMin,
            globalRange = globalRange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        // Max temp
        Text(
            text = WeatherUtils.formatTemperature(maxTemp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
private fun TemperatureRangeBar(
    minTemp: Double?,
    maxTemp: Double?,
    globalMin: Double,
    globalRange: Double,
    modifier: Modifier = Modifier
) {
    if (minTemp == null || maxTemp == null) return

    val startFraction = ((minTemp - globalMin) / globalRange).toFloat().coerceIn(0f, 1f)
    val endFraction = ((maxTemp - globalMin) / globalRange).toFloat().coerceIn(0f, 1f)

    val avgTemp = (minTemp + maxTemp) / 2
    val barColor = when {
        avgTemp < 0 -> Color(0xFF90CAF9)
        avgTemp < 10 -> Color(0xFF64B5F6)
        avgTemp < 20 -> Color(0xFF4FC3F7)
        avgTemp < 30 -> Color(0xFFFFD54F)
        avgTemp < 35 -> Color(0xFFFFB74D)
        else -> Color(0xFFEF5350)
    }

    Canvas(
        modifier = modifier.height(8.dp)
    ) {
        val barHeight = size.height
        val barWidth = size.width
        val cornerRadius = barHeight / 2

        // Background track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.1f),
            cornerRadius = CornerRadius(cornerRadius),
            size = Size(barWidth, barHeight)
        )

        // Active range
        val activeLeft = startFraction * barWidth
        val activeWidth = (endFraction - startFraction).coerceAtLeast(0.02f) * barWidth

        drawRoundRect(
            color = barColor,
            topLeft = Offset(activeLeft, 0f),
            size = Size(activeWidth, barHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )
    }
}

private fun formatMonthDay(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(dateStr) ?: return ""
        val cal = Calendar.getInstance()
        cal.time = date
        "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    } catch (e: Exception) {
        ""
    }
}
