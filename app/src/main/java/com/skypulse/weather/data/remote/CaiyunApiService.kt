package com.skypulse.weather.data.remote

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.model.WeatherResponse
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 彩云天气 API 实现。
 *
 * 封装 CaiyunApi（Retrofit 接口），处理 token 注入和版本号。
 * 未来切换 API 提供商时，只需新建一个 WeatherApiService 实现类。
 */
@Singleton
class CaiyunApiService @Inject constructor(
    private val caiyunApi: CaiyunApi
) : WeatherApiService {

    companion object {
        private const val APP_VERSION = "7.59.0"
    }

    override suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        span: Int,
        alert: Boolean,
        dailyStart: Int?,
        hourlySteps: Int,
        lang: String
    ): WeatherResponse {
        return caiyunApi.getWeather(
            token = BuildConfig.CAIYUN_TOKEN,
            longitude = longitude,
            latitude = latitude,
            span = span,
            alert = alert,
            dailyStart = dailyStart,
            hourlySteps = hourlySteps,
            lang = lang,
            version = APP_VERSION
        )
    }
}
