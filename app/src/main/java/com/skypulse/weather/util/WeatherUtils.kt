package com.skypulse.weather.util

import androidx.compose.ui.graphics.Color
import com.skypulse.weather.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

object WeatherUtils {

    data class WeatherInfo(
        val description: String,
        val icon: String,
        val isDay: Boolean = true
    )

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
            skycon == null -> if (isDay) listOf(SunnyTop, SunnyBottom) else listOf(SunnyNightTop, SunnyNightBottom)
            skycon.contains("CLEAR") -> if (isDay) listOf(SunnyTop, SunnyBottom) else listOf(SunnyNightTop, SunnyNightBottom)
            skycon.contains("PARTLY_CLOUDY") -> if (isDay) listOf(PartialCloudTop, PartialCloudBottom) else listOf(PartialCloudNightTop, PartialCloudNightBottom)
            skycon.contains("CLOUDY") -> if (isDay) listOf(CloudyTop, CloudyBottom) else listOf(CloudyNightTop, CloudyNightBottom)
            skycon.contains("RAIN") || skycon.contains("STORM") -> if (isDay) listOf(RainyTop, RainyBottom) else listOf(RainyNightTop, RainyNightBottom)
            skycon.contains("SNOW") -> if (isDay) listOf(SnowyTop, SnowyBottom) else listOf(SnowyNightTop, SnowyNightBottom)
            skycon.contains("HAZE") || skycon == "FOG" -> listOf(HazeTop, HazeBottom)
            skycon == "WIND" -> listOf(WindyTop, WindyBottom)
            else -> if (isDay) listOf(SunnyTop, SunnyBottom) else listOf(SunnyNightTop, SunnyNightBottom)
        }
    }

    fun formatTemperature(temp: Double?): String {
        if (temp == null) return "--"
        return "${kotlin.math.round(temp).toInt()}°"
    }

    fun isBrightBackground(skycon: String?): Boolean {
        val isDay = isCurrentlyDay()
        return isDay && (skycon == null || skycon.contains("CLEAR") || skycon.contains("PARTLY_CLOUDY"))
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
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val date = inputFormat.parse(datetime) ?: return ""
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
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFormat.parse(dateStr) ?: return ""
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
