package com.skypulse.weather.data.remote

import com.skypulse.weather.model.WeatherResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 天气网络数据源。
 *
 * 封装 WeatherApiService 的调用，仅负责网络请求。
 * WeatherRepository 通过此类获取网络数据，自身仅负责 Room 缓存。
 */
@Singleton
class WeatherRemoteDataSource @Inject constructor(
    private val api: WeatherApiService
) {

    /**
     * 从网络获取天气数据。
     *
     * @param longitude 经度
     * @param latitude 纬度
     * @param includeYesterday 是否包含昨天的小时数据（用于过滤当前小时之前的 数据）
     * @return 天气数据或错误
     */
    suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean = false
    ): Result<WeatherResponse> {
        return try {
            val response = api.getWeather(
                longitude = longitude,
                latitude = latitude,
                span = 16,
                alert = true,
                dailyStart = if (includeYesterday) -1 else null,
                hourlySteps = if (includeYesterday) 72 else 24
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
