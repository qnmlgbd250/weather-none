package com.skypulse.weather.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeatherResponse(
    val status: String,
    val api_version: String? = null,
    val api_status: String? = null,
    val lang: String? = null,
    val unit: String? = null,
    val tzshift: Int? = null,
    val timezone: String? = null,
    val server_time: Long? = null,
    val location: List<Double>? = null,
    val result: WeatherResult? = null
)

@JsonClass(generateAdapter = true)
data class WeatherResult(
    val realtime: RealtimeWeather? = null,
    val minutely: MinutelyForecast? = null,
    val hourly: HourlyForecast? = null,
    val daily: DailyForecast? = null,
    @Json(name = "forecast_keypoint") val forecastKeypoint: String? = null,
    val alert: Alert? = null
)

@JsonClass(generateAdapter = true)
data class Alert(
    val status: String? = null,
    val content: List<AlertContent>? = null
)

@JsonClass(generateAdapter = true)
data class AlertContent(
    val province: String? = null,
    val city: String? = null,
    val county: String? = null,
    val title: String? = null,
    val description: String? = null,
    val level: String? = null,
    val type: String? = null,
    val status: String? = null,
    val id: String? = null,
    val regionCode: String? = null,
    val areaCode: String? = null,
    val publishTime: Long? = null
)

fun List<AlertContent>.sortedByPublishTimeDescending(): List<AlertContent> {
    return sortedWith(
        compareByDescending<AlertContent> { it.publishTime ?: Long.MIN_VALUE }
            .thenBy { it.title.orEmpty() }
            .thenBy { it.id.orEmpty() }
    )
}

// ============ Realtime ============

@JsonClass(generateAdapter = true)
data class RealtimeWeather(
    val status: String? = null,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val cloudrate: Double? = null,
    val skycon: String? = null,
    val visibility: Double? = null,
    val dswrf: Double? = null,
    val wind: Wind? = null,
    val pressure: Double? = null,
    val apparent_temperature: Double? = null,
    val precipitation: Precipitation? = null,
    val air_quality: AirQuality? = null,
    val life_index: LifeIndex? = null
)

@JsonClass(generateAdapter = true)
data class Wind(
    val speed: Double? = null,
    val direction: Double? = null
)

@JsonClass(generateAdapter = true)
data class Precipitation(
    val local: PrecipitationLocal? = null,
    val nearest: PrecipitationNearest? = null
)

@JsonClass(generateAdapter = true)
data class PrecipitationLocal(
    val status: String? = null,
    val datasource: String? = null,
    val intensity: Double? = null
)

@JsonClass(generateAdapter = true)
data class PrecipitationNearest(
    val status: String? = null,
    val distance: Double? = null,
    val intensity: Double? = null
)

@JsonClass(generateAdapter = true)
data class AirQuality(
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val so2: Double? = null,
    val no2: Double? = null,
    val co: Double? = null,
    val aqi: AirQualityIndex? = null,
    val description: AirQualityDescription? = null
)

@JsonClass(generateAdapter = true)
data class AirQualityIndex(
    val chn: Double? = null,
    val usa: Double? = null
)

@JsonClass(generateAdapter = true)
data class AirQualityDescription(
    val chn: String? = null,
    val usa: String? = null
)

@JsonClass(generateAdapter = true)
data class LifeIndex(
    val ultraviolet: LifeIndexItem? = null,
    val comfort: LifeIndexItem? = null
)

@JsonClass(generateAdapter = true)
data class LifeIndexItem(
    val index: String? = null,
    val desc: String? = null
)

// ============ Minutely ============

@JsonClass(generateAdapter = true)
data class MinutelyForecast(
    val status: String? = null,
    val datasource: String? = null,
    val precipitation_2h: List<Double>? = null,
    val precipitation: List<Double>? = null,
    val probability: List<Double>? = null,
    val description: String? = null
)

// ============ Hourly ============

@JsonClass(generateAdapter = true)
data class HourlyForecast(
    val status: String? = null,
    val description: String? = null,
    val precipitation: List<HourlyValue>? = null,
    val temperature: List<HourlyValue>? = null,
    val apparent_temperature: List<HourlyValue>? = null,
    val wind: List<HourlyWind>? = null,
    val gust: List<HourlyValue>? = null,
    val humidity: List<HourlyValue>? = null,
    val cloudrate: List<HourlyValue>? = null,
    val skycon: List<HourlySkycon>? = null,
    val pressure: List<HourlyValue>? = null,
    val visibility: List<HourlyValue>? = null,
    val dswrf: List<HourlyValue>? = null,
    val air_quality: HourlyAirQuality? = null,
    val life_index: HourlyLifeIndex? = null
)

@JsonClass(generateAdapter = true)
data class HourlyLifeIndex(
    val ultraviolet: List<HourlyUvItem>? = null
)

@JsonClass(generateAdapter = true)
data class HourlyUvItem(
    val datetime: String? = null,
    val index: String? = null,
    val desc: String? = null
)

@JsonClass(generateAdapter = true)
data class HourlyValue(
    val datetime: String? = null,
    val value: Double? = null,
    val probability: Double? = null
)

@JsonClass(generateAdapter = true)
data class HourlyWind(
    val datetime: String? = null,
    val speed: Double? = null,
    val direction: Double? = null
)

@JsonClass(generateAdapter = true)
data class HourlySkycon(
    val datetime: String? = null,
    val value: String? = null
)

@JsonClass(generateAdapter = true)
data class HourlyAirQuality(
    val aqi: List<HourlyAqiValue>? = null,
    val pm25: List<HourlyValue>? = null
)

@JsonClass(generateAdapter = true)
data class HourlyAqiValue(
    val datetime: String? = null,
    val value: AirQualityIndex? = null
)

// ============ Daily ============

@JsonClass(generateAdapter = true)
data class DailyForecast(
    val status: String? = null,
    val astro: List<DailyAstro>? = null,
    val precipitation: List<DailyPrecipitation>? = null,
    val temperature: List<DailyTemperature>? = null,
    val temperature_08h_20h: List<DailyTemperature>? = null,
    val temperature_20h_32h: List<DailyTemperature>? = null,
    val wind: List<DailyWind>? = null,
    val wind_08h_20h: List<DailyWind>? = null,
    val wind_20h_32h: List<DailyWind>? = null,
    val humidity: List<DailyValue>? = null,
    val cloudrate: List<DailyValue>? = null,
    val pressure: List<DailyValue>? = null,
    val visibility: List<DailyValue>? = null,
    val dswrf: List<DailyValue>? = null,
    val skycon: List<DailySkycon>? = null,
    val skycon_08h_20h: List<DailySkycon>? = null,
    val skycon_20h_32h: List<DailySkycon>? = null,
    val air_quality: DailyAirQuality? = null,
    val life_index: DailyLifeIndex? = null
)

@JsonClass(generateAdapter = true)
data class DailyAstro(
    val date: String? = null,
    val sunrise: AstroTime? = null,
    val sunset: AstroTime? = null
)

@JsonClass(generateAdapter = true)
data class AstroTime(
    val time: String? = null
)

@JsonClass(generateAdapter = true)
data class DailyPrecipitation(
    val date: String? = null,
    val max: Double? = null,
    val min: Double? = null,
    val avg: Double? = null,
    val probability: Double? = null
)

@JsonClass(generateAdapter = true)
data class DailyTemperature(
    val date: String? = null,
    val max: Double? = null,
    val min: Double? = null,
    val avg: Double? = null
)

@JsonClass(generateAdapter = true)
data class DailyWind(
    val date: String? = null,
    val max: Wind? = null,
    val min: Wind? = null,
    val avg: Wind? = null
)

@JsonClass(generateAdapter = true)
data class DailyValue(
    val date: String? = null,
    val max: Double? = null,
    val min: Double? = null,
    val avg: Double? = null
)

@JsonClass(generateAdapter = true)
data class DailySkycon(
    val date: String? = null,
    val value: String? = null
)

@JsonClass(generateAdapter = true)
data class DailyAirQuality(
    val aqi: List<DailyAqiIndexValue>? = null,
    val pm25: List<DailyAqiValue>? = null
)

@JsonClass(generateAdapter = true)
data class DailyAqiIndexValue(
    val date: String? = null,
    val max: AirQualityIndex? = null,
    val min: AirQualityIndex? = null,
    val avg: AirQualityIndex? = null
)

@JsonClass(generateAdapter = true)
data class DailyAqiValue(
    val date: String? = null,
    val max: Double? = null,
    val min: Double? = null,
    val avg: Double? = null
)

@JsonClass(generateAdapter = true)
data class DailyLifeIndex(
    val ultraviolet: List<LifeIndexDay>? = null,
    val carWashing: List<LifeIndexDay>? = null,
    val dressing: List<LifeIndexDay>? = null,
    val comfort: List<LifeIndexDay>? = null,
    val coldRisk: List<LifeIndexDay>? = null
)

@JsonClass(generateAdapter = true)
data class LifeIndexDay(
    val date: String? = null,
    val index: String? = null,
    val desc: String? = null
)
