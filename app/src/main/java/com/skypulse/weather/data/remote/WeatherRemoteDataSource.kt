package com.skypulse.weather.data.remote

import android.os.SystemClock
import com.skypulse.weather.model.Alert
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.model.toAlertContentList
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.withTimeoutOrNull
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
        private const val ALERT_TIMEOUT_MS = 5_000L
    }

    private fun weatherI(message: String) = FileLogger.weatherI(TAG, message)
    private fun weatherW(message: String) = FileLogger.weatherW(TAG, message)
    private fun weatherE(message: String, throwable: Throwable? = null) {
        if (throwable == null) FileLogger.weatherE(TAG, message) else FileLogger.weatherE(TAG, message, throwable)
    }

    private fun elapsedSince(startMs: Long): Long = SystemClock.elapsedRealtime() - startMs

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
        val totalStartMs = SystemClock.elapsedRealtime()
        weatherI("remote_get_weather_start: lon=$longitude, lat=$latitude, includeYesterday=$includeYesterday")
        return try {
            // 1. 请求天气主数据（alert=false，预警单独请求）
            val primaryStartMs = SystemClock.elapsedRealtime()
            weatherI("primary_weather_start: lon=$longitude, lat=$latitude, span=16, alert=false, dailyStart=${if (includeYesterday) -1 else null}, hourlySteps=${if (includeYesterday) 72 else 24}")
            val response = api.getWeather(
                longitude = longitude,
                latitude = latitude,
                span = 16,
                alert = false,
                dailyStart = if (includeYesterday) -1 else null,
                hourlySteps = if (includeYesterday) 72 else 24
            )
            weatherI("primary_weather_done: elapsed=${elapsedSince(primaryStartMs)}ms, status=${response.status}, serverTime=${response.server_time}, tzshift=${response.tzshift}")
            if (response.status != "ok") {
                weatherW("primary_weather_bad_status: elapsed=${elapsedSince(primaryStartMs)}ms, status=${response.status}, total=${elapsedSince(totalStartMs)}ms")
                return Result.failure(Exception("API error: ${response.status}"))
            }

            // 2. 请求独立预警 API
            val alertResponse = try {
                FileLogger.i(TAG, "预警API: 开始请求 lat=$latitude, lon=$longitude")
                val alertStartMs = SystemClock.elapsedRealtime()
                weatherI("alert_api_start: lat=$latitude, lon=$longitude, timeout=${ALERT_TIMEOUT_MS}ms")
                val alertResult = withTimeoutOrNull(ALERT_TIMEOUT_MS) {
                    alertApi.getAlerts(
                        latitude = latitude,
                        longitude = longitude
                    )
                }
                if (alertResult != null) {
                    val alertContents = alertResult.toAlertContentList()
                    weatherI("alert_api_done: elapsed=${elapsedSince(alertStartMs)}ms, rawAlerts=${alertResult.alerts?.size ?: 0}, admins=${alertResult.admins?.size ?: 0}, activeCount=${alertContents.size}")
                    FileLogger.i(TAG, "预警API: 成功, 获取到 ${alertContents.size} 条预警, " +
                        "alerts=${alertContents.map { "${it.title}(level=${it.level})" }}")
                    Alert(status = "ok", content = alertContents)
                } else {
                    weatherW("alert_api_timeout: elapsed=${elapsedSince(alertStartMs)}ms, timeout=${ALERT_TIMEOUT_MS}ms")
                    FileLogger.w(TAG, "预警API请求超时: ${ALERT_TIMEOUT_MS}ms, 使用主天气返回的预警兜底")
                    response.result?.alert ?: Alert(status = "timeout", content = emptyList())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                weatherE("alert_api_exception: type=${e.javaClass.simpleName}, message=${e.message}, total=${elapsedSince(totalStartMs)}ms", e)
                FileLogger.e(TAG, "预警API请求失败: ${e.javaClass.simpleName}: ${e.message}", e)
                // 预警请求失败不影响天气数据
                response.result?.alert ?: Alert(status = "error", content = emptyList())
            }

            // 3. 合并天气数据和预警数据（alert 嵌套在 result 中）
            val merged = response.copy(
                result = response.result?.copy(alert = alertResponse)
            )
            weatherI("remote_get_weather_success: total=${elapsedSince(totalStartMs)}ms, alertStatus=${alertResponse.status}, alertCount=${alertResponse.content?.size ?: 0}")
            Result.success(merged)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            weatherE("remote_get_weather_failed: total=${elapsedSince(totalStartMs)}ms, type=${e.javaClass.simpleName}, message=${e.message}", e)
            Result.failure(e)
        }
    }
}
