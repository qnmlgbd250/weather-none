package com.skypulse.weather.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.model.City
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.sync.RefreshManager
import com.skypulse.weather.sync.SyncReason
import com.skypulse.weather.util.FileLogger
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
        FileLogger.i(TAG, "doWork: ★ 开始执行, trigger=$trigger, runAttemptCount=$runAttemptCount, id=${id}")
        val startTime = System.currentTimeMillis()
        return try {
            // 1. 解析城市
            val cities = cityRepository.getCities()
            val firstCity = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
            FileLogger.i(TAG, "doWork: 城市列表 count=${cities.size}, " +
                "firstCity=${firstCity?.name}, cityId=${firstCity?.id}, " +
                "isCurrentLocation=${firstCity?.isCurrentLocation}")

            if (firstCity != null) {
                val displayName = resolveDisplayName(firstCity)
                FileLogger.i(TAG, "doWork: displayName=$displayName")

                // 2. 从 Room 读取缓存并立即渲染
                val cached = repository.getWeatherFromCache(firstCity.id)
                FileLogger.i(TAG, "doWork: [步骤1] Room缓存读取完成, " +
                    "有数据=${cached != null}, " +
                    "skycon=${cached?.result?.realtime?.skycon}, " +
                    "temp=${cached?.result?.realtime?.temperature}")
                WeatherWidgetUpdater.updateAll(applicationContext, cached, displayName)



                // 4. 通过 RefreshManager 请求同步
                val reason = when (trigger) {
                    "boot" -> SyncReason.BOOT_COMPLETED
                    "onetime" -> SyncReason.WIDGET_CREATED
                    else -> SyncReason.PERIODIC
                }
                FileLogger.i(TAG, "doWork: [步骤3] 请求同步, reason=$reason, forWidget=true")
                refreshManager.requestSync(reason, forWidget = true)
                FileLogger.i(TAG, "doWork: [步骤3] 同步请求完成")

                // 5. 同步完成后重新读取并渲染
                val freshWeather = repository.getWeatherFromCache(firstCity.id)
                val freshName = resolveDisplayName(firstCity)
                val dataChanged = cached?.result?.realtime?.temperature !=
                    freshWeather?.result?.realtime?.temperature
                FileLogger.i(TAG, "doWork: [步骤4] 同步后读取完成, " +
                    "有数据=${freshWeather != null}, " +
                    "skycon=${freshWeather?.result?.realtime?.skycon}, " +
                    "temp=${freshWeather?.result?.realtime?.temperature}, " +
                    "displayName=$freshName, 数据变化=$dataChanged")
                WeatherWidgetUpdater.updateAll(applicationContext, freshWeather, freshName)

            } else {
                FileLogger.w(TAG, "doWork: 无城市数据，渲染空状态")
                WeatherWidgetUpdater.updateAll(applicationContext, null, null)
            }
            val elapsed = System.currentTimeMillis() - startTime
            FileLogger.i(TAG, "doWork: ★ 执行完成, 耗时=${elapsed}ms, trigger=$trigger")
            Result.success()
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            FileLogger.e(TAG, "doWork: ★ 执行异常, 耗时=${elapsed}ms, trigger=$trigger", e)
            try {
                WeatherWidgetUpdater.updateAll(applicationContext, null, null)
                FileLogger.d(TAG, "doWork: 异常后渲染空状态完成")
            } catch (e2: Exception) {
                FileLogger.e(TAG, "doWork: 异常后渲染空状态也失败", e2)
            }
            Result.success()
        }
    }

    companion object {
        private const val TAG = "WidgetWorker"
    }

    /**
     * 解析小组件显示的城市名。
     * 过滤"未知位置"和"定位中..."等无效名称，始终返回用户可读的名称。
     */
    private fun resolveDisplayName(city: City): String {
        if (!city.isCurrentLocation) return city.name
        val cachedName = locationManager.getCachedLocation()?.name
        // 优先用缓存中的定位名（过滤无效值）
        if (cachedName != null && cachedName != "未知位置" && cachedName != "当前定位") {
            return cachedName
        }
        // 其次用 Room 中的城市名（过滤无效值）
        if (city.name != "未知位置" && city.name != "当前定位") {
            return city.name
        }
        // 兜底：Room 中的名字（即使是"当前定位"，也比"未知位置"好）
        return city.name
    }
}
