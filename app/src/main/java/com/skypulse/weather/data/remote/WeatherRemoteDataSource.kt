package com.skypulse.weather.data.remote

import com.skypulse.weather.model.Alert
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.model.toAlertContentList
import com.skypulse.weather.util.FileLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 天气网络数据源。
 *
 * 封装 WeatherApiService 的调用，仅负责网络请求。
 * WeatherRepository 通过此类获取网络数据，自身仅负责 Room 缓存。
 *
 * 预警数据通过独立的 CaiyunAlertApi 获取（数据更完整），
 * 天气主接口的 alert 参数设为 false 以避免重复。
 */
@Singleton
class WeatherRemoteDataSource @Inject constructor(
    private val api: WeatherApiService,
    private val alertApi: CaiyunAlertApi
) {

    companion object {
        private const val TAG = "WeatherRemoteDS"
    }

    /**
     * 从网络获取天气数据（含预警）。
     *
     * 天气数据和预警数据分别请求，预警来自独立 API（starplucker）。
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
            // 1. 请求天气主数据（alert=false，预警单独请求）
            val response = api.getWeather(
                longitude = longitude,
                latitude = latitude,
                span = 16,
                alert = false,
                dailyStart = if (includeYesterday) -1 else null,
                hourlySteps = if (includeYesterday) 72 else 24
            )
            if (response.status != "ok") {
                return Result.failure(Exception("API error: ${response.status}"))
            }

            // 2. 请求独立预警 API
            val alertResponse = try {
                FileLogger.i(TAG, "预警API: 开始请求 lat=$latitude, lon=$longitude")
                val alertResult = alertApi.getAlerts(
                    latitude = latitude,
                    longitude = longitude
                )
                val alertContents = alertResult.toAlertContentList()
                FileLogger.i(TAG, "预警API: 成功, 获取到 ${alertContents.size} 条预警, " +
                    "alerts=${alertContents.map { "${it.title}(level=${it.level})" }}")
                Alert(status = "ok", content = alertContents)
            } catch (e: Exception) {
                FileLogger.e(TAG, "预警API请求失败: ${e.javaClass.simpleName}: ${e.message}", e)
                // 预警请求失败不影响天气数据
                response.result?.alert ?: Alert(status = "error", content = emptyList())
            }

            // 3. 合并天气数据和预警数据（alert 嵌套在 result 中）
            val merged = response.copy(
                result = response.result?.copy(alert = alertResponse)
            )
            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
