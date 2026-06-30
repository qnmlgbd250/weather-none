package com.skypulse.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.util.CityFileCache
import com.skypulse.weather.util.FileLogger
import com.skypulse.weather.util.WeatherFileCache
import java.util.concurrent.TimeUnit

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                enqueueWorker(context)
                enqueueOneTimeWorker(context, trigger = "boot")
            } catch (_: Exception) {}
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        FileLogger.i("WidgetRefresh", "【系统兜底刷新】onUpdate 触发, appWidgetIds=${appWidgetIds.toList()}")
        // 立即用缓存数据刷新 UI（从文件缓存读取）
        var hasWeatherData = false
        try {
            val cities = CityFileCache.load(context)
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
            val weather = city?.let { WeatherFileCache.load(context, it.id) }
            hasWeatherData = weather != null
            WeatherWidgetUpdater.updateAll(context, weather, city?.name)
        } catch (_: Exception) {
            WeatherWidgetUpdater.updateAll(context, null, null)
        }
        // 确保 periodic worker 已注册（10 分钟周期刷新）
        enqueueWorker(context)
        // 缓存为空时（首次创建 Widget），触发一次同步
        if (!hasWeatherData) {
            FileLogger.i("WidgetRefresh", "【缓存为空】触发 onetime worker")
            enqueueOneTimeWorker(context, trigger = "onetime")
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        try { enqueueWorker(context) } catch (_: Exception) {}
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        try {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(WORK_NAME_ONETIME)
            }
        } catch (_: Exception) {}
    }

    companion object {

        /**
         * 刷新所有 Widget 实例。
         *
         * @param context Application Context
         * @param weather 可选：传入的天气数据（仅用于第一个城市时才使用）
         * @param cityName 可选：城市名称（仅用于第一个城市时才使用）
         *
         * 小组件始终显示第一个城市的天气数据，忽略传入的 weather 和 cityName 参数。
         * 当 weather 为 null 或不是第一个城市时，从 WeatherFileCache 读取。
         */
        fun refresh(
            context: Context,
            weather: WeatherResponse? = null,
            cityName: String? = null
        ) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    val cities = CityFileCache.load(context)
                    val firstCity = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()

                    if (firstCity != null) {
                        // 始终使用第一个城市的天气数据
                        // 如果传入的 weather 对应的是第一个城市，则使用；否则从缓存读取
                        val isFirstCityWeather = weather != null && cityName == firstCity.name
                        val finalWeather = if (isFirstCityWeather) {
                            weather
                        } else {
                            WeatherFileCache.load(context, firstCity.id)
                        }
                        val finalCityName = firstCity.name

                        WeatherWidgetUpdater.updateAll(context, finalWeather, finalCityName)
                        // 同步写入 FileCache，确保 onUpdate() 读到最新数据
                        if (finalWeather != null) {
                            WeatherFileCache.save(context, firstCity.id, finalWeather)
                        }
                    } else {
                        WeatherWidgetUpdater.updateAll(context, null, null)
                    }
                }
            } catch (_: Exception) {}
        }

        private const val WORK_NAME = "weather_widget_periodic"
        private const val WORK_NAME_ONETIME = "weather_widget_onetime"
        private const val ONETIME_THROTTLE_MS = 10 * 60 * 1000L // 10 分钟节流

        /** Lightweight periodic refresh for location-aware widgets. */
        fun enqueueWorker(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWidgetWorker>(
                WidgetRefreshPolicy.PERIODIC_REFRESH_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** 立即触发一次独立刷新 */
        private fun enqueueOneTimeWorker(context: Context, trigger: String = "onetime") {
            val inputData = Data.Builder()
                .putString("trigger", trigger)
                .build()
            val request = OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONETIME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
