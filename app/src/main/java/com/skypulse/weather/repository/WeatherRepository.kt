package com.skypulse.weather.repository

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.api.CaiyunApi
import com.skypulse.weather.model.HourlyAqiValue
import com.skypulse.weather.model.HourlyLifeIndex
import com.skypulse.weather.model.HourlySkycon
import com.skypulse.weather.model.HourlyUvItem
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.model.HourlyWind
import com.skypulse.weather.model.WeatherResponse
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class WeatherRepository @Inject constructor(
    private val api: CaiyunApi
) {

    companion object {
        val CAIYUN_TOKEN: String get() = BuildConfig.CAIYUN_TOKEN
    }

    suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean = false
    ): Result<WeatherResponse> {
        return try {
            val response = api.getWeather(
                token = CAIYUN_TOKEN,
                longitude = longitude,
                latitude = latitude,
                span = 16,
                alert = true,
                dailyStart = if (includeYesterday) -1 else null,
                hourlySteps = if (includeYesterday) 72 else 24,
                lang = "zh_CN",
                version = "7.59.0"
            )
            if (response.status == "ok") {
                Result.success(if (includeYesterday) response.withCurrentHourlyWindow() else response)
            } else {
                Result.failure(Exception("API error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun WeatherResponse.withCurrentHourlyWindow(): WeatherResponse {
        val hourly = result?.hourly ?: return this
        val threshold = currentHour()
        val filteredHourly = hourly.copy(
            precipitation = hourly.precipitation?.filterHourlyValuesFrom(threshold),
            temperature = hourly.temperature?.filterHourlyValuesFrom(threshold),
            apparent_temperature = hourly.apparent_temperature?.filterHourlyValuesFrom(threshold),
            wind = hourly.wind?.filterHourlyWindFrom(threshold),
            humidity = hourly.humidity?.filterHourlyValuesFrom(threshold),
            cloudrate = hourly.cloudrate?.filterHourlyValuesFrom(threshold),
            skycon = hourly.skycon?.filterHourlySkyconFrom(threshold),
            pressure = hourly.pressure?.filterHourlyValuesFrom(threshold),
            visibility = hourly.visibility?.filterHourlyValuesFrom(threshold),
            dswrf = hourly.dswrf?.filterHourlyValuesFrom(threshold),
            air_quality = hourly.air_quality?.copy(
                aqi = hourly.air_quality.aqi?.filterHourlyAqiFrom(threshold),
                pm25 = hourly.air_quality.pm25?.filterHourlyValuesFrom(threshold)
            ),
            life_index = hourly.life_index?.copy(
                ultraviolet = hourly.life_index.ultraviolet?.filterHourlyUvFrom(threshold)
            )
        )
        return copy(result = result.copy(hourly = filteredHourly))
    }

    private fun WeatherResponse.currentHour(): OffsetDateTime {
        val offset = resultOffset()
        val epochSeconds = server_time ?: Instant.now().epochSecond
        return Instant.ofEpochSecond(epochSeconds)
            .atOffset(offset)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
    }

    private fun WeatherResponse.resultOffset(): ZoneOffset {
        return ZoneOffset.ofTotalSeconds(tzshift ?: 8 * 60 * 60)
    }

    private fun parseDateTime(value: String?): OffsetDateTime? {
        if (value == null) return null
        return try {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (_: Exception) {
            null
        }
    }

    private fun List<HourlyValue>.filterHourlyValuesFrom(threshold: OffsetDateTime): List<HourlyValue> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }

    private fun List<HourlyWind>.filterHourlyWindFrom(threshold: OffsetDateTime): List<HourlyWind> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }

    private fun List<HourlySkycon>.filterHourlySkyconFrom(threshold: OffsetDateTime): List<HourlySkycon> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }

    private fun List<HourlyAqiValue>.filterHourlyAqiFrom(threshold: OffsetDateTime): List<HourlyAqiValue> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }

    private fun List<HourlyUvItem>.filterHourlyUvFrom(threshold: OffsetDateTime): List<HourlyUvItem> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }
}
