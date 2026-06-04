package com.skypulse.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import androidx.work.*
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 立即用缓存数据刷新 UI
        try {
            val moshi = Moshi.Builder().build()
            val cities = CityManager(context, moshi).getCities()
            val city = cities.firstOrNull { it.isCurrentLocation }
            val weather = city?.let { WeatherCache(context).load(it.id) }
            WeatherWidgetUpdater.updateAll(context, weather, city?.name)
        } catch (_: Exception) {
            WeatherWidgetUpdater.updateAll(context, null, null)
        }
        // 启动后台 Worker 独立拉取最新数据
        enqueueWorker(context)
        // 立即触发一次 Worker 执行，不等待 30 分钟周期
        enqueueOneTimeWorker(context)
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
                    val city = cities.firstOrNull { it.isCurrentLocation }
                    val weather = city?.let { WeatherCache(context).load(it.id) }
                    WeatherWidgetUpdater.updateAll(context, weather, city?.name)
                }
            } catch (_: Exception) {}
        }

        private const val WORK_NAME = "weather_widget_periodic"
        private const val WORK_NAME_ONETIME = "weather_widget_onetime"

        /** 每 30 分钟定时刷新 */
        fun enqueueWorker(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** 立即触发一次独立刷新 */
        private fun enqueueOneTimeWorker(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
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