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
            FileLogger.i(TAG, "onReceive: 收到 BOOT_COMPLETED 广播")
            try {
                enqueueWorker(context)
                enqueueOneTimeWorker(context, trigger = "boot")
                FileLogger.i(TAG, "onReceive: BOOT — periodic + onetime worker 已入队")
            } catch (e: Exception) {
                FileLogger.e(TAG, "onReceive: BOOT 入队失败", e)
            }
        } else {
            FileLogger.d(TAG, "onReceive: 忽略非 BOOT 广播 action=${intent.action}")
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        FileLogger.i(TAG, "onUpdate: 系统触发, widgetIds=${appWidgetIds.toList()}, count=${appWidgetIds.size}")
        // 立即用缓存数据刷新 UI（从文件缓存读取）
        var hasWeatherData = false
        try {
            val cities = CityFileCache.load(context)
            FileLogger.i(TAG, "onUpdate: 城市列表加载完成, count=${cities.size}, " +
                "currentLocation=${cities.firstOrNull { it.isCurrentLocation }?.name ?: "无"}, " +
                "first=${cities.firstOrNull()?.name ?: "无"}")
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
            val weather = city?.let { WeatherFileCache.load(context, it.id) }
            hasWeatherData = weather != null
            if (weather != null) {
                val skycon = weather.result?.realtime?.skycon
                val temp = weather.result?.realtime?.temperature
                FileLogger.i(TAG, "onUpdate: 缓存数据可用, city=${city?.name}, " +
                    "cityId=${city?.id}, skycon=$skycon, temp=$temp")
            } else {
                FileLogger.w(TAG, "onUpdate: 缓存数据为空, city=${city?.name}, cityId=${city?.id}")
            }
            WeatherWidgetUpdater.updateAll(context, weather, city?.name)
            FileLogger.i(TAG, "onUpdate: UI 渲染完成")
        } catch (e: Exception) {
            FileLogger.e(TAG, "onUpdate: 读取缓存或渲染异常", e)
            WeatherWidgetUpdater.updateAll(context, null, null)
        }
        // 确保 periodic worker 已注册（10 分钟周期刷新）
        enqueueWorker(context)
        // 缓存为空时（首次创建 Widget），触发一次同步
        if (!hasWeatherData) {
            FileLogger.i(TAG, "onUpdate: 缓存为空，触发 onetime worker 进行首次同步")
            enqueueOneTimeWorker(context, trigger = "onetime")
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        FileLogger.i(TAG, "onEnabled: 首个小组件实例被放置")
        try {
            enqueueWorker(context)
            FileLogger.i(TAG, "onEnabled: periodic worker 已入队")
        } catch (e: Exception) {
            FileLogger.e(TAG, "onEnabled: periodic worker 入队失败", e)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        FileLogger.i(TAG, "onDisabled: 最后一个小组件实例被移除，清理 worker")
        try {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(WORK_NAME_ONETIME)
            }
            FileLogger.i(TAG, "onDisabled: periodic + onetime worker 已取消")
        } catch (e: Exception) {
            FileLogger.e(TAG, "onDisabled: 取消 worker 失败", e)
        }
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
            FileLogger.i(TAG, "refresh: 被调用, 来源weather=${weather != null}, 来源cityName=$cityName")
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                FileLogger.i(TAG, "refresh: 当前活跃 widgetIds=${ids.toList()}, count=${ids.size}")
                if (ids.isNotEmpty()) {
                    val cities = CityFileCache.load(context)
                    val firstCity = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
                    FileLogger.i(TAG, "refresh: 解析到 firstCity=${firstCity?.name}, " +
                        "cityId=${firstCity?.id}, isCurrentLocation=${firstCity?.isCurrentLocation}")

                    if (firstCity != null) {
                        val isFirstCityWeather = weather != null && cityName == firstCity.name
                        val finalWeather = if (isFirstCityWeather) {
                            FileLogger.d(TAG, "refresh: 使用传入的天气数据 (匹配首个城市)")
                            weather
                        } else {
                            val cached = WeatherFileCache.load(context, firstCity.id)
                            FileLogger.d(TAG, "refresh: 从文件缓存读取, " +
                                "有数据=${cached != null}, skycon=${cached?.result?.realtime?.skycon}")
                            cached
                        }
                        val finalCityName = firstCity.name

                        WeatherWidgetUpdater.updateAll(context, finalWeather, finalCityName)
                        FileLogger.i(TAG, "refresh: UI 渲染完成, city=$finalCityName")
                    } else {
                        FileLogger.w(TAG, "refresh: 无城市数据，渲染空状态")
                        WeatherWidgetUpdater.updateAll(context, null, null)
                    }
                } else {
                    FileLogger.w(TAG, "refresh: 无活跃 widget，跳过渲染")
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "refresh: 异常", e)
            }
        }

        private const val TAG = "WidgetProvider"
        private const val WORK_NAME = "weather_widget_periodic"
        private const val WORK_NAME_ONETIME = "weather_widget_onetime"
        private const val ONETIME_THROTTLE_MS = 10 * 60 * 1000L // 10 分钟节流

        /** Lightweight periodic refresh for location-aware widgets. */
        fun enqueueWorker(context: Context) {
            FileLogger.i(TAG, "enqueueWorker: 注册 periodic worker, 间隔=${WidgetRefreshPolicy.PERIODIC_REFRESH_MINUTES}分钟")
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
            FileLogger.i(TAG, "enqueueWorker: periodic worker 入队成功 (UPDATE策略)")
        }

        /** 立即触发一次独立刷新 */
        private fun enqueueOneTimeWorker(context: Context, trigger: String = "onetime") {
            FileLogger.i(TAG, "enqueueOneTimeWorker: 入队 onetime worker, trigger=$trigger")
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
            FileLogger.i(TAG, "enqueueOneTimeWorker: onetime worker 入队成功 (REPLACE策略)")
        }
    }
}
