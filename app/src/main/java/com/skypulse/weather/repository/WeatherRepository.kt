package com.skypulse.weather.repository

import com.skypulse.weather.api.ApiClient
import com.skypulse.weather.model.WeatherResponse

class WeatherRepository {

    private val api = ApiClient.caiyunApi

    suspend fun getWeather(
        longitude: Double,
        latitude: Double
    ): Result<WeatherResponse> {
        return try {
            val response = api.getWeather(
                token = ApiClient.CAIYUN_TOKEN,
                longitude = longitude,
                latitude = latitude
            )
            if (response.status == "ok") {
                Result.success(response)
            } else {
                Result.failure(Exception("API error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
