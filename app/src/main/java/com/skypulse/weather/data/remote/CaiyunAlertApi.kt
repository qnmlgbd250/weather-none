package com.skypulse.weather.data.remote

import com.skypulse.weather.model.CaiyunAlertResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * 彩云天气独立预警 API。
 * 域名: starplucker.cyapi.cn（与天气 API 不同，使用独立 Retrofit 实例）
 */
interface CaiyunAlertApi {

    @Headers("User-Agent: weather/7.59.0 (Xiaomi:M2006C3LC; android/13)")
    @GET("v3/alert/location")
    suspend fun getAlerts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): CaiyunAlertResponse
}
