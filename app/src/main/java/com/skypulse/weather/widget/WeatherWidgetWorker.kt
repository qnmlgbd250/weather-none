package com.skypulse.weather.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.sync.WeatherSyncManager
import com.skypulse.weather.util.FileLogger
import com.skypulse.weather.util.WeatherFileCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Widget 后台刷新 Worker。
 *
 * Phase 6 架构：通过 CityRepository 读取城市（Room SSOT），
 * 通过 Repository 读取天气（Room SSOT），委托 SyncManager 刷新。
 */
@HiltWorker
class WeatherWidgetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeatherRepository,
    private val cityRepository: CityRepository,
    private val syncManager: WeatherSyncManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val trigger = inputData.getString("trigger") ?: "periodic"
        FileLogger.i("WidgetRefresh", "【WorkManager刷新】doWork 触发, trigger=$trigger, runAttemptCount=$runAttemptCount")
        return try {
            val cities = cityRepository.getCities()
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()

            if (city != null) {
                // 1. 从 Room 读取缓存并立即渲染
                val cached = repository.getWeatherFromCache(city.id)
                WeatherWidgetUpdater.updateAll(applicationContext, cached, city.name)

                // 2. 同步写入文件缓存供 WidgetProvider.onUpdate() 读取
                if (cached != null) {
                    WeatherFileCache.save(applicationContext, city.id, cached)
                }

                // 3. 判断是否需要刷新
                val lastFetchTime = syncManager.getLastFetchTime(city.id)
                val shouldFetch = cached == null || WidgetRefreshPolicy.shouldFetchWeather(
                    distanceMeters = 0f,
                    lastFetchTimeMillis = lastFetchTime,
                    nowMillis = System.currentTimeMillis()
                )

                // 4. 需要刷新时，委托 SyncManager
                if (shouldFetch) {
                    try {
                        val result = syncManager.refreshWeatherWithLocation()
                        result.onSuccess { response ->
                            WeatherWidgetUpdater.updateAll(applicationContext, response, city.name)
                            // 同步写入文件缓存
                            WeatherFileCache.save(applicationContext, city.id, response)
                        }
                    } catch (e: Exception) {
                        Log.w("WidgetWorker", "Sync failed, using cache", e)
                    }
                }
            } else {
                WeatherWidgetUpdater.updateAll(applicationContext, null, null)
            }
            Result.success()
        } catch (_: Exception) {
            try { WeatherWidgetUpdater.updateAll(applicationContext, null, null) } catch (_: Exception) {}
            Result.success()
        }
    }
}
