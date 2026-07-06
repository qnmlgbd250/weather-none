package com.skypulse.weather.util

import androidx.compose.ui.graphics.Color
import com.skypulse.weather.model.AstroTime
import com.skypulse.weather.model.DailyAstro
import com.skypulse.weather.model.DailyForecast
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class WeatherUtilsTest {

    // ============ formatTemperature ============

    @Test
    fun `formatTemperature returns -- for null`() {
        assertEquals("--", WeatherUtils.formatTemperature(null))
    }

    @Test
    fun `formatTemperature rounds and appends degree sign`() {
        assertEquals("25°", WeatherUtils.formatTemperature(25.0))
        assertEquals("25°", WeatherUtils.formatTemperature(25.4))
        assertEquals("26°", WeatherUtils.formatTemperature(25.5))
    }

    @Test
    fun `formatTemperature handles negative values`() {
        assertEquals("-5°", WeatherUtils.formatTemperature(-5.0))
        assertEquals("0°", WeatherUtils.formatTemperature(0.0))
    }

    @Test
    fun `formatTemperature handles extremes`() {
        assertEquals("45°", WeatherUtils.formatTemperature(45.0))
        assertEquals("-30°", WeatherUtils.formatTemperature(-30.0))
    }

    // ============ formatHumidity ============

    @Test
    fun `formatHumidity returns -- for null`() {
        assertEquals("--", WeatherUtils.formatHumidity(null))
    }

    @Test
    fun `formatHumidity converts decimal to percentage`() {
        assertEquals("65%", WeatherUtils.formatHumidity(0.65))
        assertEquals("100%", WeatherUtils.formatHumidity(1.0))
        assertEquals("0%", WeatherUtils.formatHumidity(0.0))
    }

    @Test
    fun `formatHumidity rounds correctly`() {
        assertEquals("56%", WeatherUtils.formatHumidity(0.567))
    }

    // ============ formatWindSpeed ============

    @Test
    fun `formatWindSpeed returns -- for null`() {
        assertEquals("--", WeatherUtils.formatWindSpeed(null))
    }

    @Test
    fun `formatWindSpeed classifies calm wind`() {
        assertEquals("0", WeatherUtils.formatWindSpeed(0.5))
        assertEquals("0", WeatherUtils.formatWindSpeed(0.0))
    }

    @Test
    fun `formatWindSpeed classifies level 1`() {
        assertEquals("1", WeatherUtils.formatWindSpeed(3.0))
        assertEquals("1", WeatherUtils.formatWindSpeed(5.0))
        assertEquals("2", WeatherUtils.formatWindSpeed(5.9))
    }

    @Test
    fun `formatWindSpeed classifies strong wind`() {
        assertEquals("6", WeatherUtils.formatWindSpeed(45.0))
        assertEquals("12", WeatherUtils.formatWindSpeed(120.0))
    }

    // ============ formatWindDirection ============

    @Test
    fun `formatWindDirection returns empty for null`() {
        assertEquals("", WeatherUtils.formatWindDirection(null))
    }

    @Test
    fun `formatWindDirection returns north for 0 degrees`() {
        assertEquals("北风", WeatherUtils.formatWindDirection(0.0))
    }

    @Test
    fun `formatWindDirection returns east for 90 degrees`() {
        assertEquals("东风", WeatherUtils.formatWindDirection(90.0))
    }

    @Test
    fun `formatWindDirection returns south for 180 degrees`() {
        assertEquals("南风", WeatherUtils.formatWindDirection(180.0))
    }

    @Test
    fun `formatWindDirection returns west for 270 degrees`() {
        assertEquals("西风", WeatherUtils.formatWindDirection(270.0))
    }

    @Test
    fun `formatWindDirection wraps around 360 to north`() {
        assertEquals("北风", WeatherUtils.formatWindDirection(350.0))
    }

    @Test
    fun `formatWindDirection returns northeast for 45 degrees`() {
        assertEquals("东北风", WeatherUtils.formatWindDirection(45.0))
    }

    // ============ formatPressure ============

    @Test
    fun `formatPressure returns -- for null`() {
        assertEquals("--", WeatherUtils.formatPressure(null))
    }

    @Test
    fun `formatPressure converts Pa to hPa with Chinese unit`() {
        assertEquals("1013 百帕", WeatherUtils.formatPressure(101300.0))
        assertEquals("1013 百帕", WeatherUtils.formatPressure(101350.0))
    }

    // ============ formatVisibility ============

    @Test
    fun `formatVisibility returns -- for null`() {
        assertEquals("--", WeatherUtils.formatVisibility(null))
    }

    @Test
    fun `formatVisibility formats meters below 1000`() {
        assertEquals("500 m", WeatherUtils.formatVisibility(500.0))
        assertEquals("999 m", WeatherUtils.formatVisibility(999.0))
    }

    @Test
    fun `formatVisibility formats kilometers above 1000`() {
        assertEquals("1.0 km", WeatherUtils.formatVisibility(1000.0))
        assertEquals("15.3 km", WeatherUtils.formatVisibility(15300.0))
    }

    // ============ formatHourShort ============

    @Test
    fun `formatHourShort returns empty for null`() {
        assertEquals("", WeatherUtils.formatHourShort(null))
    }

    @Test
    fun `formatHourShort returns empty for invalid string`() {
        assertEquals("", WeatherUtils.formatHourShort("not-a-date"))
    }

    @Test
    fun `formatHourShort extracts hour from valid datetime`() {
        val result = WeatherUtils.formatHourShort("2026-06-01T14:30")
        assertTrue(result.endsWith(":00"))
        assertEquals("14:00", result)
    }

    // ============ formatWeekday ============

    @Test
    fun `formatWeekday returns empty for null`() {
        assertEquals("", WeatherUtils.formatWeekday(null))
    }

    @Test
    fun `formatWeekday returns empty for invalid string`() {
        assertEquals("", WeatherUtils.formatWeekday("bad-date"))
    }

    // ============ isTomorrow ============

    @Test
    fun `isTomorrow returns false for null`() {
        assertFalse(WeatherUtils.isTomorrow(null))
    }

    @Test
    fun `isTomorrow returns false for invalid string`() {
        assertFalse(WeatherUtils.isTomorrow("invalid"))
    }

    // ============ isCurrentlyDay ============

    @Test
    fun `isCurrentlyDay returns boolean without crash`() {
        // Just verify it doesn't throw; actual value depends on current time
        val result = WeatherUtils.isCurrentlyDay()
        assertTrue(result || !result)
    }

    @Test
    fun `isCurrentlyDay uses daily sunrise and sunset when available`() {
        val daily = DailyForecast(
            astro = listOf(
                DailyAstro(
                    date = "2026-07-05",
                    sunrise = AstroTime("07:30"),
                    sunset = AstroTime("17:20")
                )
            )
        )

        assertFalse(WeatherUtils.isCurrentlyDay(daily, calendarAt("2026-07-05", 7, 0)))
        assertTrue(WeatherUtils.isCurrentlyDay(daily, calendarAt("2026-07-05", 12, 0)))
        assertFalse(WeatherUtils.isCurrentlyDay(daily, calendarAt("2026-07-05", 18, 0)))
    }

    @Test
    fun `isCurrentlyDay falls back to fixed window when astro is missing`() {
        assertTrue(WeatherUtils.isCurrentlyDay(null, calendarAt("2026-07-05", 12, 0)))
        assertFalse(WeatherUtils.isCurrentlyDay(null, calendarAt("2026-07-05", 23, 0)))
    }

    // ============ getWeatherInfo ============

    @Test
    fun `getWeatherInfo returns correct info for CLEAR_DAY`() {
        val info = WeatherUtils.getWeatherInfo("CLEAR_DAY")
        assertEquals("晴", info.description)
        assertEquals("clear-day", info.icon)
        assertTrue(info.isDay)
    }

    @Test
    fun `getWeatherInfo returns correct info for CLEAR_NIGHT`() {
        val info = WeatherUtils.getWeatherInfo("CLEAR_NIGHT")
        assertEquals("晴", info.description)
        assertEquals("clear-night", info.icon)
        assertFalse(info.isDay)
    }

    @Test
    fun `getWeatherInfo returns correct info for CLOUDY`() {
        val info = WeatherUtils.getWeatherInfo("CLOUDY", hour = 12)
        assertEquals("阴", info.description)
        assertEquals("overcast", info.icon)
        assertTrue(info.isDay)
    }

    @Test
    fun `getWeatherInfo returns correct info for LIGHT_RAIN`() {
        val info = WeatherUtils.getWeatherInfo("LIGHT_RAIN")
        assertEquals("小雨", info.description)
        assertEquals("drizzle", info.icon)
    }

    @Test
    fun `getWeatherInfo returns correct info for HEAVY_RAIN`() {
        val info = WeatherUtils.getWeatherInfo("HEAVY_RAIN")
        assertEquals("大雨", info.description)
        assertEquals("extreme-rain", info.icon)
    }

    @Test
    fun `getWeatherInfo returns correct info for STORM_RAIN`() {
        val info = WeatherUtils.getWeatherInfo("STORM_RAIN")
        assertEquals("暴雨", info.description)
        assertEquals("thunderstorms-rain", info.icon)
    }

    @Test
    fun `getWeatherInfo returns correct info for LIGHT_SNOW`() {
        val info = WeatherUtils.getWeatherInfo("LIGHT_SNOW")
        assertEquals("小雪", info.description)
        assertEquals("snow", info.icon)
    }

    @Test
    fun `getWeatherInfo returns correct info for HEAVY_SNOW`() {
        val info = WeatherUtils.getWeatherInfo("HEAVY_SNOW")
        assertEquals("大雪", info.description)
        assertEquals("extreme-snow", info.icon)
    }

    @Test
    fun `getWeatherInfo returns unknown for unrecognized skycon`() {
        val info = WeatherUtils.getWeatherInfo("UNKNOWN_WEATHER")
        assertEquals("未知", info.description)
        assertEquals("overcast", info.icon)
    }

    @Test
    fun `getWeatherInfo returns unknown for null`() {
        val info = WeatherUtils.getWeatherInfo(null)
        assertEquals("未知", info.description)
    }

    @Test
    fun `getWeatherInfo uses hour parameter for isDay`() {
        val dayInfo = WeatherUtils.getWeatherInfo("CLOUDY", hour = 12)
        assertTrue(dayInfo.isDay)

        val nightInfo = WeatherUtils.getWeatherInfo("CLOUDY", hour = 22)
        assertFalse(nightInfo.isDay)
    }

    @Test
    fun `getWeatherInfo handles PARTLY_CLOUDY variants`() {
        val day = WeatherUtils.getWeatherInfo("PARTLY_CLOUDY_DAY")
        assertEquals("多云", day.description)
        assertEquals("partly-cloudy-day", day.icon)
        assertTrue(day.isDay)

        val night = WeatherUtils.getWeatherInfo("PARTLY_CLOUDY_NIGHT")
        assertEquals("多云", night.description)
        assertEquals("partly-cloudy-night", night.icon)
        assertFalse(night.isDay)
    }

    @Test
    fun `getWeatherInfo handles haze variants`() {
        assertEquals("轻度霾", WeatherUtils.getWeatherInfo("LIGHT_HAZE").description)
        assertEquals("中度霾", WeatherUtils.getWeatherInfo("MODERATE_HAZE").description)
        assertEquals("重度霾", WeatherUtils.getWeatherInfo("HEAVY_HAZE").description)
    }

    @Test
    fun `getWeatherInfo handles wind`() {
        val info = WeatherUtils.getWeatherInfo("WIND")
        assertEquals("大风", info.description)
        assertEquals("wind", info.icon)
    }

    @Test
    fun `getWeatherInfo handles special weather`() {
        assertEquals("雾", WeatherUtils.getWeatherInfo("FOG").description)
        assertEquals("浮尘", WeatherUtils.getWeatherInfo("DUST").description)
        assertEquals("沙尘", WeatherUtils.getWeatherInfo("SAND").description)
        assertEquals("雨夹雪", WeatherUtils.getWeatherInfo("SLEET").description)
        assertEquals("雷阵雨", WeatherUtils.getWeatherInfo("THUNDER_SHOWER").description)
    }

    // ============ getWeatherGradient ============

    @Test
    fun `getWeatherGradient returns non-empty list for any skycon`() {
        val skycons = listOf(
            null, "CLEAR_DAY", "CLEAR_NIGHT", "PARTLY_CLOUDY_DAY",
            "CLOUDY", "LIGHT_RAIN", "HEAVY_RAIN", "LIGHT_SNOW",
            "HEAVY_SNOW", "LIGHT_HAZE", "FOG", "WIND", "DUST", "UNKNOWN"
        )
        skycons.forEach { skycon ->
            val dayGradient = WeatherUtils.getWeatherGradient(skycon, isDay = true)
            assertTrue("Day gradient for $skycon should not be empty", dayGradient.isNotEmpty())

            val nightGradient = WeatherUtils.getWeatherGradient(skycon, isDay = false)
            assertTrue("Night gradient for $skycon should not be empty", nightGradient.isNotEmpty())
        }
    }

    @Test
    fun `getWeatherGradient returns same for null and CLEAR_DAY`() {
        val nullGradient = WeatherUtils.getWeatherGradient(null, isDay = true)
        val clearGradient = WeatherUtils.getWeatherGradient("CLEAR_DAY", isDay = true)
        assertEquals(nullGradient, clearGradient)
    }

    // ============ getPrecipitationIconColor ============

    @Test
    fun `getPrecipitationIconColor keeps original blue on bright day`() {
        assertEquals(Color(0xFF0A5AD4), WeatherUtils.getPrecipitationIconColor("CLEAR_DAY", isDay = true))
    }

    @Test
    fun `getPrecipitationIconColor uses pale rain color on rainy day`() {
        assertEquals(Color(0xFFEAF7FF), WeatherUtils.getPrecipitationIconColor("MODERATE_RAIN", isDay = true))
    }

    @Test
    fun `getPrecipitationIconColor uses white at night`() {
        assertEquals(Color.White, WeatherUtils.getPrecipitationIconColor("LIGHT_RAIN", isDay = false))
    }

    private fun calendarAt(date: String, hour: Int, minute: Int): Calendar {
        val parts = date.split("-").map { it.toInt() }
        return Calendar.getInstance().apply {
            clear()
            set(parts[0], parts[1] - 1, parts[2], hour, minute, 0)
        }
    }
}
