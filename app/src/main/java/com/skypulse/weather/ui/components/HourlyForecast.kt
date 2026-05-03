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
    val rawRange = maxTemp - minTemp
    val padding = rawRange * 0.15
    val paddedMin = minTemp - padding
    val paddedMax = maxTemp + padding
    val tempRange = (paddedMax - paddedMin).coerceAtLeast(1.0)

    val precipValues = precipitation?.map { it.value ?: 0.0 } ?: List(temperatures.size) { 0.0 }
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
            val canvasH = size.height

            // Curve area: top portion of canvas
            val curveAreaBottom = canvasH * 0.70f
            val curveTop = 20.dp.toPx()

            // Catmull-Rom spline
            val splinePoints = (0 until itemCount).map { i ->
                val x = halfStep + i * step
                val normalizedY = ((tempValues[i] - paddedMin) / tempRange).toFloat()
                val y = curveAreaBottom - normalizedY * (curveAreaBottom - curveTop)
                Offset(x, y.coerceIn(curveTop, curveAreaBottom))
            }

            fun sampleSplineY(x: Float): Float {
                if (splinePoints.isEmpty()) return curveAreaBottom
                if (x <= splinePoints.first().x) return splinePoints.first().y
                if (x >= splinePoints.last().x) return splinePoints.last().y

                for (j in 0 until splinePoints.size - 1) {
                    if (x <= splinePoints[j + 1].x) {
                        val p0 = splinePoints[maxOf(0, j - 1)]
                        val p1 = splinePoints[j]
                        val p2 = splinePoints[j + 1]
                        val p3 = splinePoints[minOf(splinePoints.size - 1, j + 2)]

                        val t = if (p2.x != p1.x) ((x - p1.x) / (p2.x - p1.x)).coerceIn(0f, 1f) else 0f
                        val t2 = t * t
                        val t3 = t2 * t

                        val tau = 0.35f
                        val h00 = (2f * t3 - 3f * t2 + 1f)
                        val h10 = (t3 - 2f * t2 + t) * tau
                        val h01 = (-2f * t3 + 3f * t2)
                        val h11 = (t3 - t2) * tau
                        val y = h00 * p1.y + h10 * (p2.y - p0.y) + h01 * p2.y + h11 * (p3.y - p1.y)
                        return y.coerceIn(curveTop, curveAreaBottom)
                    }
                }
                return splinePoints.last().y
            }

            // --- Colored bars per hour with gradient (curve top → transparent bottom) ---
            for (i in 0 until itemCount) {
                val leftX = if (i == 0) 0f else (splinePoints[i - 1].x + splinePoints[i].x) / 2f
                val rightX = if (i == itemCount - 1) size.width
                    else (splinePoints[i].x + splinePoints[i + 1].x) / 2f

                val skycon = skyconValues[i]
                val barColor = when {
                    skycon == null -> Color(0xFF78909C)
                    skycon.contains("PARTLY_CLOUDY") -> Color(0xFFF1F8FF)
                    skycon.contains("CLEAR") -> Color(0xFFFFFCF7)
                    skycon.contains("CLOUDY") -> Color(0xFFF6F7F9)
                    skycon.contains("STORM_RAIN") -> Color(0xFF1565C0)
                    skycon.contains("HEAVY_RAIN") -> Color(0xFF1E88E5)
                    skycon.contains("RAIN") -> Color(0xFF64B5F6)
                    skycon.contains("STORM_SNOW") -> Color(0xFF90A4AE)
                    skycon.contains("HEAVY_SNOW") -> Color(0xFFB0BEC5)
                    skycon.contains("SNOW") -> Color(0xFFCFD8DC)
                    skycon.contains("HAZE") || skycon == "FOG" -> Color(0xFFA1887F)
                    skycon == "WIND" -> Color(0xFF4DB6AC)
                    else -> Color(0xFF78909C)
                }

                val barSteps = ((rightX - leftX) / (0.5f * density)).toInt().coerceIn(20, 300)
                val barTopY = splinePoints[i].y.coerceAtMost(
                    if (i < itemCount - 1) splinePoints[i + 1].y else splinePoints[i].y
                )

                val barPath = Path().apply {
                    moveTo(leftX, canvasH)
                    for (s in 0..barSteps) {
                        val t = s.toFloat() / barSteps
                        val x = leftX + (rightX - leftX) * t
                        lineTo(x, sampleSplineY(x))
                    }
                    lineTo(rightX, canvasH)
                    close()
                }
                drawPath(
                    path = barPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            barColor,
                            barColor.copy(alpha = 0f)
                        ),
                        startY = barTopY,
                        endY = canvasH
                    )
                )
            }

            // --- Curve line on top of bars ---
            if (splinePoints.size >= 2) {
                val linePath = Path().apply {
                    moveTo(splinePoints.first().x, splinePoints.first().y)
                    for (i in 1 until splinePoints.size) {
                        val prev = splinePoints[i - 1]
                        val curr = splinePoints[i]
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
            splinePoints.forEachIndexed { index, point ->
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

            // --- Weather text in lower part of bars ---
            val labelStyle = TextStyle(fontSize = 9.sp, color = Color.White.copy(alpha = 0.9f))
            val labelCenterY = curveAreaBottom + (canvasH - curveAreaBottom) * 0.45f

            for (i in 0 until itemCount) {
                val skycon = skyconValues[i] ?: continue
                val leftX = if (i == 0) 0f else (splinePoints[i - 1].x + splinePoints[i].x) / 2f
                val rightX = if (i == itemCount - 1) size.width
                    else (splinePoints[i].x + splinePoints[i + 1].x) / 2f
                val centerX = (leftX + rightX) / 2f

                val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
                val weatherLabel = weatherInfo.description
                val weatherResult = textMeasurer.measure(AnnotatedString(weatherLabel), style = labelStyle)

                val isPrecip = skycon.contains("RAIN") || skycon.contains("STORM") || skycon.contains("SNOW")
                val prob = probValues[i]
                val probText = if (isPrecip && prob >= 1.0) "${prob.toInt()}%" else ""

                val lineSpacing = 2.dp.toPx()
                val totalTextHeight = if (probText.isNotEmpty()) {
                    val probResult = textMeasurer.measure(AnnotatedString(probText), style = labelStyle)
                    weatherResult.size.height.toFloat() + lineSpacing + probResult.size.height.toFloat()
                } else {
                    weatherResult.size.height.toFloat()
                }

                val barWidth = rightX - leftX
                if (barWidth < weatherResult.size.width * 0.7f) continue

                val textStartY = labelCenterY - totalTextHeight / 2f
                val weatherX = centerX - weatherResult.size.width / 2f
                drawText(weatherResult, topLeft = Offset(weatherX, textStartY))

                if (probText.isNotEmpty()) {
                    val probResult = textMeasurer.measure(AnnotatedString(probText), style = labelStyle)
                    val probX = centerX - probResult.size.width / 2f
                    drawText(
                        probResult,
                        topLeft = Offset(probX, textStartY + weatherResult.size.height + lineSpacing)
                    )
                }
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
                    WeatherIcon(iconType = info.icon, size = 30.dp)
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
