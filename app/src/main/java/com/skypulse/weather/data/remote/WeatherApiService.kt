package com.skypulse.weather.data.remote

import com.skypulse.weather.model.WeatherResponse

/**
 * 天气 API 的抽象接口。
 *
 * 为未来切换天气提供商（和风天气、OpenWeather 等）做准备。
 * 当前唯一实现：CaiyunApiService（彩云天气）。
 */
interface WeatherApiService {

    /**
     * 获取天气数据。
     *
     * @param longitude 经度
     * @param latitude 纬度
     * @param span 预报天数
     * @param alert 是否包含预警
     * @param dailyStart 每日预报起始日（-1 = 包含昨天）
     * @param hourlySteps 小时预报步数
     * @param lang 语言
     * @return 天气响应
     */
    suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        span: Int = 16,
        alert: Boolean = true,
        dailyStart: Int? = null,
        hourlySteps: Int = 24,
        lang: String = "zh_CN"
    ): WeatherResponse
}
