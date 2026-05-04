package com.skypulse.weather.api

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
        @Query("alert") alert: Boolean = true,
        @Query("dailysteps") dailySteps: Int = 15,
        @Query("hourlysteps") hourlySteps: Int = 48
    ): WeatherResponse
}
