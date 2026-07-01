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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.DailyAstro
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import java.util.Calendar
import java.util.Locale

@Composable
fun SunriseSunsetCard(
    astro: List<DailyAstro>?,
    modifier: Modifier = Modifier
) {
    val todayAstro = remember(astro) {
        val todayStr = String.format(
            Locale.US, "%04d-%02d-%02d",
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH) + 1,
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        )
        astro?.find { entry ->
            val d = entry.date ?: return@find false
            // API date format: "2026-07-01T00:00+08:00" — extract the date part before 'T'
            val datePart = if (d.contains("T")) d.substringBefore('T') else d
            datePart == todayStr
        }
    }

    val sunriseTime = todayAstro?.sunrise?.time ?: "--:--"
    val sunsetTime = todayAstro?.sunset?.time ?: "--:--"

    val progress = remember(sunriseTime, sunsetTime) {
        calculateSunProgress(sunriseTime, sunsetTime)
    }

    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Top row: sunrise icon + label on left, sunset icon + label on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sunrise: sun icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WbSunny,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "日出",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                // Sunset: moon icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "日落",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Icon(
                        imageVector = Icons.Outlined.DarkMode,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal progress bar with sun indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            ) {
                HorizontalSunProgress(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: sunrise time + sunset time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sunriseTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
                Text(
                    text = sunsetTime,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun HorizontalSunProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val barY = size.height / 2
        val barHeight = 3.dp.toPx()
        val cornerRadius = barHeight / 2
        val barWidth = size.width
        val sunX = barWidth * clampedProgress

        // Before current time: dark (elapsed daylight, already passed)
        if (clampedProgress > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(0f, barY - barHeight / 2),
                size = Size(sunX, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        // After current time: bright (remaining daylight)
        if (clampedProgress < 1f) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(sunX, barY - barHeight / 2),
                size = Size(barWidth - sunX, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )
        }

        // Sun indicator on the progress bar
        val sunRadius = 5.dp.toPx()

        // Outer glow
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = sunRadius * 2.2f,
            center = Offset(sunX, barY)
        )

        // Sun body
        drawCircle(
            color = Color.White,
            radius = sunRadius,
            center = Offset(sunX, barY)
        )

        // Short rays
        val rayInner = sunRadius + 1.5.dp.toPx()
        val rayOuter = sunRadius + 4.dp.toPx()
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45.0))
            drawLine(
                color = Color.White,
                start = Offset(
                    sunX + (rayInner * kotlin.math.cos(angle)).toFloat(),
                    barY + (rayInner * kotlin.math.sin(angle)).toFloat()
                ),
                end = Offset(
                    sunX + (rayOuter * kotlin.math.cos(angle)).toFloat(),
                    barY + (rayOuter * kotlin.math.sin(angle)).toFloat()
                ),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
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
