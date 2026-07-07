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
    suspend fun refreshWithLocation(highAccuracy: Boolean = false): SyncResult {
        return syncManager.refreshWeatherWithLocation(highAccuracy = highAccuracy)
    }

    /**
     * 首页快路径：优先用已确认的当前定位坐标刷新天气，不把完整定位链路放进用户可见等待中。
     */
    suspend fun refreshCurrentLocationFast(): SyncResult {
        return syncManager.refreshCurrentLocationFast()
    }

    /**
     * 后台校准当前位置。用于保持当前定位最终准确，但不阻塞首页刷新体验。
     */
    suspend fun calibrateCurrentLocation(force: Boolean = false): SyncResult {
        return syncManager.calibrateCurrentLocation(force = force)
    }

    /**
     * 使用已有的真实定位缓存或第一个手动城市获取天气。
     * 不再用北京作为当前定位的隐式兜底，避免污染 current_location 缓存。
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

    suspend fun isFreshEnough(cityId: String?): Boolean {
        return syncManager.isFreshEnough(cityId)
    }
}
