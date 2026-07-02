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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    // 解析天象数据，判断是否已过今天的日落时间
    val cardState = remember(astro) {
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
        }

        val todaySunsetTime = todayAstro?.sunset?.time
        val isAfterSunset = if (todaySunsetTime != null && todaySunsetTime.contains(":")) {
            val parts = todaySunsetTime.split(":")
            val sunsetMinutes = parts[0].toInt() * 60 + parts[1].toInt()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            currentMinutes > sunsetMinutes
        } else {
            false
        }

        if (isAfterSunset) {
            // 已过日落：显示明天的日出日落，位置互换
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
            }

            SunriseSunsetCardState(
                leftTime = tomorrowAstro?.sunset?.time ?: "--:--",
                rightTime = tomorrowAstro?.sunrise?.time ?: "--:--",
                leftLabel = "日落",
                rightLabel = "日出",
                showMoon = true,
                progress = calculateNightProgress(
                    todaySunsetTime ?: "18:00",
                    tomorrowAstro?.sunrise?.time ?: "06:00"
                )
            )
        } else {
            // 日落前：显示今天的日出日落，正常布局
            SunriseSunsetCardState(
                leftTime = todayAstro?.sunrise?.time ?: "--:--",
                rightTime = todayAstro?.sunset?.time ?: "--:--",
                leftLabel = "日出",
                rightLabel = "日落",
                showMoon = false,
                progress = calculateSunProgress(
                    todayAstro?.sunrise?.time ?: "06:00",
                    todayAstro?.sunset?.time ?: "18:00"
                )
            )
        }
    }

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
                        style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.bodySmall,
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
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
                Text(
                    text = cardState.rightTime,
                    style = MaterialTheme.typography.titleMedium.copy(
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
    val theme = LocalWeatherTheme.current
    val moonCutoutColor = if (theme.isDay) {
        Color(0xFF34577A)
    } else {
        theme.backgroundGradient.firstOrNull() ?: Color(0xFF1D2842)
    }

    Canvas(modifier = modifier) {
        val barY = size.height / 2
        val barHeight = 3.dp.toPx()
        val cornerRadius = barHeight / 2
        val barWidth = size.width
        val indicatorX = barWidth * clampedProgress

        val gapRadius = if (showMoon) 15.dp.toPx() else 14.dp.toPx()
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

        // Indicator (sun or moon)
        val indicatorRadius = if (showMoon) 6.dp.toPx() else 5.dp.toPx()

        // Outer glow
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = indicatorRadius * 2.2f,
            center = Offset(indicatorX, barY)
        )

        if (showMoon) {
            // Moon: use a solid cutout color so the crescent remains readable on glass backgrounds.
            drawCircle(
                color = Color.White,
                radius = indicatorRadius,
                center = Offset(indicatorX, barY)
            )
            drawCircle(
                color = moonCutoutColor.copy(alpha = 0.92f),
                radius = indicatorRadius * 0.88f,
                center = Offset(indicatorX + indicatorRadius * 0.42f, barY - indicatorRadius * 0.12f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = 1.dp.toPx(),
                center = Offset(
                    (indicatorX - 13.dp.toPx()).coerceAtLeast(1.dp.toPx()),
                    barY - 6.dp.toPx()
                )
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = 0.8.dp.toPx(),
                center = Offset(
                    (indicatorX + 13.dp.toPx()).coerceAtMost(size.width - 1.dp.toPx()),
                    barY + 6.dp.toPx()
                )
            )
        } else {
            // Sun body
            drawCircle(
                color = Color.White,
                radius = indicatorRadius,
                center = Offset(indicatorX, barY)
            )

            // Short rays
            val rayInner = indicatorRadius + 1.5.dp.toPx()
            val rayOuter = indicatorRadius + 4.dp.toPx()
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45.0))
                drawLine(
                    color = Color.White,
                    start = Offset(
                        indicatorX + (rayInner * kotlin.math.cos(angle)).toFloat(),
                        barY + (rayInner * kotlin.math.sin(angle)).toFloat()
                    ),
                    end = Offset(
                        indicatorX + (rayOuter * kotlin.math.cos(angle)).toFloat(),
                        barY + (rayOuter * kotlin.math.sin(angle)).toFloat()
                    ),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
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
