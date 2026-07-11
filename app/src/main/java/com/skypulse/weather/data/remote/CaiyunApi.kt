package com.skypulse.weather.data.remote

import com.skypulse.weather.model.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CaiyunApi {

    @GET("v2.7/{token}/{lon},{lat}/weather")
    suspend fun getWeather(
        @Path("token") token: String,
        @Path("lon") longitude: Double,
        @Path("lat") latitude: Double,
        @Query("span") span: Int = 16,
        @Query("alert") alert: Boolean = true,
        @Query("dailystart") dailyStart: Int? = null,
        @Query("hourlysteps") hourlySteps: Int = 24,
        @Query("lang") lang: String = "zh_CN",
        @Query("version") version: String = "7.59.0"
    ): WeatherResponse
}
