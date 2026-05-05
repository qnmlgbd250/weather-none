package com.skypulse.weather.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.skypulse.weather.model.MinutelyForecast
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary

private const val BAR_COUNT = 48
private const val BAR_WIDTH_DP = 3f
private const val BAR_GAP_DP = 3f
private const val CHART_HEIGHT_DP = 60f

@Composable
fun MinutelyPrecipitationCard(
    minutely: MinutelyForecast?,
    modifier: Modifier = Modifier,
    isSunnyDay: Boolean = false
) {
    val raw = minutely?.precipitation_2h
    if (raw.isNullOrEmpty()) return
    if (raw.all { it == 0.0 }) return

    // Sample 120 entries down to 48 bins
    val sampled = remember(raw) { sampleToBins(raw, BAR_COUNT) }
    if (sampled.all { it == 0.0 }) return

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 200),
        label = "minutely_fade"
    )
    LaunchedEffect(Unit) { visible = true }

    GlassCard(modifier = modifier.alpha(alpha), isSunnyDay = isSunnyDay) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = "分钟级降水",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            MinutelyBarChart(
                data = sampled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun MinutelyBarChart(
    data: List<Double>,
    modifier: Modifier = Modifier
) {
    // Fixed scale: 2.5 mm/h = moderate rain = 100% fill
    val maxVal = 2.5

    val barWidthDp = BAR_WIDTH_DP.dp
    val barGapDp = BAR_GAP_DP.dp
    val chartHeightDp = CHART_HEIGHT_DP.dp

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeightDp)
        ) {
            val barCount = data.size
            val barW = barWidthDp.toPx()
            val gapW = barGapDp.toPx()
            val step = barW + gapW
            val chartW = size.width
            val chartH = size.height
            val corner = CornerRadius(barW / 2f)

            val totalW = barCount * barW + (barCount - 1) * gapW
            val startX = ((chartW - totalW) / 2f).coerceAtLeast(0f)

            val grayFrame = Color.White.copy(alpha = 0.12f)

            for (i in 0 until barCount) {
                val value = data[i]
                val fillRatio = (value / maxVal).toFloat().coerceIn(0f, 1f)
                val left = startX + i * step
                val fillH = chartH * fillRatio
                val fillTop = chartH - fillH

                // Gray frame (full height, always visible)
                drawRoundRect(
                    color = grayFrame,
                    topLeft = Offset(left, 0f),
                    size = Size(barW, chartH),
                    cornerRadius = corner
                )

                // Shadow + blue fill for bars with precipitation
                if (fillRatio > 0f) {
                    val shadowAlpha = (0.15f + 0.25f * fillRatio).coerceIn(0.15f, 0.4f)
                    val shadowColor = Color(0xFF92DDFE).copy(alpha = shadowAlpha)
                    for (s in 1..3) {
                        val expand = s * 0.8f
                        drawRoundRect(
                            color = shadowColor,
                            topLeft = Offset(left - expand, fillTop - expand * 0.5f),
                            size = Size(barW + expand * 2, fillH + expand),
                            cornerRadius = CornerRadius(barW / 2f + expand)
                        )
                    }

                    val fillAlpha = (0.4f + 0.6f * fillRatio).coerceIn(0.4f, 1f)
                    drawRoundRect(
                        color = Color(0xFF92DDFE).copy(alpha = fillAlpha),
                        topLeft = Offset(left, fillTop),
                        size = Size(barW, fillH),
                        cornerRadius = corner
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Time labels: now, +1h, +2h
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val now = LocalTime.now()
        val t0 = now.format(fmt)
        val t1 = now.plusHours(1).format(fmt)
        val t2 = now.plusHours(2).format(fmt)

        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = t0,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.align(BiasAlignment(-1f, 0f))
            )
            Text(
                text = t1,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.align(BiasAlignment(0f, 0f))
            )
            Text(
                text = t2,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.align(BiasAlignment(1f, 0f))
            )
        }
    }
}

/**
 * Sample N data points down to targetBins by averaging groups.
 * Uses alternating bin sizes (2, 3, 2, 3, ...) to handle 120 -> 48.
 */
private fun sampleToBins(data: List<Double>, targetBins: Int): List<Double> {
    if (data.size <= targetBins) return data
    if (targetBins <= 0) return emptyList()

    val result = mutableListOf<Double>()
    val binSize = data.size.toDouble() / targetBins
    var pos = 0.0

    for (i in 0 until targetBins) {
        val start = pos.toInt()
        pos += binSize
        val end = pos.toInt().coerceAtMost(data.size)
        if (start < end) {
            val avg = (start until end).map { data[it] }.average()
            result.add(avg)
        } else {
            result.add(0.0)
        }
    }
    return result
}
