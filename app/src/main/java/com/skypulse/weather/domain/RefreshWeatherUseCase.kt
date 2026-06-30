package com.skypulse.weather.domain

import com.skypulse.weather.sync.SyncResult
import com.skypulse.weather.sync.WeatherSyncManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 天气刷新的业务逻辑封装。
 *
 * 封装所有天气刷新场景，ViewModel 不再直接调用 SyncManager。
 * 职责：协调 SyncManager 完成不同场景的天气刷新。
 */
@Singleton
class RefreshWeatherUseCase @Inject constructor(
    private val syncManager: WeatherSyncManager
) {

    /**
     * 通过 GPS 定位刷新天气。
     * 完整流程：定位解析 → 更新城市坐标 → 获取天气 → 写入 Room。
     */
    suspend fun refreshWithLocation(): SyncResult {
        return syncManager.refreshWeatherWithLocation()
    }

    /**
     * 使用默认坐标（北京）获取天气。
     * 用于首次安装无定位权限时的兜底。
     */
    suspend fun refreshDefault(): SyncResult {
        return syncManager.refreshWeatherDefault()
    }

    /**
     * 为指定城市刷新天气（已知坐标）。
     */
    suspend fun refreshCity(cityId: String, longitude: Double, latitude: Double): SyncResult {
        return syncManager.refreshWeather(cityId, longitude, latitude)
    }

    /**
     * 检查指定城市是否最近已刷新过（限流判断）。
     */
    fun isRecentlyFetched(cityId: String?): Boolean {
        return syncManager.isRecentlyFetched(cityId)
    }
}
