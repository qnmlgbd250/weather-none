package com.skypulse.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.DailyAstro
import com.skypulse.weather.ui.theme.LocalWeatherTheme
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale

@Composable
fun SunriseSunsetCard(
    astro: List<DailyAstro>?,
    modifier: Modifier = Modifier
) {
    val now = Calendar.getInstance()
    val todayStr = String.format(
        Locale.US, "%04d-%02d-%02d",
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH) + 1,
        now.get(Calendar.DAY_OF_MONTH)
    )

    val todayAstro = astro?.find { entry ->
        val d = entry.date ?: return@find false
        val datePart = if (d.contains("T")) d.substringBefore('T') else d
        datePart == todayStr
    } ?: astro?.firstOrNull()

    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
    val tomorrowStr = String.format(
        Locale.US, "%04d-%02d-%02d",
        tomorrow.get(Calendar.YEAR),
        tomorrow.get(Calendar.MONTH) + 1,
        tomorrow.get(Calendar.DAY_OF_MONTH)
    )
    val tomorrowAstro = astro?.find { entry ->
        val d = entry.date ?: return@find false
        val datePart = if (d.contains("T")) d.substringBefore('T') else d
        datePart == tomorrowStr
    } ?: astro?.getOrNull(1)

    val todaySunriseTime = todayAstro?.sunrise?.time ?: "06:00"
    val todaySunsetTime = todayAstro?.sunset?.time ?: "18:00"
    val tomorrowSunriseTime = tomorrowAstro?.sunrise?.time ?: "06:00"

    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    val todaySunriseParts = todaySunriseTime.split(":")
    val todaySunriseMinutes = todaySunriseParts.getOrNull(0)?.toIntOrNull() ?: 6
    val todaySunriseMin = todaySunriseParts.getOrNull(1)?.toIntOrNull() ?: 0
    val todaySunriseTotal = todaySunriseMinutes * 60 + todaySunriseMin

    val todaySunsetParts = todaySunsetTime.split(":")
    val todaySunsetMinutes = todaySunsetParts.getOrNull(0)?.toIntOrNull() ?: 18
    val todaySunsetMin = todaySunsetParts.getOrNull(1)?.toIntOrNull() ?: 0
    val todaySunsetTotal = todaySunsetMinutes * 60 + todaySunsetMin

    // 判断当前时间是否是白天 (日出之后 且 日落之前)
    val isDaytime = currentMinutes in todaySunriseTotal..todaySunsetTotal

    val cardState = if (isDaytime) {
        // 白天：显示今天的日出到日落，正常布局
        SunriseSunsetCardState(
            leftTime = todayAstro?.sunrise?.time ?: "--:--",
            rightTime = todayAstro?.sunset?.time ?: "--:--",
            leftLabel = "日出",
            rightLabel = "日落",
            showMoon = false,
            progress = calculateSunProgress(todaySunriseTime, todaySunsetTime)
        )
    } else {
        // 夜晚：显示日落到日出，位置互换
        val isBeforeSunrise = currentMinutes < todaySunriseTotal
        if (isBeforeSunrise) {
            // 午夜到日出之前：左边是昨天的日落（用今天日落近似），右边是今天的日出
            SunriseSunsetCardState(
                leftTime = todayAstro?.sunset?.time ?: "--:--",
                rightTime = todayAstro?.sunrise?.time ?: "--:--",
                leftLabel = "日落",
                rightLabel = "日出",
                showMoon = true,
                progress = calculateNightProgress(todaySunsetTime, todaySunriseTime)
            )
        } else {
            // 日落之后到午夜：左边是今天的日落，右边是明天的日出
            SunriseSunsetCardState(
                leftTime = todayAstro?.sunset?.time ?: "--:--",
                rightTime = tomorrowAstro?.sunrise?.time ?: "--:--",
                leftLabel = "日落",
                rightLabel = "日出",
                showMoon = true,
                progress = calculateNightProgress(todaySunsetTime, tomorrowSunriseTime)
            )
        }
    }

    android.util.Log.d("SunriseSunsetCard", "Card: sunrise=$todaySunriseTime, sunset=$todaySunsetTime, currentMinutes=$currentMinutes, isDaytime=$isDaytime, progress=${cardState.progress}, astroListSize=${astro?.size ?: 0}")

    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Top row: left label + icon, right label + icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (cardState.showMoon) Icons.Outlined.DarkMode else Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = cardState.leftLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = TextSecondary
                    )
                }
                // Right side
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = cardState.rightLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = TextSecondary
                    )
                    Icon(
                        imageVector = if (cardState.showMoon) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal progress bar with sun/moon indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                HorizontalSunProgress(
                    progress = cardState.progress,
                    showMoon = cardState.showMoon,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: left time + right time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = cardState.leftTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
                Text(
                    text = cardState.rightTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * 日出日落卡片状态
 */
private data class SunriseSunsetCardState(
    val leftTime: String,
    val rightTime: String,
    val leftLabel: String,
    val rightLabel: String,
    val showMoon: Boolean,
    val progress: Float
)

@Composable
private fun HorizontalSunProgress(
    progress: Float,
    showMoon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val sunPainter = rememberVectorPainter(image = Icons.Outlined.WbSunny)
    val moonPainter = rememberVectorPainter(image = Icons.Outlined.DarkMode)

    Canvas(modifier = modifier) {
        val barY = size.height / 2
        val barHeight = 3.dp.toPx()
        val cornerRadius = barHeight / 2
        val barWidth = size.width
        val indicatorX = barWidth * clampedProgress

        val gapRadius = 14.dp.toPx()
        val leftLineEnd = (indicatorX - gapRadius).coerceAtLeast(0f)
        val rightLineStart = (indicatorX + gapRadius).coerceAtMost(barWidth)

        // Before current time: dark (elapsed, already passed) - with gap
        if (leftLineEnd > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(0f, barY - barHeight / 2),
                size = Size(leftLineEnd, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        // After current time: bright (remaining) - with gap
        if (rightLineStart < barWidth) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(rightLineStart, barY - barHeight / 2),
                size = Size(barWidth - rightLineStart, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        // Vector Icon Size
        val iconSizePx = 18.dp.toPx()
        val iconOffset = Offset(indicatorX - iconSizePx / 2, barY - iconSizePx / 2)

        // Outer glow
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = iconSizePx * 0.7f,
            center = Offset(indicatorX, barY)
        )

        // Draw the vector icon (tilted or default)
        val painter = if (showMoon) moonPainter else sunPainter
        translate(left = iconOffset.x, top = iconOffset.y) {
            with(painter) {
                draw(
                    size = Size(iconSizePx, iconSizePx),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                )
            }
        }
    }
}

private fun calculateSunProgress(sunriseTime: String, sunsetTime: String): Float {
    return try {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val sunriseParts = sunriseTime.split(":")
        val sunriseMinutes = sunriseParts[0].toInt() * 60 + sunriseParts[1].toInt()

        val sunsetParts = sunsetTime.split(":")
        val sunsetMinutes = sunsetParts[0].toInt() * 60 + sunsetParts[1].toInt()

        val totalDaylight = sunsetMinutes - sunriseMinutes
        if (totalDaylight <= 0) return 0f

        val elapsed = currentMinutes - sunriseMinutes
        (elapsed.toFloat() / totalDaylight).coerceIn(0f, 1f)
    } catch (e: Exception) {
        0.5f
    }
}

/**
 * 计算夜间进度：从今天的日落到明天的日出
 * @param todaySunsetTime 今天的日落时间 (HH:mm)
 * @param tomorrowSunriseTime 明天的日出时间 (HH:mm)
 * @return 0.0 ~ 1.0 的进度值
 */
private fun calculateNightProgress(todaySunsetTime: String, tomorrowSunriseTime: String): Float {
    return try {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val sunsetParts = todaySunsetTime.split(":")
        val sunsetMinutes = sunsetParts[0].toInt() * 60 + sunsetParts[1].toInt()

        val sunriseParts = tomorrowSunriseTime.split(":")
        val sunriseMinutes = sunriseParts[0].toInt() * 60 + sunriseParts[1].toInt()

        // 夜晚总时长：从日落到第二天日出（跨天，需要加24小时）
        val totalNight = (24 * 60 - sunsetMinutes) + sunriseMinutes
        if (totalNight <= 0) return 0f

        // 已过时长：从日落到当前时间
        val elapsed = if (currentMinutes >= sunsetMinutes) {
            currentMinutes - sunsetMinutes
        } else {
            // 跨过午夜的情况
            (24 * 60 - sunsetMinutes) + currentMinutes
        }

        (elapsed.toFloat() / totalNight).coerceIn(0f, 1f)
    } catch (e: Exception) {
        0.5f
    }
}
