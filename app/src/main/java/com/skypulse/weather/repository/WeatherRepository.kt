package com.skypulse.weather.repository

import com.skypulse.weather.api.CaiyunApi
import com.skypulse.weather.model.WeatherResponse
import javax.inject.Inject

class WeatherRepository @Inject constructor(
    private val api: CaiyunApi
) {

    companion object {
        const val CAIYUN_TOKEN = "Y2FpeXVuIGFuZHJpb2QgYXBp"
    }

    suspend fun getWeather(
        longitude: Double,
        latitude: Double
    ): Result<WeatherResponse> {
        return try {
            val response = api.getWeather(
                token = CAIYUN_TOKEN,
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
