package com.skypulse.weather.sync

import android.util.Log
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.util.FileLogger
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
    private val cityRepository: CityRepository,
    private val weatherRepository: WeatherRepository,
) {

    companion object {
        private const val TAG = "RefreshManager"
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
     * @param forWidget 是否使用小组件专用定位流程（3层兜底：AMap → FusedLocation → 原生 LocationManager）
     * @return SyncResult
     */
    suspend fun requestSync(reason: SyncReason, force: Boolean = false, forWidget: Boolean = false): SyncResult {
        FileLogger.i(TAG, "requestSync($reason): ★ 请求开始, force=$force, forWidget=$forWidget, isSyncing=$isSyncing")
        val startTime = System.currentTimeMillis()

        // 1. 并发控制：已有同步任务在执行中
        if (isSyncing && !force) {
            FileLogger.i(TAG, "requestSync($reason): [检查1] 已有同步任务在执行，跳过")
            Log.d(TAG, "requestSync($reason): 已有同步任务在执行，跳过")
            return SyncResult.RateLimited
        }

        return syncMutex.withLock {
            // 2. 全局限流：距离上次同步不足 120 秒
            if (!force) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastSyncTime
                FileLogger.i(TAG, "requestSync($reason): [检查2] 距上次同步=${elapsed}ms, 限流阈值=${RefreshPolicy.GLOBAL_SYNC_INTERVAL_MS}ms")
                if (RefreshPolicy.isGlobalRateLimited(now, lastSyncTime, force)) {
                    FileLogger.i(TAG, "requestSync($reason): [检查2] 距上次同步仅 ${elapsed}ms，跳过")
                    Log.d(TAG, "requestSync($reason): 距上次同步仅 ${elapsed}ms，跳过")
                    return@withLock SyncResult.RateLimited
                }
            }

            // 3. 缓存检查：天气数据未过期则跳过（Repository 决定缓存策略）
            if (!force) {
                val city = cityRepository.getCurrentLocationCity()
                    ?: cityRepository.getCities().firstOrNull()
                if (city != null) {
                    val isStale = weatherRepository.isCacheStale(city.id, RefreshPolicy.WEATHER_TTL_MS)
                    val lastUpdated = weatherRepository.getLastFetchTime(city.id)
                    val cacheAge = System.currentTimeMillis() - lastUpdated
                    FileLogger.i(TAG, "requestSync($reason): [检查3] 城市=${city.name}, cityId=${city.id}, " +
                        "缓存年龄=${cacheAge}ms, TTL=${RefreshPolicy.WEATHER_TTL_MS}ms, isStale=$isStale")
                    if (RefreshPolicy.shouldSkipFreshCache(isStale, force)) {
                        FileLogger.i(TAG, "requestSync($reason): [检查3] 缓存未过期，跳过同步")
                        Log.d(TAG, "requestSync($reason): 缓存未过期，跳过")
                        return@withLock SyncResult.RateLimited
                    }
                } else {
                    FileLogger.w(TAG, "requestSync($reason): [检查3] 无城市数据，跳过缓存检查")
                }
            }

            // 4. 执行同步
            isSyncing = true
            try {
                FileLogger.i(TAG, "requestSync($reason): [步骤4] 开始执行同步, forWidget=$forWidget")
                Log.i(TAG, "requestSync($reason): 开始同步")
                val result = if (forWidget) {
                    syncManager.refreshWeatherWithLocationForWidget()
                } else {
                    syncManager.refreshWeatherWithLocation()
                }
                val elapsed = System.currentTimeMillis() - startTime
                if (result is SyncResult.Success) {
                    lastSyncTime = System.currentTimeMillis()
                    FileLogger.i(TAG, "requestSync($reason): [步骤4] 同步成功, 耗时=${elapsed}ms")
                    Log.i(TAG, "requestSync($reason): 同步成功")
                } else {
                    FileLogger.w(TAG, "requestSync($reason): [步骤4] 同步失败 - $result, 耗时=${elapsed}ms")
                    Log.w(TAG, "requestSync($reason): 同步失败 - $result")
                }
                result
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                FileLogger.e(TAG, "requestSync($reason): [步骤4] 同步异常, 耗时=${elapsed}ms", e)
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
