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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.skypulse.weather.model.MinutelyForecast
import com.skypulse.weather.ui.theme.PrecipBarBottom
import com.skypulse.weather.ui.theme.PrecipBarShadow
import com.skypulse.weather.ui.theme.PrecipBarTop
import com.skypulse.weather.ui.theme.SkyPulseDesignSystem
import com.skypulse.weather.ui.theme.TextPrimary
import com.skypulse.weather.ui.theme.TextSecondary
import kotlin.math.pow
import com.skypulse.weather.ui.screen.LocalSkipCardAnimation

private const val BAR_COUNT = 48
private const val BAR_WIDTH_DP = 3f
private const val BAR_GAP_DP = 3f
private const val CHART_HEIGHT_DP = 60f

@Composable
fun MinutelyPrecipitationCard(
    minutely: MinutelyForecast?,
    modifier: Modifier = Modifier
) {
    val raw = minutely?.precipitation_2h
    if (raw.isNullOrEmpty()) return
    if (raw.all { it == 0.0 }) return

    // Sample 120 entries down to 48 bins
    // Convert mm/min → mm/h (API returns mm/min, scale expects mm/h)
    val sampled = remember(raw) { sampleToBins(raw, BAR_COUNT).map { it * 25.0 } }
    if (sampled.all { it == 0.0 }) return

    val skipAnimation = LocalSkipCardAnimation.current
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (skipAnimation || visible) 1f else 0f,
        animationSpec = if (skipAnimation) tween(0) else tween(
            SkyPulseDesignSystem.Motion.cardEnterMillis,
            delayMillis = SkyPulseDesignSystem.Motion.fastMillis
        ),
        label = "minutely_fade"
    )
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(skipAnimation) { if (skipAnimation) visible = true }

    GlassCard(modifier = modifier.alpha(cardAlpha)) {
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
    // Light(0-2.5)→0-40%, Moderate(2.5-8)→40-70%, Heavy(8-16)→70-90%, Torrential(16+)→90-100%
    // 0->0%, 0.1->16%, 0.5->50%, 1.0->65%, 2.5->91%, 4.0->97%, 6.0+->100%

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
                // Scale aligned with Chinese meteorological standards:
                // Light: 0-2.5mm/h → 0-40%, Moderate: 2.5-8mm/h → 40-70%,
                // Heavy: 8-16mm/h → 70-90%, Torrential: 16-30mm/h → 90-100%
                // Scale aligned with Chinese meteorological standards (mm/h):
                // Light(0-2.5)→0-38%, Moderate(2.5-8)→38-70%, Heavy(8-16)→70-90%, Torrential(16+)→90-100%
                val fillRatio = when {
                    value <= 0.0 -> 0f
                    value <= 0.1 -> (value / 0.1 * 0.03f).toFloat()                   // 0%~3%
                    value <= 0.3 -> (0.03f + (value - 0.1) / 0.2 * 0.05f).toFloat()   // 3%~8%
                    value <= 0.5 -> (0.08f + (value - 0.3) / 0.2 * 0.07f).toFloat()   // 8%~15%
                    value <= 1.0 -> (0.15f + (value - 0.5) / 0.5 * 0.07f).toFloat()   // 15%~22%
                    value <= 2.0 -> (0.22f + (value - 1.0) / 1.0 * 0.10f).toFloat()   // 22%~32%
                    value <= 2.5 -> (0.32f + (value - 2.0) / 0.5 * 0.06f).toFloat()   // 32%~38%
                    value <= 8.0 -> (0.38f + (value - 2.5) / 5.5 * 0.32f).toFloat()   // 38%~70%
                    value <= 16.0 -> (0.70f + (value - 8.0) / 8.0 * 0.20f).toFloat()  // 70%~90%
                    else -> (0.90f + (value - 16.0) / 14.0 * 0.10f).coerceIn(0.90, 1.0).toFloat() // 90%~100%
                }
                // Power curve: expand light rain visual area, compress heavy rain
                val visualRatio = if (fillRatio > 0f) fillRatio.pow(0.70f) else 0f
                val left = startX + i * step
                val fillH = chartH * visualRatio
                val fillTop = chartH - fillH

                // Gray frame (full height, always visible)
                drawRoundRect(
                    color = grayFrame,
                    topLeft = Offset(left, 0f),
                    size = Size(barW, chartH),
                    cornerRadius = corner
                )

                // Soft halo + blue fill for bars with precipitation
                if (visualRatio > 0f) {
                    val shadowAlpha = (0.08f + 0.14f * visualRatio).coerceIn(0.08f, 0.22f)
                    val shadowColor = PrecipBarShadow.copy(alpha = shadowAlpha)
                    for (s in 1..2) {
                        val expand = s * 0.6f
                        drawRoundRect(
                            color = shadowColor,
                            topLeft = Offset(left - expand, fillTop - expand * 0.5f),
                            size = Size(barW + expand * 2, fillH + expand),
                            cornerRadius = CornerRadius(barW / 2f + expand)
                        )
                    }

                    // Vertical gradient: brighter at top, normal at bottom
                    val topAlpha = (0.7f + 0.3f * visualRatio).coerceIn(0.7f, 1f)
                    val bottomAlpha = (0.4f + 0.6f * visualRatio).coerceIn(0.4f, 1f)
                    val gradientBrush = Brush.verticalGradient(
                        colors = listOf(
                            PrecipBarTop.copy(alpha = topAlpha),
                            PrecipBarBottom.copy(alpha = bottomAlpha)
                        ),
                        startY = fillTop,
                        endY = fillTop + fillH
                    )
                    drawRoundRect(
                        brush = gradientBrush,
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                color = TextSecondary,
                modifier = Modifier.align(BiasAlignment(-1f, 0f))
            )
            Text(
                text = t1,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                color = TextSecondary,
                modifier = Modifier.align(BiasAlignment(0f, 0f))
            )
            Text(
                text = t2,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
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