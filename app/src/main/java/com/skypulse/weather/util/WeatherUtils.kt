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
            !isDay -> listOf(NightSkyTop, NightSkyBottom)
            skycon == null -> listOf(SunnyTop, SunnyBottom)
            skycon.contains("CLEAR") && isDay -> listOf(SunnyTop, SunnyBottom)
            skycon.contains("PARTLY_CLOUDY") -> listOf(PartialCloudTop, PartialCloudBottom)
            skycon.contains("CLOUDY") -> listOf(CloudyTop, CloudyBottom)
            skycon.contains("RAIN") || skycon.contains("STORM") -> listOf(RainyTop, RainyBottom)
            skycon.contains("SNOW") -> listOf(SnowyTop, SnowyBottom)
            skycon.contains("HAZE") || skycon == "FOG" -> listOf(HazeTop, HazeBottom)
            skycon == "WIND" -> listOf(WindyTop, WindyBottom)
            else -> listOf(SunnyTop, SunnyBottom)
        }
    }

    fun formatTemperature(temp: Double?): String {
        if (temp == null) return "--"
        return "${temp.toInt()}°"
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
