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
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypulse.weather.model.HourlyForecast
import com.skypulse.weather.model.HourlySkycon
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.ui.theme.TextTertiary
import com.skypulse.weather.util.WeatherUtils

private const val HOUR_WIDTH = 56
private val SIDE_PADDING = 12

@Composable
fun HourlyForecastCard(
    hourly: HourlyForecast?,
    @Suppress("UNUSED_PARAMETER") currentSkycon: String? = null,
    modifier: Modifier = Modifier
) {
    if (hourly?.temperature.isNullOrEmpty()) return
    val data = hourly ?: return
    data.temperature ?: return

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 300),
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
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HourlyTemperatureChart(
                hourlyData = data
            )
        }
    }
}

@Composable
private fun HourlyTemperatureChart(
    hourlyData: HourlyForecast
) {
    val temperatures = hourlyData.temperature?.take(24) ?: return
    val skycons = hourlyData.skycon?.take(24)
    val precipitation = hourlyData.precipitation?.take(24)

    val theme = LocalWeatherTheme.current
    val textMeasurer = rememberTextMeasurer()

    val tempValues = temperatures.mapNotNull { it.value?.let { v -> kotlin.math.round(v) } }
    if (tempValues.isEmpty()) return

    val minTemp = tempValues.min()
    val maxTemp = tempValues.max()
    val rawRange = maxTemp - minTemp
    val padding = (rawRange * 0.15).coerceAtLeast(1.0)
    val paddedMin = minTemp - padding
    val paddedMax = maxTemp + padding
    val tempRange = (paddedMax - paddedMin).coerceAtLeast(1.0)

    val probValues = precipitation?.map { it.probability ?: 0.0 } ?: List(temperatures.size) { 0.0 }
    val skyconValues = skycons?.map { it.value } ?: List(temperatures.size) { null }

    val itemWidthDp = HOUR_WIDTH.dp
    val sidePad = SIDE_PADDING.dp
    val totalWidth = (temperatures.size * HOUR_WIDTH).dp + sidePad * 2
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
            val canvasH = size.height

            val curveAreaBottom = canvasH * 0.70f
            val curveTop = 20.dp.toPx()

            val points = (0 until itemCount).map { i ->
                val x = halfStep + i * step
                val normalizedY = ((tempValues[i] - paddedMin) / tempRange).toFloat()
                val y = curveAreaBottom - normalizedY * (curveAreaBottom - curveTop)
                Offset(x, y.coerceIn(curveTop, curveAreaBottom))
            }

            val tangents = FloatArray(itemCount)
            val segmentSlopes = FloatArray(itemCount - 1)
            for (i in 0 until itemCount - 1) {
                segmentSlopes[i] = (points[i + 1].y - points[i].y) / (points[i + 1].x - points[i].x)
            }

            for (i in 0 until itemCount) {
                when {
                    i == 0 -> tangents[i] = segmentSlopes[0]
                    i == itemCount - 1 -> tangents[i] = segmentSlopes[itemCount - 2]
                    else -> {
                        val s0 = segmentSlopes[i - 1]
                        val s1 = segmentSlopes[i]
                        tangents[i] = if (s0 * s1 <= 0) 0f else (s0 + s1) / 2f
                    }
                }
            }

            fun findSegmentIndex(x: Float): Int {
                var lo = 0; var hi = points.size - 2
                while (lo <= hi) {
                    val mid = (lo + hi) ushr 1
                    if (x < points[mid].x) hi = mid - 1
                    else if (x > points[mid + 1].x) lo = mid + 1
                    else return mid
                }
                return lo.coerceIn(0, points.size - 2)
            }

            fun sampleSplineY(x: Float): Float {
                if (points.isEmpty()) return curveAreaBottom
                if (x <= points.first().x) return points.first().y
                if (x >= points.last().x) return points.last().y
                val i = findSegmentIndex(x)
                val p0 = points[i]; val p1 = points[i + 1]
                val m0 = tangents[i]; val m1 = tangents[i + 1]
                val t = (x - p0.x) / (p1.x - p0.x)
                val t2 = t * t; val t3 = t2 * t
                val h00 = 2 * t3 - 3 * t2 + 1; val h10 = t3 - 2 * t2 + t
                val h01 = -2 * t3 + 3 * t2; val h11 = t3 - t2
                val dx = p1.x - p0.x
                return h00 * p0.y + h10 * m0 * dx + h01 * p1.y + h11 * m1 * dx
            }

            val chartColors = theme.chartColors
            val barColorPairs = skyconValues.map { skycon ->
                when {
                    skycon == null -> chartColors.clear
                    skycon.contains("STORM") -> chartColors.storm
                    skycon.contains("HEAVY_RAIN") || skycon.contains("HEAVY_SNOW") -> chartColors.rain
                    skycon.contains("RAIN") || skycon.contains("SNOW") -> chartColors.rain
                    skycon.contains("LIGHT_RAIN") || skycon.contains("LIGHT_SNOW") -> chartColors.rain
                    skycon.contains("CLOUDY") -> chartColors.cloudy
                    skycon.contains("PARTLY_CLOUDY") -> chartColors.partlyCloudy
                    skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> chartColors.haze
                    skycon == "WIND" -> chartColors.wind
                    skycon.contains("CLEAR") -> chartColors.clear
                    else -> chartColors.clear
                }
            }

            for (i in 0 until itemCount) {
                val leftX = if (i == 0) 0f else (points[i - 1].x + points[i].x) / 2f
                val rightX = if (i == itemCount - 1) size.width else (points[i].x + points[i + 1].x) / 2f
                val (topColor, _) = barColorPairs[i]

                val barSteps = ((rightX - leftX) / (2f * density)).toInt().coerceIn(10, 50)
                val sampledYs = FloatArray(barSteps + 1)
                for (s in 0..barSteps) {
                    val x = leftX + (rightX - leftX) * (s.toFloat() / barSteps)
                    sampledYs[s] = sampleSplineY(x)
                }
                val barTopY = sampledYs.min()

                val barPath = Path().apply {
                    moveTo(leftX, canvasH)
                    for (s in 0..barSteps) {
                        val x = leftX + (rightX - leftX) * (s.toFloat() / barSteps)
                        lineTo(x, sampledYs[s])
                    }
                    lineTo(rightX, canvasH); close()
                }
                drawPath(path = barPath, brush = Brush.verticalGradient(
                    colors = listOf(
                        topColor,
                        topColor.copy(alpha = topColor.alpha * 0.6f),
                        topColor.copy(alpha = topColor.alpha * 0.25f),
                        topColor.copy(alpha = 0f)
                    ),
                    startY = barTopY,
                    endY = canvasH
                ))
            }

            if (points.size >= 2) {
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]; val p1 = points[i + 1]
                        val m0 = tangents[i]; val m1 = tangents[i + 1]; val dx = p1.x - p0.x
                        cubicTo(p0.x + dx / 3f, p0.y + m0 * dx / 3f, p1.x - dx / 3f, p1.y - m1 * dx / 3f, p1.x, p1.y)
                    }
                }
                drawPath(path = linePath, color = Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            points.forEachIndexed { index, point ->
                if (index == 0) {
                    drawCircle(Color.White, 3.5.dp.toPx(), point)
                } else {
                    drawCircle(Color.White, 3.dp.toPx(), point, style = Stroke(width = 1.5.dp.toPx()))
                }
                val tempText = "${tempValues[index].toInt()}°"
                val result = textMeasurer.measure(AnnotatedString(tempText), style = TextStyle(fontSize = 12.sp, color = Color.White))
                drawText(result, topLeft = Offset(point.x - result.size.width / 2, point.y - result.size.height - 6.dp.toPx()))
            }

            val labelStyle = TextStyle(fontSize = 11.sp, color = TextSecondary)
            val labelCenterY = curveAreaBottom + (canvasH - curveAreaBottom) * 0.45f
            for (i in 0 until itemCount) {
                val skycon = skyconValues[i] ?: continue
                val leftX = if (i == 0) 0f else (points[i - 1].x + points[i].x) / 2f
                val rightX = if (i == itemCount - 1) size.width else (points[i].x + points[i + 1].x) / 2f
                val centerX = (leftX + rightX) / 2f
                val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
                val weatherResult = textMeasurer.measure(AnnotatedString(weatherInfo.description), style = labelStyle)
                val isPrecip = skycon.contains("RAIN") || skycon.contains("STORM") || skycon.contains("SNOW")
                val prob = probValues[i]
                val probText = if (isPrecip && prob >= 1.0) "${prob.toInt()}%" else ""
                val lineSpacing = 2.dp.toPx()
                val totalH = if (probText.isNotEmpty()) weatherResult.size.height + lineSpacing + textMeasurer.measure(AnnotatedString(probText), style = labelStyle).size.height else weatherResult.size.height.toFloat()
                if (rightX - leftX < weatherResult.size.width * 0.7f) continue
                val startY = labelCenterY - totalH / 2f
                drawText(weatherResult, topLeft = Offset(centerX - weatherResult.size.width / 2f, startY))
                if (probText.isNotEmpty()) {
                    val probResult = textMeasurer.measure(AnnotatedString(probText), style = labelStyle)
                    drawText(probResult, topLeft = Offset(centerX - probResult.size.width / 2f, startY + weatherResult.size.height + lineSpacing))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Icons Row
        Row(modifier = Modifier.width(totalWidth).padding(horizontal = sidePad)) {
            skycons?.forEach { skycon ->
                val info = WeatherUtils.getWeatherInfo(skycon.value)
                Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                    WeatherIcon(iconType = info.icon, size = 36.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Time Row
        Row(modifier = Modifier.width(totalWidth).padding(horizontal = sidePad)) {
            temperatures.forEachIndexed { index, temp ->
                Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (index == 0) "现在" else WeatherUtils.formatHourShort(temp.datetime),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
