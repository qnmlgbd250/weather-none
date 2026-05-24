package com.skypulse.weather.util

import androidx.compose.ui.graphics.Color
import com.skypulse.weather.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

object WeatherUtils {

    private val hourFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
    }
    private val dateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    data class WeatherInfo(
        val description: String,
        val icon: String,
        val isDay: Boolean = true
    )

    fun getWeatherTheme(skycon: String?, isDay: Boolean): WeatherTheme {
        val background = getWeatherGradient(skycon, isDay)
        val isBright = isBrightBackground(skycon, isDay)
        
        // --- Card Styling ---
        val topAlpha = if (isBright) 0.28f else 0.18f
        val bottomAlpha = if (isBright) 0.12f else 0.08f
        
        val borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color.White.copy(alpha = if (isBright) 0.65f else 0.45f),
                androidx.compose.ui.graphics.Color.White.copy(alpha = if (isBright) 0.15f else 0.10f),
                androidx.compose.ui.graphics.Color.White.copy(alpha = if (isBright) 0.35f else 0.25f)
            ),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset.Infinite
        )

        // --- Chart Colors ---
        val chartColors = if (isDay) {
            WeatherChartColors(
                clear = Color(0xFFFFF9C4).copy(alpha = 0.85f) to Color(0xFFFFF9C4).copy(alpha = 0.20f),
                partlyCloudy = Color(0xFFE1F5FE).copy(alpha = 0.75f) to Color(0xFFE1F5FE).copy(alpha = 0.15f),
                cloudy = Color(0xFFCFD8DC).copy(alpha = 0.70f) to Color(0xFFCFD8DC).copy(alpha = 0.10f),
                rain = Color(0xFF4FC3F7).copy(alpha = 0.70f) to Color(0xFF4FC3F7).copy(alpha = 0.15f),
                snow = Color(0xFFFFFFFF).copy(alpha = 0.85f) to Color(0xFFFFFFFF).copy(alpha = 0.20f),
                wind = Color(0xFF4DB6AC).copy(alpha = 0.65f) to Color(0xFF4DB6AC).copy(alpha = 0.10f),
                haze = Color(0xFFBCAAA4).copy(alpha = 0.65f) to Color(0xFFBCAAA4).copy(alpha = 0.10f),
                storm = Color(0xFF5C6BC0).copy(alpha = 0.75f) to Color(0xFF5C6BC0).copy(alpha = 0.15f)
            )
        } else {
            WeatherChartColors(
                clear = Color(0xFFFFFDE7).copy(alpha = 0.25f) to Color(0xFFFFF9C4).copy(alpha = 0.15f),
                partlyCloudy = Color(0xFFFFF9C4).copy(alpha = 0.22f) to Color(0xFFFFECB3).copy(alpha = 0.12f),
                cloudy = Color(0xFF8898B0).copy(alpha = 0.25f) to Color(0xFF607088).copy(alpha = 0.15f),
                rain = Color(0xFF70A0F0).copy(alpha = 0.30f) to Color(0xFF4070B8).copy(alpha = 0.18f),
                snow = Color(0xFF80B8FF).copy(alpha = 0.32f) to Color(0xFF5090D0).copy(alpha = 0.20f),
                wind = Color(0xFF60C0D0).copy(alpha = 0.28f) to Color(0xFF4090A0).copy(alpha = 0.16f),
                haze = Color(0xFF908878).copy(alpha = 0.25f) to Color(0xFF706858).copy(alpha = 0.15f),
                storm = Color(0xFFB080FF).copy(alpha = 0.35f) to Color(0xFF7040C0).copy(alpha = 0.22f)
            )
        }

        return WeatherTheme(
            isDay = isDay,
            backgroundGradient = background,
            cardTopAlpha = topAlpha,
            cardBottomAlpha = bottomAlpha,
            cardBorderBrush = borderBrush,
            chartColors = chartColors
        )
    }

    fun getWeatherInfo(skycon: String?, hour: Int = 12): WeatherInfo {
        val isDay = hour in 6..18
        return when (skycon) {
            "CLEAR_DAY" -> WeatherInfo("晴", "clear-day", true)
            "CLEAR_NIGHT" -> WeatherInfo("晴", "clear-night", false)
            "PARTLY_CLOUDY_DAY" -> WeatherInfo("多云", "partly-cloudy-day", true)
            "PARTLY_CLOUDY_NIGHT" -> WeatherInfo("多云", "partly-cloudy-night", false)
            "CLOUDY" -> WeatherInfo("阴", "overcast", isDay)
            "LIGHT_HAZE" -> WeatherInfo("轻度霾", "haze", isDay)
            "MODERATE_HAZE" -> WeatherInfo("中度霾", "haze", isDay)
            "HEAVY_HAZE" -> WeatherInfo("重度霾", "haze", isDay)
            "LIGHT_RAIN" -> WeatherInfo("小雨", "drizzle", isDay)
            "MODERATE_RAIN" -> WeatherInfo("中雨", "rain", isDay)
            "HEAVY_RAIN" -> WeatherInfo("大雨", "extreme-rain", isDay)
            "STORM_RAIN" -> WeatherInfo("暴雨", "thunderstorms-rain", isDay)
            "FOG" -> WeatherInfo("雾", "fog", isDay)
            "LIGHT_SNOW" -> WeatherInfo("小雪", "snow", isDay)
            "MODERATE_SNOW" -> WeatherInfo("中雪", "snow", isDay)
            "HEAVY_SNOW" -> WeatherInfo("大雪", "extreme-snow", isDay)
            "STORM_SNOW" -> WeatherInfo("暴雪", "extreme-snow", isDay)
            "WIND" -> WeatherInfo("大风", "wind", isDay)
            else -> WeatherInfo("未知", "overcast", isDay)
        }
    }

    fun getWeatherGradient(skycon: String?, isDay: Boolean = true): List<Color> {
        return when {
            skycon == null -> if (isDay) SunnyGradient else SunnyNightGradient
            skycon.contains("CLEAR") -> if (isDay) SunnyGradient else SunnyNightGradient
            skycon.contains("PARTLY_CLOUDY") -> if (isDay) PartialCloudGradient else PartialCloudNightGradient
            skycon.contains("CLOUDY") -> if (isDay) CloudyGradient else CloudyNightGradient
            skycon.contains("RAIN") || skycon.contains("STORM") -> if (isDay) RainyGradient else RainyNightGradient
            skycon.contains("SNOW") -> if (isDay) SnowyGradient else SnowyNightGradient
            skycon.contains("HAZE") || skycon == "FOG" -> HazeGradient
            skycon == "WIND" -> WindyGradient
            else -> if (isDay) SunnyGradient else SunnyNightGradient
        }
    }

    fun formatTemperature(temp: Double?): String {
        if (temp == null) return "--"
        return "${kotlin.math.round(temp).toInt()}°"
    }

    fun isBrightBackground(skycon: String?, isDay: Boolean = isCurrentlyDay()): Boolean {
        if (!isDay) return false
        return skycon == null ||
               skycon.contains("CLEAR") ||
               skycon.contains("PARTLY_CLOUDY") ||
               skycon.contains("CLOUDY")
    }

    fun getTemperatureColor(temp: Double?): Color {
        if (temp == null) return Color.White
        val t = temp.toFloat()
        return when {
            t < -10f -> Color(0xFF90CAF9) // Very cold
            t < 0f -> lerpColor(Color(0xFF90CAF9), Color(0xFF64B5F6), (t + 10f) / 10f)
            t < 10f -> lerpColor(Color(0xFF64B5F6), Color(0xFF4FC3F7), t / 10f)
            t < 20f -> lerpColor(Color(0xFF4FC3F7), Color(0xFFFFD54F), (t - 10f) / 10f)
            t < 30f -> lerpColor(Color(0xFFFFD54F), Color(0xFFFFB74D), (t - 20f) / 10f)
            t < 40f -> lerpColor(Color(0xFFFFB74D), Color(0xFFEF5350), (t - 30f) / 10f)
            else -> Color(0xFFEF5350) // Very hot
        }
    }

    private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return Color(
            red = start.red + (end.red - start.red) * f,
            green = start.green + (end.green - start.green) * f,
            blue = start.blue + (end.blue - start.blue) * f,
            alpha = start.alpha + (end.alpha - start.alpha) * f
        )
    }

    fun formatHumidity(humidity: Double?): String {
        if (humidity == null) return "--"
        return "${(humidity * 100).toInt()}%"
    }

    fun formatWindSpeed(speed: Double?): String {
        if (speed == null) return "--"
        return when {
            speed < 1 -> "0级"
            speed < 6 -> "1级"
            speed < 12 -> "2级"
            speed < 20 -> "3级"
            speed < 29 -> "4级"
            speed < 39 -> "5级"
            speed < 50 -> "6级"
            speed < 62 -> "7级"
            speed < 75 -> "8级"
            speed < 89 -> "9级"
            speed < 103 -> "10级"
            speed < 117 -> "11级"
            else -> "12级"
        }
    }

    fun formatWindDirection(direction: Double?): String {
        if (direction == null) return ""
        return when {
            direction < 22.5 || direction >= 337.5 -> "北风"
            direction < 67.5 -> "东北风"
            direction < 112.5 -> "东风"
            direction < 157.5 -> "东南风"
            direction < 202.5 -> "南风"
            direction < 247.5 -> "西南风"
            direction < 292.5 -> "西风"
            else -> "西北风"
        }
    }

    fun formatPressure(pressure: Double?): String {
        if (pressure == null) return "--"
        return "${pressure.toInt()} hPa"
    }

    fun formatVisibility(visibility: Double?): String {
        if (visibility == null) return "--"
        return if (visibility >= 1000) {
            "${"%.1f".format(visibility / 1000)} km"
        } else {
            "${visibility.toInt()} m"
        }
    }

    fun formatHourShort(datetime: String?): String {
        if (datetime == null) return ""
        return try {
            val date = hourFormat.get().parse(datetime) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = date
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            "${hour}:00"
        } catch (e: Exception) {
            ""
        }
    }

    fun formatWeekday(dateStr: String?): String {
        if (dateStr == null) return ""
        return try {
            val date = dateFormat.get().parse(dateStr) ?: return ""
            val cal = Calendar.getInstance()
            val today = Calendar.getInstance()
            cal.time = date

            when {
                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "今天"

                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) + 1 -> "明天"

                cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) + 2 -> "后天"

                else -> {
                    val weekdays = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
                    weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun isCurrentlyDay(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 6..18
    }
}
