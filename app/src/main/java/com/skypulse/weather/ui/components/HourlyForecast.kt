package com.skypulse.weather.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.skypulse.weather.model.HourlyWind
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.ui.screen.LocalSkipCardAnimation
import com.skypulse.weather.ui.theme.TextTertiary
import com.skypulse.weather.util.WeatherUtils

private const val HOUR_WIDTH = 56
private val SIDE_PADDING = 12

// ============ Hourly Parameter Colors (低饱和度，兼容毛玻璃主题) ============
private object HourlyParamColors {
    // AQI — 两种绿色：亮色背景用深绿，暗色背景用浅绿
    val AqiExcellentBright = Color(0xFF43A047)  // 优 亮色背景（晴/多云白天）
    val AqiExcellentDark = Color(0xFF81C784)    // 优 暗色背景（阴/雨/夜）
    val AqiGood = Color(0xFFFFD54F)        // 良 51-100 暖金
    val AqiLight = Color(0xFFFFB74D)       // 轻度 101-150 柔橙
    val AqiModerate = Color(0xFFE57373)    // 中度 151-200 柔红
    val AqiHeavy = Color(0xFFCE93D8)       // 重度 201-300 柔紫
    val AqiSevere = Color(0xFFA1887F)      // 严重 300+ 棕灰

    // UV
    val UvWeakBright = Color(0xFF43A047)   // 无/很弱 0-2 亮色背景（与空气优一致）
    val UvWeakDark = Color(0xFF81C784)     // 无/很弱 0-2 暗色背景（与空气优一致）
    val UvLow = Color(0xFFFFD54F)          // 弱 3-4 暖金
    val UvMedium = Color(0xFFFFB74D)       // 中等 5-6 柔橙
    val UvStrong = Color(0xFFEF9A9A)       // 强 7-9 柔粉红
    val UvVeryStrong = Color(0xFFCE93D8)   // 极强 10+ 柔紫

    // Wind
    val WindCalm = Color(0xFFB0BEC5)       // 0-2级 柔灰蓝
    val WindModerate = Color(0xFF80CBC4)   // 3-4级 柔青
    val WindFresh = Color(0xFF4DB6AC)      // 5-6级 青绿
    val WindStrong = Color(0xFFFFB74D)     // 7-8级 柔橙
    val WindGale = Color(0xFFE57373)       // 9+级 柔红
}

private fun aqiLabel(aqi: Double?): String {
    if (aqi == null) return ""
    val v = aqi.toInt()
    return when {
        v <= 50 -> "${v}优"
        v <= 100 -> "${v}良"
        v <= 150 -> "${v}轻度"
        v <= 200 -> "${v}中度"
        v <= 300 -> "${v}重度"
        else -> "${v}严重"
    }
}

private fun aqiColor(aqi: Double?, isBrightBg: Boolean = true): Color {
    if (aqi == null) return TextDisabled
    val v = aqi.toInt()
    return when {
        v <= 50 -> if (isBrightBg) HourlyParamColors.AqiExcellentBright else HourlyParamColors.AqiExcellentDark
        v <= 100 -> HourlyParamColors.AqiGood
        v <= 150 -> HourlyParamColors.AqiLight
        v <= 200 -> HourlyParamColors.AqiModerate
        v <= 300 -> HourlyParamColors.AqiHeavy
        else -> HourlyParamColors.AqiSevere
    }
}

private fun uvLabel(uvIndex: String?): String {
    if (uvIndex.isNullOrBlank()) return ""
    val v = uvIndex.toIntOrNull() ?: return ""
    return when {
        v <= 0 -> "$v 无"
        v <= 2 -> "$v 很弱"
        v <= 4 -> "$v 弱"
        v <= 6 -> "$v 中等"
        v <= 9 -> "$v 强"
        else -> "$v 极强"
    }
}

private fun uvColor(uvIndex: String?, isBrightBg: Boolean = true): Color {
    if (uvIndex.isNullOrBlank()) return TextDisabled
    val v = uvIndex.toIntOrNull() ?: return TextDisabled
    return when {
        v <= 2 -> if (isBrightBg) HourlyParamColors.UvWeakBright else HourlyParamColors.UvWeakDark
        v <= 4 -> HourlyParamColors.UvLow
        v <= 6 -> HourlyParamColors.UvMedium
        v <= 9 -> HourlyParamColors.UvStrong
        else -> HourlyParamColors.UvVeryStrong
    }
}

private fun windLabel(wind: HourlyWind?): String {
    if (wind == null) return ""
    val dir = WeatherUtils.formatWindDirection(wind.direction)
    val level = WeatherUtils.formatWindSpeed(wind.speed)
    if (dir.isEmpty() && level == "--") return ""
    return "$dir$level"
}

private fun windColor(wind: HourlyWind?): Color {
    if (wind?.speed == null) return TextDisabled
    val speed = wind.speed
    return when {
        speed < 12 -> HourlyParamColors.WindCalm
        speed < 29 -> HourlyParamColors.WindModerate
        speed < 50 -> HourlyParamColors.WindFresh
        speed < 75 -> HourlyParamColors.WindStrong
        else -> HourlyParamColors.WindGale
    }
}

private fun gustLabel(speed: Double?): String {
    if (speed == null) return ""
    val level = WeatherUtils.formatWindSpeed(speed)
    if (level == "--") return ""
    return "阵风${level}级"
}


private val ParamTagShape = RoundedCornerShape(4.dp)

@Composable
fun HourlyForecastCard(
    hourly: HourlyForecast?,
    modifier: Modifier = Modifier
) {
    if (hourly?.temperature.isNullOrEmpty()) return
    val data = hourly ?: return
    data.temperature ?: return

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("notification_prefs", android.content.Context.MODE_PRIVATE)
    val showAqi = prefs.getBoolean("show_hourly_aqi", true)
    val showUv = prefs.getBoolean("show_hourly_uv", true)
    val showWind = prefs.getBoolean("show_hourly_wind", true)
    val showWindGust = prefs.getBoolean("show_hourly_wind_gust", false)

    val skipAnimation = LocalSkipCardAnimation.current
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (skipAnimation || visible) 1f else 0f,
        animationSpec = if (skipAnimation) tween(0) else tween(600, delayMillis = 300),
        label = "card_fade"
    )
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(skipAnimation) { if (skipAnimation) visible = true }

    GlassCard(
        modifier = modifier.alpha(cardAlpha)
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Text(
                text = "逐小时预报",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HourlyTemperatureChart(
                hourlyData = data,
                showAqi = showAqi,
                showUv = showUv,
                showWind = showWind,
                showWindGust = showWindGust
            )
        }
    }
}

@Composable
private fun HourlyTemperatureChart(
    hourlyData: HourlyForecast,
    showAqi: Boolean = true,
    showUv: Boolean = true,
    showWind: Boolean = true,
    showWindGust: Boolean = true
) {
    val showAnyParam = showAqi || showUv || showWind || showWindGust
    val temperatures = hourlyData.temperature?.take(24) ?: return
    val skycons = hourlyData.skycon?.take(24)
    val precipitation = hourlyData.precipitation?.take(24)
    val winds = hourlyData.wind?.take(24)
    val gustValues = hourlyData.gust?.take(24)
    val aqiValues = hourlyData.air_quality?.aqi?.take(24)
    val uvItems = hourlyData.life_index?.ultraviolet?.take(24)

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

    // 判断当前天气背景是否为亮色（用于AQI颜色自适应）
    val primarySkycon = skyconValues.firstOrNull()
    val isBrightBg = theme.isDay && WeatherUtils.isBrightBackground(primarySkycon, true)

    val itemWidthDp = HOUR_WIDTH.dp
    val sidePad = SIDE_PADDING.dp
    val totalWidth = (temperatures.size * HOUR_WIDTH).dp + sidePad * 2
    val chartHeight = 120.dp

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
                    // Extend to left bar boundary
                    moveTo(0f, points.first().y)
                    lineTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]; val p1 = points[i + 1]
                        val m0 = tangents[i]; val m1 = tangents[i + 1]; val dx = p1.x - p0.x
                        cubicTo(p0.x + dx / 3f, p0.y + m0 * dx / 3f, p1.x - dx / 3f, p1.y - m1 * dx / 3f, p1.x, p1.y)
                    }
                    // Extend to right bar boundary
                    lineTo(size.width, points.last().y)
                }
                drawPath(path = linePath, color = Color.White, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round))
            }
            points.forEachIndexed { index, point ->
                if (index == 0) {
                    drawCircle(Color.White.copy(alpha = 0.25f), 8.dp.toPx(), point)
                    drawCircle(Color.White, 4.5.dp.toPx(), point)
                } else {
                    drawCircle(Color.White, 3.5.dp.toPx(), point)
                }
                val tempText = "${tempValues[index].toInt()}°"
                val result = textMeasurer.measure(AnnotatedString(tempText), style = TextStyle(fontSize = 13.sp, color = Color.White))
                drawText(result, topLeft = Offset(point.x - result.size.width / 2, point.y - result.size.height - 6.dp.toPx()))
            }

            val labelStyle = TextStyle(fontSize = 12.sp, color = TextSecondary)
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

        if (showAnyParam) {
            Spacer(modifier = Modifier.height(6.dp))
        }

        val tagWidth = 48.dp

        // AQI Row
        if (showAqi) {
            Row(modifier = Modifier.width(totalWidth).padding(horizontal = sidePad)) {
                temperatures.forEachIndexed { index, _ ->
                    val aqi = aqiValues?.getOrNull(index)?.value?.chn
                    val label = aqiLabel(aqi)
                    val color = aqiColor(aqi, isBrightBg)
                    Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = color,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(tagWidth)
                                    .clip(ParamTagShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        // UV Row
        if (showUv) {
            if (showAqi) Spacer(modifier = Modifier.height(7.5.dp))
            Row(modifier = Modifier.width(totalWidth).padding(horizontal = sidePad)) {
                temperatures.forEachIndexed { index, _ ->
                    val uvIndex = uvItems?.getOrNull(index)?.index
                    val label = uvLabel(uvIndex)
                    val color = uvColor(uvIndex, isBrightBg)
                    Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = color,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(tagWidth)
                                    .clip(ParamTagShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        // Wind Row
        if (showWind) {
            if (showAqi || showUv) Spacer(modifier = Modifier.height(7.5.dp))
            Row(modifier = Modifier.width(totalWidth).padding(horizontal = sidePad)) {
                temperatures.forEachIndexed { index, _ ->
                    val wind = winds?.getOrNull(index)
                    val label = windLabel(wind)
                    Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(tagWidth)
                                    .clip(ParamTagShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        // Gust Row
        if (showWindGust) {
            if (showAqi || showUv || showWind) Spacer(modifier = Modifier.height(7.5.dp))
            Row(modifier = Modifier.width(totalWidth).padding(horizontal = sidePad)) {
                temperatures.forEachIndexed { index, _ ->
                    val gustSpeed = gustValues?.getOrNull(index)?.value
                    val label = gustLabel(gustSpeed)
                    Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(tagWidth)
                                    .clip(ParamTagShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Time Row
        Row(modifier = Modifier.width(totalWidth).padding(horizontal = sidePad)) {
            temperatures.forEachIndexed { index, temp ->
                Box(modifier = Modifier.width(itemWidthDp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (index == 0) "现在" else WeatherUtils.formatHourShort(temp.datetime),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}