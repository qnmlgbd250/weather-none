package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.ui.screen.LocalSkipCardAnimation
import com.skypulse.weather.ui.theme.TextTertiary
import com.skypulse.weather.util.WeatherUtils

private const val DAY_WIDTH = 64

@Composable
fun DailyForecastCard(
    daily: DailyForecast?,
    isPremium: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (daily?.temperature.isNullOrEmpty()) return
    val forecast = daily ?: return
    val allTemperatures = forecast.temperature ?: return
    
    // Free users: limit to 5 days (including yesterday)
    val startIndex = if (isPremium) 0 else {
        val yesterdayIndex = allTemperatures.indexOfFirst { WeatherUtils.isYesterday(it.date) }
        if (yesterdayIndex >= 0) yesterdayIndex else 0
    }
    val endIndex = if (isPremium) allTemperatures.size else {
        minOf(startIndex + 5, allTemperatures.size)
    }
    val temperatures = allTemperatures.subList(startIndex, endIndex)

    val skipAnimation = LocalSkipCardAnimation.current
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (skipAnimation || visible) 1f else 0f,
        animationSpec = if (skipAnimation) tween(0) else tween(
            SkyPulseDesignSystem.Motion.cardEnterMillis,
            delayMillis = SkyPulseDesignSystem.Motion.cardEnterDelayMillis
        ),
        label = "card_fade"
    )
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(skipAnimation) { if (skipAnimation) visible = true }

    val allTemps = temperatures.flatMap { t -> listOfNotNull(t.max, t.min) }
    val globalMin = allTemps.minOrNull() ?: 0.0
    val globalMax = allTemps.maxOrNull() ?: 1.0

    val itemWidth = DAY_WIDTH.dp
    val horizontalEdgeGuard = rememberHorizontalScrollEdgeGuard()

    GlassCard(
        modifier = modifier.alpha(cardAlpha)
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Text(
                text = "多日预报",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(horizontalEdgeGuard)
                    .disableCityPagerWhilePressed()
                    .horizontalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.width(12.dp))

                temperatures.forEachIndexed { index, temp ->
                    val originalIndex = startIndex + index
                    val skycon = forecast.skycon?.getOrNull(originalIndex)?.value
                    val isPast = WeatherUtils.isYesterday(temp.date)
                    DailyColumn(
                        dateStr = temp.date,
                        skycon = skycon,
                        precipProb = forecast.precipitation?.getOrNull(originalIndex)?.probability,
                        maxTemp = temp.max,
                        minTemp = temp.min,
                        globalMin = globalMin,
                        globalMax = globalMax,
                        isPast = isPast,
                        itemWidth = itemWidth
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun DailyColumn(
    dateStr: String?,
    skycon: String?,
    precipProb: Double?,
    maxTemp: Double?,
    minTemp: Double?,
    globalMin: Double,
    globalMax: Double,
    isPast: Boolean,
    itemWidth: Dp
) {
    val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
    val weekday = when {
        WeatherUtils.isYesterday(dateStr) -> "昨天"
        WeatherUtils.isToday(dateStr) -> "今天"
        WeatherUtils.isTomorrow(dateStr) -> "明天"
        else -> WeatherUtils.formatWeekday(dateStr)
    }

    val dateLabel = formatShortDate(dateStr)

    Column(
        modifier = Modifier
            .width(itemWidth)
            .then(if (isPast) Modifier.alpha(0.52f).blur(0.45.dp) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Date + Weekday ---
        Text(
            text = weekday,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
            color = TextPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- Weather icon ---
        Box(
            modifier = Modifier
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            WeatherIcon(iconType = weatherInfo.icon, size = 36.dp, animated = false)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // --- Weather description ---
        Text(
            text = weatherInfo.description,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        // --- Precipitation probability (fixed height) ---
        Box(
            modifier = Modifier.height(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (precipProb != null && precipProb >= 1.0) {
                Text(
                    text = "${precipProb.toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextTertiary,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- Max temp ---
        TempText(
            value = maxTemp,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 15.sp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // --- Vertical temperature bar ---
        VerticalTempBar(
            maxTemp = maxTemp,
            minTemp = minTemp,
            globalMin = globalMin,
            globalMax = globalMax,
            modifier = Modifier
                .width(4.dp)
                .height(50.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // --- Min temp ---
        TempText(
            value = minTemp,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 15.sp)
        )
    }
}

@Composable
private fun TempText(
    value: Double?,
    color: Color,
    style: TextStyle
) {
    if (value == null) return
    val tempStr = WeatherUtils.formatTemperature(value).replace("°", "")

    Layout(
        content = {
            Text(text = tempStr, style = style, color = color)
            Text(
                text = "°",
                style = style.copy(fontSize = style.fontSize * 0.7f),
                color = color.copy(alpha = 0.7f)
            )
        }
    ) { measurables, constraints ->
        val tempPlaceable = measurables[0].measure(constraints)
        val degPlaceable = measurables[1].measure(constraints)

        // Only report the width of the temperature digits.
        // The degree sign will be drawn to the right but won't shift the center point.
        layout(tempPlaceable.width, maxOf(tempPlaceable.height, degPlaceable.height)) {
            tempPlaceable.placeRelative(0, 0)
            degPlaceable.placeRelative(tempPlaceable.width, 0)
        }
    }
}

@Composable
private fun VerticalTempBar(
    maxTemp: Double?,
    minTemp: Double?,
    globalMin: Double,
    globalMax: Double,
    modifier: Modifier = Modifier
) {
    if (maxTemp == null || minTemp == null) return
    val globalRange = (globalMax - globalMin).coerceAtLeast(1.0)

    Canvas(modifier = modifier) {
        val barW = size.width
        val barH = size.height
        val corner = barW / 2f

        // Background track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            cornerRadius = CornerRadius(corner),
            size = Size(barW, barH)
        )

        // Position within range: top = hottest, bottom = coldest
        val topFraction = (1f - ((maxTemp - globalMin) / globalRange)).toFloat().coerceIn(0f, 1f)
        val bottomFraction = (1f - ((minTemp - globalMin) / globalRange)).toFloat().coerceIn(0f, 1f)

        val activeTop = topFraction * barH
        val activeHeight = (bottomFraction - topFraction).coerceAtLeast(0.04f) * barH

        // Smooth color gradient based on temperature
        val topColor = WeatherUtils.getTemperatureColor(maxTemp)
        val bottomColor = WeatherUtils.getTemperatureColor(minTemp)

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(topColor, bottomColor),
                startY = activeTop,
                endY = activeTop + activeHeight
            ),
            topLeft = Offset(0f, activeTop),
            size = Size(barW, activeHeight),
            cornerRadius = CornerRadius(corner)
        )
    }
}

private val shortDateFmt: java.lang.ThreadLocal<java.text.SimpleDateFormat> = java.lang.ThreadLocal.withInitial {
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
}

private fun formatShortDate(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        val date = shortDateFmt.get()!!.parse(dateStr) ?: return ""
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        "${cal.get(java.util.Calendar.MONTH) + 1}月${cal.get(java.util.Calendar.DAY_OF_MONTH)}日"
    } catch (_: Exception) { "" }
}