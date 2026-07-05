package com.skypulse.weather.util

import androidx.compose.ui.graphics.Color
import com.skypulse.weather.model.DailyAstro
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

        // --- Card Colors & Alphas Refactoring ---
        val cardTintColor: Color
        if (isDay) {
            cardTintColor = when {
                skycon == null || skycon.contains("CLEAR") -> {
                    // Sunny / Clear: Clean, light flat white panel
                    Color(0xFFFFFFFF).copy(alpha = 0.22f)
                }
                skycon.contains("PARTLY_CLOUDY") -> {
                    // Partly Cloudy: Clean flat white panel with slightly lower opacity
                    Color(0xFFFFFFFF).copy(alpha = 0.20f)
                }
                skycon.contains("CLOUDY") -> {
                    // Cloudy: Muted flat white panel
                    Color(0xFFFFFFFF).copy(alpha = 0.18f)
                }
                skycon.contains("RAIN") || skycon.contains("STORM") -> {
                    // Rainy / Stormy: Flat slate-blue panel to contrast rain
                    Color(0xFF1E293B).copy(alpha = 0.25f)
                }
                skycon.contains("SNOW") -> {
                    // Snowy: Dark slate panel for high contrast against snowy glare
                    Color(0xFF0F172A).copy(alpha = 0.20f)
                }
                skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> {
                    // Haze / Fog: Soft flat white panel
                    Color(0xFFFFFFFF).copy(alpha = 0.18f)
                }
                skycon == "WIND" -> {
                    // Windy: Fresh flat white panel
                    Color(0xFFFFFFFF).copy(alpha = 0.20f)
                }
                else -> {
                    Color(0xFFFFFFFF).copy(alpha = 0.22f)
                }
            }
        } else {
            cardTintColor = when {
                skycon == null || skycon.contains("CLEAR") -> {
                    // Sunny / Clear Night: Deep midnight blue flat panel
                    Color(0xFF0B132B).copy(alpha = 0.45f)
                }
                skycon.contains("PARTLY_CLOUDY") -> {
                    // Partly Cloudy Night: Deep slate flat panel
                    Color(0xFF0F172A).copy(alpha = 0.45f)
                }
                skycon.contains("CLOUDY") -> {
                    // Cloudy Night: Deep slate-blue flat panel
                    Color(0xFF1E293B).copy(alpha = 0.45f)
                }
                skycon.contains("RAIN") || skycon.contains("STORM") -> {
                    // Rainy / Stormy Night: Very deep flat slate panel
                    Color(0xFF0F172A).copy(alpha = 0.50f)
                }
                skycon.contains("SNOW") -> {
                    // Snowy Night: Flat deep slate panel
                    Color(0xFF0F172A).copy(alpha = 0.40f)
                }
                skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> {
                    // Haze / Fog Night: Warm deep stone grey flat panel
                    Color(0xFF1C1917).copy(alpha = 0.50f)
                }
                skycon == "WIND" -> {
                    // Windy Night: Deep ocean-teal flat panel
                    Color(0xFF042F2E).copy(alpha = 0.40f)
                }
                else -> {
                    Color(0xFF0F172A).copy(alpha = 0.45f)
                }
            }
        }

        // Keep values for backwards compatibility in WeatherTheme model
        val cardFrostColor = Color.Transparent
        val topAlpha = 0f
        val midAlpha = 0f
        val bottomAlpha = 0f
        val borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent)
        )
        val borderColor = Color.Transparent

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
            skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> if (isDay) HazeGradient else HazeNightGradient
            skycon == "WIND" -> if (isDay) WindyGradient else WindyNightGradient
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
               skycon.contains("PARTLY_CLOUDY")
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

    fun isCurrentlyDay(
        daily: DailyForecast? = null,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        isDayByAstro(daily?.astro, now)?.let { return it }
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour in 6..18
    }

    private fun isDayByAstro(
        astro: List<DailyAstro>?,
        now: Calendar
    ): Boolean? {
        if (astro.isNullOrEmpty()) return null
        val candidate = astro.firstOrNull { isSameDate(it.date, now) }
            ?: astro.firstOrNull { parseTimeMinutes(it.sunrise?.time) != null && parseTimeMinutes(it.sunset?.time) != null }
            ?: return null
        val sunrise = parseTimeMinutes(candidate.sunrise?.time) ?: return null
        val sunset = parseTimeMinutes(candidate.sunset?.time) ?: return null
        if (sunrise >= sunset) return null
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minuteOfDay in sunrise until sunset
    }

    private fun parseTimeMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val match = Regex("(\\d{1,2}):(\\d{2})").find(time) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun isSameDate(dateStr: String?, calendar: Calendar): Boolean {
        return try {
            val date = parseDateOnly(dateStr) ?: return false
            val other = Calendar.getInstance()
            other.time = date
            isSameDay(other, calendar)
        } catch (_: Exception) {
            false
        }
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
