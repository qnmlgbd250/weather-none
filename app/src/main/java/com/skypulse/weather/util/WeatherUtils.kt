package com.skypulse.weather.util

import androidx.compose.ui.graphics.Color
import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.model.DailyTemperature
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

        // Card base: night deep navy, cloudy day subtle dark tint, other day transparent
        val cardTintColor = if (!isDay) {
            Color(0xFF050D33).copy(alpha = 0.60f)
        } else if (skycon != null && skycon.contains("CLOUDY") && !skycon.contains("PARTLY_CLOUDY")) {
            Color(0xFF2C3E50).copy(alpha = 0.08f)
        } else {
            Color.Transparent
        }

        // Frost tint: night uses cool blue, day blends 12% gradient accent into white
        val cardFrostColor = if (!isDay) {
            Color(0.90f, 0.92f, 1.0f) // cool blue-white for night
        } else {
            val accent = background[2]
            val frostMix = 0.12f
            Color(
                red = (1f - frostMix) + accent.red * frostMix,
                green = (1f - frostMix) + accent.green * frostMix,
                blue = (1f - frostMix) + accent.blue * frostMix
            )
        }

        // --- Card Styling: frost layer alpha (day: standalone; night: on dark base) ---
        val (topAlpha, midAlpha, bottomAlpha) = if (!isDay) {
            Triple(0.18f, 0.12f, 0.06f)
        } else when {
            skycon == null ||
            skycon.contains("CLEAR") -> Triple(0.30f, 0.22f, 0.14f)
            skycon.contains("PARTLY_CLOUDY") -> Triple(0.32f, 0.24f, 0.16f)
            skycon.contains("CLOUDY") -> Triple(0.28f, 0.20f, 0.14f)
            skycon.contains("RAIN") ||
            skycon.contains("STORM") -> Triple(0.20f, 0.14f, 0.10f)
            skycon.contains("SNOW") -> Triple(0.42f, 0.32f, 0.24f)
            skycon.contains("HAZE") ||
            skycon == "FOG" ||
            skycon == "DUST" ||
            skycon == "SAND" -> Triple(0.38f, 0.28f, 0.20f)
            skycon == "WIND" -> Triple(0.25f, 0.18f, 0.14f)
            else -> Triple(0.30f, 0.22f, 0.14f)
        }

        // Border brightness scales with card opacity
        val baseTop = 0.28f
        val borderScale = (topAlpha / baseTop).coerceIn(0.6f, 1.8f)

        val borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color.White.copy(alpha = (0.65f * borderScale).coerceAtMost(1f)),
                androidx.compose.ui.graphics.Color.White.copy(alpha = (0.15f * borderScale).coerceAtMost(1f)),
                androidx.compose.ui.graphics.Color.White.copy(alpha = (0.35f * borderScale).coerceAtMost(1f))
            ),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset.Infinite
        )

        val borderColor = if (isDay) CardBorderDay else CardBorderNight

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
            cardTintColor = cardTintColor,
            cardFrostColor = cardFrostColor,
            cardTopAlpha = topAlpha,
            cardMidAlpha = midAlpha,
            cardBottomAlpha = bottomAlpha,
            cardBorderBrush = borderBrush,
            cardBorderColor = borderColor,
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
            "DUST" -> WeatherInfo("浮尘", "haze", isDay)
            "SAND" -> WeatherInfo("沙尘", "haze", isDay)
            "SLEET" -> WeatherInfo("雨夹雪", "snow", isDay)
            "THUNDER_SHOWER" -> WeatherInfo("雷阵雨", "thunderstorms-rain", isDay)
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
            skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> HazeGradient
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
        if (speed < 1) return "0"
        val s = kotlin.math.ceil(speed).toInt()
        return when {
            s < 6 -> "1"
            s < 12 -> "2"
            s < 20 -> "3"
            s < 29 -> "4"
            s < 39 -> "5"
            s < 50 -> "6"
            s < 62 -> "7"
            s < 75 -> "8"
            s < 89 -> "9"
            s < 103 -> "10"
            s < 118 -> "11"
            s < 134 -> "12"
            s < 150 -> "13"
            s < 167 -> "14"
            s < 184 -> "15"
            s < 202 -> "16"
            else -> "17"
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
        return "${(pressure / 100).toInt()} 百帕"
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
            val date = hourFormat.get()!!.parse(datetime) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = date
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            "${hour}:00"
        } catch (e: Exception) {
            ""
        }
    }

    fun extractHour(datetime: String?): Int {
        if (datetime == null) return -1
        return try {
            val date = hourFormat.get()!!.parse(datetime) ?: return -1
            val cal = Calendar.getInstance()
            cal.time = date
            cal.get(Calendar.HOUR_OF_DAY)
        } catch (e: Exception) {
            -1
        }
    }

    fun formatWeekday(dateStr: String?): String {
        return try {
            val date = parseDateOnly(dateStr) ?: return ""
            val cal = Calendar.getInstance()
            val today = Calendar.getInstance()
            cal.time = date

            when {
                isSameDay(cal, today) -> "今天"

                isTomorrow(dateStr) -> "明天"

                else -> {
                    val weekdays = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
                    weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun todayTemperature(daily: DailyForecast?): DailyTemperature? {
        val temperatures = daily?.temperature ?: return null
        return temperatures.firstOrNull { isToday(it.date) } ?: temperatures.firstOrNull()
    }

    fun isToday(dateStr: String?): Boolean = isRelativeDay(dateStr, 0)

    fun isYesterday(dateStr: String?): Boolean = isRelativeDay(dateStr, -1)

    fun isTomorrow(dateStr: String?): Boolean {
        return isRelativeDay(dateStr, 1)
    }

    fun isCurrentlyDay(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 6..18
    }

    private fun parseDateOnly(dateStr: String?): Date? {
        if (dateStr == null) return null
        val dateText = dateStr.take(10)
        return dateFormat.get()!!.parse(dateText)
    }

    private fun isRelativeDay(dateStr: String?, dayOffset: Int): Boolean {
        return try {
            val date = parseDateOnly(dateStr) ?: return false
            val cal = Calendar.getInstance()
            val target = Calendar.getInstance()
            cal.time = date
            target.add(Calendar.DAY_OF_YEAR, dayOffset)
            isSameDay(cal, target)
        } catch (e: Exception) {
            false
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
