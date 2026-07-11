package com.skypulse.weather.data.remote

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实况天气 skycon 校准器。
 *
 * 解决彩云天气在"阴天"（CLOUDY）上的系统性偏差：
 * 当彩云返回 CLOUDY 时，调用小米天气（中国气象局数据）进行校准。
 * 若气象局判定为"晴"或"多云"，则覆盖彩云的 skycon。
 *
 * 校准范围：仅当前定位城市。
 * 校准条件：skycon == "CLOUDY" 时才触发请求。
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
        // 仅在彩云返回 CLOUDY 时触发校准
        if (skycon != "CLOUDY") {
            return skycon
        }

        FileLogger.i(TAG, "校准触发: skycon=$skycon, lon=$longitude, lat=$latitude")

        val xiaomiWeather = fetchXiaomiWeather(longitude, latitude)
        if (xiaomiWeather == null) {
            FileLogger.w(TAG, "校准失败: 小米天气请求失败，保持原值 CLOUDY")
            return skycon
        }

        val calibrated = when (xiaomiWeather) {
            CODE_CLEAR -> {
                val calibratedSkycon = if (isDay) "CLEAR_DAY" else "CLEAR_NIGHT"
                FileLogger.i(TAG, "校准生效: CLOUDY → $calibratedSkycon (小米=$xiaomiWeather/晴, isDay=$isDay)")
                calibratedSkycon
            }
            CODE_CLOUDY -> {
                val calibratedSkycon = if (isDay) "PARTLY_CLOUDY_DAY" else "PARTLY_CLOUDY_NIGHT"
                FileLogger.i(TAG, "校准生效: CLOUDY → $calibratedSkycon (小米=$xiaomiWeather/多云, isDay=$isDay)")
                calibratedSkycon
            }
            CODE_OVERCAST -> {
                FileLogger.i(TAG, "校准保持: CLOUDY (小米=$xiaomiWeather/阴，两源一致)")
                skycon
            }
            else -> {
                FileLogger.i(TAG, "校准保持: CLOUDY (小米=$xiaomiWeather/其他天气)")
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
