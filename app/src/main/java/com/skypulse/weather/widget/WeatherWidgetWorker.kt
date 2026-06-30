package com.skypulse.weather.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.sync.RefreshManager
import com.skypulse.weather.sync.SyncReason
import com.skypulse.weather.util.FileLogger
import com.skypulse.weather.util.WeatherFileCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Widget 后台刷新 Worker。
 *
 * 职责：
 * 1. 从 Room 读取缓存并立即渲染 Widget
 * 2. 通过 RefreshManager 请求同步（不直接联网）
 * 3. 同步完成后重新渲染 Widget
 */
@HiltWorker
class WeatherWidgetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeatherRepository,
    private val cityRepository: CityRepository,
    private val refreshManager: RefreshManager,
    private val locationManager: LocationManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val trigger = inputData.getString("trigger") ?: "periodic"
        FileLogger.i("WidgetRefresh", "【WorkManager刷新】doWork 触发, trigger=$trigger, runAttemptCount=$runAttemptCount")
        return try {
            val cities = cityRepository.getCities()
            // 小组件始终显示第一个城市的天气数据
            val firstCity = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()

            if (firstCity != null) {
                // 优先使用缓存中的定位名（GPS 成功后已更新），而不是 Room 中可能过时的城市名
                val displayName = if (firstCity.isCurrentLocation) {
                    locationManager.getCachedLocation()?.name
                        ?: firstCity.name.takeIf { it != "当前定位" }
                        ?: "定位中..."
                } else {
                    firstCity.name
                }

                // 1. 从 Room 读取缓存并立即渲染
                val cached = repository.getWeatherFromCache(firstCity.id)
                WeatherWidgetUpdater.updateAll(applicationContext, cached, displayName)

                // 2. 同步写入文件缓存供 WidgetProvider.onUpdate() 读取
                if (cached != null) {
                    WeatherFileCache.save(applicationContext, firstCity.id, cached)
                }

                // 3. 通过 RefreshManager 请求同步（RefreshManager 决策是否需要联网）
                val reason = when (trigger) {
                    "boot" -> SyncReason.BOOT_COMPLETED
                    "onetime" -> SyncReason.WIDGET_CREATED
                    else -> SyncReason.PERIODIC
                }
                refreshManager.requestSync(reason)

                // 4. 无论同步结果如何，始终从 Room 重新读取并渲染
                //    确保 App 写入 Room 的最新数据能被 Widget 读到
                val freshWeather = repository.getWeatherFromCache(firstCity.id)
                val freshName = if (firstCity.isCurrentLocation) {
                    locationManager.getCachedLocation()?.name
                        ?: firstCity.name.takeIf { it != "当前定位" }
                        ?: "定位中..."
                } else {
                    firstCity.name
                }
                WeatherWidgetUpdater.updateAll(applicationContext, freshWeather, freshName)
                if (freshWeather != null) {
                    WeatherFileCache.save(applicationContext, firstCity.id, freshWeather)
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
