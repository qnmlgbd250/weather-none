package com.skypulse.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.util.FileLogger
import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try { enqueueWorker(context) } catch (_: Exception) {}
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        FileLogger.i("WidgetRefresh", "【系统兜底刷新】onUpdate 触发, appWidgetIds=${appWidgetIds.toList()}")
        // 立即用缓存数据刷新 UI
        try {
            val moshi = Moshi.Builder().build()
            val cities = CityManager(context, moshi).getCities()
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
            val weather = city?.let { WeatherCache(context).load(it.id) }
            WeatherWidgetUpdater.updateAll(context, weather, city?.name)
        } catch (_: Exception) {
            WeatherWidgetUpdater.updateAll(context, null, null)
        }
        // 启动后台 Worker 独立拉取最新数据
        enqueueWorker(context)
        // 节流：10 分钟内不重复触发 onetime worker
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("widget_throttle", Context.MODE_PRIVATE)
        val lastTrigger = prefs.getLong("last_onetime_trigger", 0L)
        if (now - lastTrigger >= ONETIME_THROTTLE_MS) {
            prefs.edit().putLong("last_onetime_trigger", now).apply()
            FileLogger.i("WidgetRefresh", "【节流通过】触发 onetime worker，距上次 ${(now - lastTrigger) / 1000}秒")
            enqueueOneTimeWorker(context)
        } else {
            FileLogger.i("WidgetRefresh", "【节流拦截】跳过 onetime worker，距上次 ${(now - lastTrigger) / 1000}秒")
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

        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    val moshi = Moshi.Builder().build()
                    val cities = CityManager(context, moshi).getCities()
                    val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
                    val weather = city?.let { WeatherCache(context).load(it.id) }
                    WeatherWidgetUpdater.updateAll(context, weather, city?.name)
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
        private fun enqueueOneTimeWorker(context: Context) {
            val inputData = Data.Builder()
                .putString("trigger", "onetime")
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
