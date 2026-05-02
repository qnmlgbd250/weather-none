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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.HourlyForecast
import com.skypulse.weather.model.HourlySkycon
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.util.WeatherUtils

private const val HOUR_WIDTH = 56
private val SIDE_PADDING = 8

@Composable
fun HourlyForecastCard(
    hourly: HourlyForecast?,
    modifier: Modifier = Modifier
) {
    if (hourly?.temperature.isNullOrEmpty()) return
    val data = hourly ?: return
    val temps = data.temperature ?: return

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 200),
        label = "card_fade"
    )

    LaunchedEffect(Unit) { visible = true }

    GlassCard(
        modifier = modifier.alpha(alpha)
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = "逐小时预报",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HourlyTemperatureChart(
                temperatures = temps.take(24),
                skycons = data.skycon?.take(24),
                precipitation = data.precipitation?.take(24)
            )
        }
    }
}

@Composable
private fun HourlyTemperatureChart(
    temperatures: List<HourlyValue>,
    skycons: List<HourlySkycon>?,
    precipitation: List<HourlyValue>?
) {
    val textMeasurer = rememberTextMeasurer()

    val tempValues = temperatures.mapNotNull { it.value }
    if (tempValues.isEmpty()) return

    val minTemp = tempValues.min()
    val maxTemp = tempValues.max()
    val tempRange = (maxTemp - minTemp).coerceAtLeast(1.0)

    val precipValues = precipitation?.map { it.value ?: 0.0 } ?: List(temperatures.size) { 0.0 }
    val maxPrecip = precipValues.maxOrNull()?.coerceAtLeast(0.1) ?: 1.0

    // Probability from precipitation entries (API returns 0-100)
    val probValues = precipitation?.map { it.probability ?: 0.0 }
        ?: List(temperatures.size) { 0.0 }

    val skyconValues = skycons?.map { it.value } ?: List(temperatures.size) { null }

    val itemWidthDp = HOUR_WIDTH.dp
    val sidePad = SIDE_PADDING.dp
    val contentWidth = (temperatures.size * HOUR_WIDTH).dp
    val totalWidth = contentWidth + sidePad * 2
    val chartHeight = 140.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Canvas(
            modifier = Modifier
                .width(totalWidth)
                .height(chartHeight)
                .padding(horizontal = sidePad)
        ) {
            val itemCount = temperatures.size
            val step = size.width / itemCount
            val halfStep = step / 2f

            val points = tempValues.mapIndexed { index, temp ->
                val x = halfStep + index * step
                val normalizedY = ((temp - minTemp) / tempRange).toFloat()
                val y = size.height - 28.dp.toPx() - normalizedY * (size.height - 52.dp.toPx())
                Offset(x, y)
            }

            // --- Precipitation bars ---
            data class PrecipSegment(val startIdx: Int, val endIdx: Int)

            val segments = mutableListOf<PrecipSegment>()
            var segStart = -1
            for (i in 0 until itemCount) {
                if (precipValues[i] > 0.01) {
                    if (segStart == -1) segStart = i
                } else {
                    if (segStart != -1) {
                        segments.add(PrecipSegment(segStart, i - 1))
                        segStart = -1
                    }
                }
            }
            if (segStart != -1) segments.add(PrecipSegment(segStart, itemCount - 1))

            fun sampleSplineY(x: Float): Float {
                for (j in 0 until itemCount - 1) {
                    if (x <= points[j + 1].x || j == itemCount - 2) {
                        val p0 = points[j]
                        val p1 = points[j + 1]
                        val t = if (p1.x != p0.x) ((x - p0.x) / (p1.x - p0.x)).coerceIn(0f, 1f) else 0f
                        val u = 1f - t
                        return u * u * u * p0.y +
                            3f * u * u * t * p0.y +
                            3f * u * t * t * p1.y +
                            t * t * t * p1.y
                    }
                }
                return points.last().y
            }

            for (seg in segments) {
                val leftX = if (seg.startIdx == 0) 0f
                    else (points[seg.startIdx - 1].x + points[seg.startIdx].x) / 2f
                val rightX = if (seg.endIdx == itemCount - 1) size.width
                    else (points[seg.endIdx].x + points[seg.endIdx + 1].x) / 2f

                val segMaxPrecip = (seg.startIdx..seg.endIdx).maxOf { precipValues[it] }
                val intensityRatio = (segMaxPrecip / maxPrecip).toFloat().coerceIn(0f, 1f)
                val barAlpha = (0.15f + intensityRatio * 0.3f).coerceIn(0.15f, 0.4f)

                val barPath = Path().apply {
                    moveTo(leftX, size.height)
                    val steps = ((rightX - leftX) / (1.5f * density)).toInt().coerceIn(10, 200)
                    for (s in 0..steps) {
                        val t = s.toFloat() / steps
                        val x = leftX + (rightX - leftX) * t
                        lineTo(x, sampleSplineY(x))
                    }
                    lineTo(rightX, size.height)
                    close()
                }
                drawPath(barPath, PrecipitationBlue.copy(alpha = barAlpha))

                // Text inside bars: group by same skycon
                data class TextGroup(val startIdx: Int, val endIdx: Int, val skycon: String?)

                val textGroups = mutableListOf<TextGroup>()
                var groupStart = seg.startIdx
                var groupSkycon = skyconValues.getOrNull(seg.startIdx)
                for (i in (seg.startIdx + 1)..seg.endIdx) {
                    val curSkycon = skyconValues.getOrNull(i)
                    if (curSkycon != groupSkycon) {
                        textGroups.add(TextGroup(groupStart, i - 1, groupSkycon))
                        groupStart = i
                        groupSkycon = curSkycon
                    }
                }
                textGroups.add(TextGroup(groupStart, seg.endIdx, groupSkycon))

                for (group in textGroups) {
                    if (group.skycon == null) continue
                    val weatherInfo = WeatherUtils.getWeatherInfo(group.skycon)
                    val rainLabel = weatherInfo.description

                    if (group.skycon?.contains("RAIN") != true &&
                        group.skycon?.contains("STORM") != true &&
                        group.skycon?.contains("SNOW") != true
                    ) continue

                    // Max probability in this group (already 0-100)
                    val groupMaxProb = (group.startIdx..group.endIdx)
                        .map { probValues[it] }.maxOrNull() ?: 0.0
                    val probText = if (groupMaxProb >= 1.0) "${groupMaxProb.toInt()}%" else ""

                    val gLeftX = if (group.startIdx == 0) 0f
                        else (points[group.startIdx - 1].x + points[group.startIdx].x) / 2f
                    val gRightX = if (group.endIdx == itemCount - 1) size.width
                        else (points[group.endIdx].x + points[group.endIdx + 1].x) / 2f
                    val centerX = (gLeftX + gRightX) / 2f

                    val curveYAtCenter = sampleSplineY(centerX)
                    val centerY = (curveYAtCenter + size.height) / 2f

                    val lineStyle = TextStyle(fontSize = 9.sp, color = Color.White.copy(alpha = 0.9f))
                    val rainResult = textMeasurer.measure(AnnotatedString(rainLabel), style = lineStyle)

                    val groupWidth = gRightX - gLeftX
                    if (groupWidth < rainResult.size.width * 0.8f) continue

                    val rainX = centerX - rainResult.size.width / 2f
                    val lineSpacing = 2.dp.toPx()
                    val totalTextHeight = if (probText.isNotEmpty()) {
                        val probResult = textMeasurer.measure(AnnotatedString(probText), style = lineStyle)
                        rainResult.size.height.toFloat() + lineSpacing + probResult.size.height.toFloat()
                    } else {
                        rainResult.size.height.toFloat()
                    }
                    val startY = centerY - totalTextHeight / 2f

                    drawText(rainResult, topLeft = Offset(rainX, startY))

                    if (probText.isNotEmpty()) {
                        val probResult = textMeasurer.measure(AnnotatedString(probText), style = lineStyle)
                        val probX = centerX - probResult.size.width / 2f
                        drawText(
                            probResult,
                            topLeft = Offset(probX, startY + rainResult.size.height + lineSpacing)
                        )
                    }
                }
            }

            // --- Gradient fill under curve ---
            if (points.size >= 2) {
                val fillPath = Path().apply {
                    moveTo(points.first().x, size.height)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, size.height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.0f)
                        )
                    )
                )

                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val cx = (prev.x + curr.x) / 2f
                        cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = Color.White,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // --- Dots and temperature labels ---
            points.forEachIndexed { index, point ->
                drawCircle(Color.White, 3.dp.toPx(), point)
                drawCircle(Color.White.copy(alpha = 0.3f), 6.dp.toPx(), point)

                val tempText = "${tempValues[index].toInt()}°"
                val result = textMeasurer.measure(
                    AnnotatedString(tempText),
                    style = TextStyle(fontSize = 10.sp, color = Color.White)
                )
                drawText(
                    result,
                    topLeft = Offset(point.x - result.size.width / 2, point.y - result.size.height - 6.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Weather icon row
        Row(
            modifier = Modifier
                .width(totalWidth)
                .padding(horizontal = sidePad)
        ) {
            skycons?.forEach { skycon ->
                val info = WeatherUtils.getWeatherInfo(skycon.value)
                Box(
                    modifier = Modifier.width(itemWidthDp),
                    contentAlignment = Alignment.Center
                ) {
                    WeatherIcon(iconType = info.icon, size = 22.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Hour label row
        Row(
            modifier = Modifier
                .width(totalWidth)
                .padding(horizontal = sidePad)
        ) {
            temperatures.forEach { temp ->
                Box(
                    modifier = Modifier.width(itemWidthDp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = WeatherUtils.formatHourShort(temp.datetime),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
