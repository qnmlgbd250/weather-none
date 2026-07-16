package com.skypulse.weather.data.remote

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实况天气 skycon 校准器。
 *
 * 解决彩云天气在"阴天"（CLOUDY）和"多云"（PARTLY_CLOUDY_DAY/NIGHT）上的系统性偏差：
 * - 当彩云返回 CLOUDY 时，调用小米天气（中国气象局数据）进行校准。
 * - 当彩云返回 PARTLY_CLOUDY_DAY 或 PARTLY_CLOUDY_NIGHT 时，同样进行校准。
 * 若气象局判定为"晴"或不同天气，则覆盖彩云的 skycon。
 *
 * 校准范围：仅当前定位城市。
 * 校准条件：skycon == "CLOUDY" 或 skycon == "PARTLY_CLOUDY_DAY/NIGHT" 时才触发请求。
 * 容错策略：小米 API 超时或失败时，保持彩云原始值不变。
 */
@Singleton
class SkyconCalibrator @Inject constructor(
    private val xiaomiWeatherApi: XiaomiWeatherApi
) {

    companion object {
        private const val TAG = "SkyconCalibrator"
        private const val CALIBRATE_TIMEOUT_MS = 3_000L

        /** 中国气象局天气编码 → 彩云 skycon 映射（仅校准用到的） */
        private const val CODE_CLEAR = "0"
        private const val CODE_CLOUDY = "1"
        private const val CODE_OVERCAST = "2"
    }

    /**
     * 校准 skycon。
     *
     * @param skycon 彩云返回的 skycon 值
     * @param longitude 经度
     * @param latitude 纬度
     * @param isDay 是否处于白天，用于选择 DAY/NIGHT skycon
     * @return 校准后的 skycon 值；若无需校准或校准失败，返回原值
     */
    suspend fun calibrateIfNeeded(
        skycon: String?,
        longitude: Double,
        latitude: Double,
        isDay: Boolean
    ): String? {
        // 仅在彩云返回 CLOUDY 或 PARTLY_CLOUDY_DAY/NIGHT 时触发校准
        val needsCalibration = skycon == "CLOUDY" || 
            skycon == "PARTLY_CLOUDY_DAY" || 
            skycon == "PARTLY_CLOUDY_NIGHT"
        
        if (!needsCalibration) {
            return skycon
        }

        FileLogger.i(TAG, "校准触发: skycon=$skycon, lon=$longitude, lat=$latitude")

        val xiaomiWeather = fetchXiaomiWeather(longitude, latitude)
        if (xiaomiWeather == null) {
            FileLogger.w(TAG, "校准失败: 小米天气请求失败，保持原值 $skycon")
            return skycon
        }

        val calibrated = when (xiaomiWeather) {
            CODE_CLEAR -> {
                // 小米判定为"晴"，覆盖彩云的"阴天"或"多云"
                val calibratedSkycon = if (isDay) "CLEAR_DAY" else "CLEAR_NIGHT"
                FileLogger.i(TAG, "校准生效: $skycon → $calibratedSkycon (小米=$xiaomiWeather/晴, isDay=$isDay)")
                calibratedSkycon
            }
            CODE_CLOUDY -> {
                // 小米判定为"多云"
                if (skycon == "CLOUDY") {
                    // 彩云是"阴天"，小米是"多云"，修正为多云
                    val calibratedSkycon = if (isDay) "PARTLY_CLOUDY_DAY" else "PARTLY_CLOUDY_NIGHT"
                    FileLogger.i(TAG, "校准生效: CLOUDY → $calibratedSkycon (小米=$xiaomiWeather/多云, isDay=$isDay)")
                    calibratedSkycon
                } else {
                    // 彩云已经是"多云"，小米也确认是"多云"，保持不变
                    FileLogger.i(TAG, "校准保持: $skycon (小米=$xiaomiWeather/多云，两源一致)")
                    skycon
                }
            }
            CODE_OVERCAST -> {
                // 小米判定为"阴"
                if (skycon == "CLOUDY") {
                    // 彩云是"阴天"，小米也确认是"阴"，保持不变
                    FileLogger.i(TAG, "校准保持: CLOUDY (小米=$xiaomiWeather/阴，两源一致)")
                    skycon
                } else {
                    // 彩云是"多云"，小米是"阴"，修正为阴天
                    FileLogger.i(TAG, "校准生效: $skycon → CLOUDY (小米=$xiaomiWeather/阴, isDay=$isDay)")
                    "CLOUDY"
                }
            }
            else -> {
                // 小米是其他天气（雨、雪等），保持彩云原值
                FileLogger.i(TAG, "校准保持: $skycon (小米=$xiaomiWeather/其他天气)")
                skycon
            }
        }

        return calibrated
    }

    private suspend fun fetchXiaomiWeather(longitude: Double, latitude: Double): String? {
        return withTimeoutOrNull(CALIBRATE_TIMEOUT_MS) {
            try {
                val response = xiaomiWeatherApi.getCurrentWeather(
                    latitude = latitude,
                    longitude = longitude,
                    appKey = BuildConfig.XIAOMI_APP_KEY,
                    sign = BuildConfig.XIAOMI_SIGN
                )
                val code = response.current?.weatherCode
                FileLogger.i(TAG, "小米天气响应: weatherCode=$code")
                code
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                FileLogger.e(TAG, "小米天气请求异常: ${e.javaClass.simpleName}: ${e.message}", e)
                null
            }
        }
    }
}
