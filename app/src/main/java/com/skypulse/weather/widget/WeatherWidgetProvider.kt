package com.skypulse.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.util.FileLogger
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
        // 确保 periodic worker 已注册（10 分钟周期刷新）
        enqueueWorker(context)
        // 触发一次一次性工作，通过协程在后台异步读取 Room 数据库并更新小组件
        enqueueOneTimeWorker(context, trigger = "onetime")
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
                    if (weather != null && cityName != null) {
                        FileLogger.d(TAG, "refresh: 使用传入的天气和城市数据直接更新 Widget")
                        WeatherWidgetUpdater.updateAll(context, weather, cityName)
                    } else {
                        FileLogger.d(TAG, "refresh: 启动一次性 Worker 来异步读取并渲染 Widget")
                        enqueueOneTimeWorker(context, trigger = "refresh")
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
