package com.skypulse.weather.repository

import com.skypulse.weather.data.remote.WeatherApiService
import com.skypulse.weather.model.HourlyForecast
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.model.WeatherResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WeatherRepositoryTest {

    private lateinit var api: WeatherApiService
    private lateinit var repository: WeatherRepository

    @Before
    fun setup() {
        api = mockk()
        repository = WeatherRepository(api)
    }

    @Test
    fun `getWeather returns success when API returns ok status`() = runTest {
        val mockResponse = WeatherResponse(status = "ok")
        coEvery {
            api.getWeather(
                longitude = 116.4074,
                latitude = 39.9042,
                span = 16,
                alert = true,
                dailyStart = null,
                hourlySteps = 24
            )
        } returns mockResponse

        val result = repository.getWeather(116.4074, 39.9042)

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull()?.status)
    }

    @Test
    fun `getWeather returns failure when API returns non-ok status`() = runTest {
        val mockResponse = WeatherResponse(status = "error")
        coEvery {
            api.getWeather(
                longitude = any(),
                latitude = any(),
                span = any(),
                alert = any(),
                dailyStart = any(),
                hourlySteps = any()
            )
        } returns mockResponse

        val result = repository.getWeather(116.4074, 39.9042)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API error") == true)
    }

    @Test
    fun `getWeather returns failure when API throws exception`() = runTest {
        coEvery {
            api.getWeather(
                longitude = any(),
                latitude = any(),
                span = any(),
                alert = any(),
                dailyStart = any(),
                hourlySteps = any()
            )
        } throws RuntimeException("Network error")

        val result = repository.getWeather(116.4074, 39.9042)

        assertTrue(result.isFailure)
    }

    @Test
    fun `getWeather requests wrapper history and trims past hourly values`() = runTest {
        val mockResponse = WeatherResponse(
            status = "ok",
            server_time = 1781326800,
            tzshift = 8 * 60 * 60,
            result = WeatherResult(
                hourly = HourlyForecast(
                    temperature = listOf(
                        HourlyValue(datetime = "2026-06-12T23:00+08:00", value = 24.0),
                        HourlyValue(datetime = "2026-06-13T13:00+08:00", value = 28.0),
                        HourlyValue(datetime = "2026-06-13T14:00+08:00", value = 29.0)
                    )
                )
            )
        )
        coEvery {
            api.getWeather(
                longitude = 116.4074,
                latitude = 39.9042,
                span = 16,
                alert = true,
                dailyStart = -1,
                hourlySteps = 72
            )
        } returns mockResponse

        val result = repository.getWeather(116.4074, 39.9042, includeYesterday = true)

        assertTrue(result.isSuccess)
        val temperatures = result.getOrNull()?.result?.hourly?.temperature.orEmpty()
        assertEquals(2, temperatures.size)
        assertEquals("2026-06-13T13:00+08:00", temperatures.first().datetime)
        coVerify(exactly = 1) {
            api.getWeather(
                longitude = 116.4074,
                latitude = 39.9042,
                span = 16,
                alert = true,
                dailyStart = -1,
                hourlySteps = 72
            )
        }
    }
}
