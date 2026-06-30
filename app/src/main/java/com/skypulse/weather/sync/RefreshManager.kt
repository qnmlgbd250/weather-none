package com.skypulse.weather.sync

import android.util.Log
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.repository.CityRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一刷新入口。
 *
 * 所有刷新来源（Widget、Notification、Home、开机广播等）
 * 统一调用 requestSync()，由 RefreshManager 决定是否需要联网。
 *
 * RefreshManager 不直接联网，委托 WeatherSyncManager 执行。
 */
@Singleton
class RefreshManager @Inject constructor(
    private val syncManager: WeatherSyncManager,
    private val locationManager: LocationManager,
    private val cityRepository: CityRepository,
) {

    companion object {
        private const val TAG = "RefreshManager"

        /** 全局最小同步间隔：60 秒内不重复同步 */
        private const val GLOBAL_SYNC_INTERVAL_MS = 60_000L

        /** 天气缓存 TTL：10 分钟过期则需要刷新 */
        private const val WEATHER_TTL_MS = 10 * 60 * 1000L
    }

    /** 同步执行锁：确保同一时间只有一个同步任务 */
    private val syncMutex = Mutex()

    /** 上次成功同步的时间戳 */
    @Volatile
    private var lastSyncTime: Long = 0L

    /** 当前是否正在同步 */
    @Volatile
    var isSyncing: Boolean = false
        private set

    /**
     * 统一刷新请求。
     *
     * @param reason 触发来源
     * @param force 是否跳过限流（如用户手动刷新）
     * @return SyncResult
     */
    suspend fun requestSync(reason: SyncReason, force: Boolean = false): SyncResult {
        // 1. 并发控制：已有同步任务在执行中
        if (isSyncing && !force) {
            Log.d(TAG, "requestSync($reason): 已有同步任务在执行，跳过")
            return SyncResult.RateLimited
        }

        return syncMutex.withLock {
            // 2. 全局限流：距离上次同步不足 60 秒
            if (!force) {
                val elapsed = System.currentTimeMillis() - lastSyncTime
                if (elapsed < GLOBAL_SYNC_INTERVAL_MS) {
                    Log.d(TAG, "requestSync($reason): 距上次同步仅 ${elapsed}ms，跳过")
                    return@withLock SyncResult.RateLimited
                }
            }

            // 3. 缓存检查：天气数据未过期则跳过
            if (!force) {
                val city = cityRepository.getCurrentLocationCity()
                    ?: cityRepository.getCities().firstOrNull()
                if (city != null) {
                    val lastFetch = syncManager.getLastFetchTime(city.id)
                    val cacheAge = System.currentTimeMillis() - lastFetch
                    if (lastFetch > 0 && cacheAge < WEATHER_TTL_MS) {
                        Log.d(TAG, "requestSync($reason): 缓存未过期 (${cacheAge / 1000}s)，跳过")
                        return@withLock SyncResult.RateLimited
                    }
                }
            }

            // 4. 执行同步
            isSyncing = true
            try {
                Log.i(TAG, "requestSync($reason): 开始同步")
                val result = syncManager.refreshWeatherWithLocation()
                if (result is SyncResult.Success) {
                    lastSyncTime = System.currentTimeMillis()
                    Log.i(TAG, "requestSync($reason): 同步成功")
                } else {
                    Log.w(TAG, "requestSync($reason): 同步失败 - $result")
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "requestSync($reason): 同步异常", e)
                SyncResult.Error(e.message ?: "同步异常")
            } finally {
                isSyncing = false
            }
        }
    }
}

/**
 * 刷新触发来源。
 */
enum class SyncReason {
    BOOT_COMPLETED,      // 开机广播
    PERIODIC,            // 定时刷新（Widget 10分钟 / Notification 30分钟）
    MANUAL,              // 用户手动点击刷新
    LOCATION_CHANGED,    // 定位变化
    CITY_CHANGED,        // 城市切换
    APP_RESUME,          // App 从后台恢复
    WIDGET_CREATED,      // 首次添加 Widget
}
