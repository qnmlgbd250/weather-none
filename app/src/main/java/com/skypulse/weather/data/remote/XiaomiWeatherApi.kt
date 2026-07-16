package com.skypulse.weather.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 小米天气实况接口。
 *
 * 用于校准彩云天气的"阴天"和"多云"偏差：
 * - 当彩云返回 CLOUDY 时，调用此接口获取中国气象局的实况天气编码。
 * - 当彩云返回 PARTLY_CLOUDY_DAY 或 PARTLY_CLOUDY_NIGHT 时，同样进行校准。
 * 若气象局判定为"晴"或不同天气，则覆盖彩云的 skycon。
 *
 * 数据源：中国气象局（weatherbj），与彩云独立。
 */
interface XiaomiWeatherApi {

    @GET("wtr-v3/weather/all")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("appKey") appKey: String,
        @Query("sign") sign: String,
        @Query("isGlobal") isGlobal: Boolean = false,
        @Query("locale") locale: String = "zh_cn",
        @Query("days") days: Int = 1
    ): XiaomiWeatherResponse
}

/**
 * 小米天气响应体 — 仅解析 current.weather 字段。
 *
 * current.weather 为中国气象局标准编码（字符串格式的数字）：
 * - "0" = 晴
 * - "1" = 多云
 * - "2" = 阴
 * - "3" = 阵雨
 * - "4" = 雷阵雨
 * - "7" = 小雨
 * - "8" = 中雨
 * - 等等
 */
@JsonClass(generateAdapter = true)
data class XiaomiWeatherResponse(
    val current: XiaomiCurrentWeather? = null
)

@JsonClass(generateAdapter = true)
data class XiaomiCurrentWeather(
    @Json(name = "weather") val weatherCode: String? = null
)

