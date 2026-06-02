package com.skypulse.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.*
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        try {
            val moshi = Moshi.Builder().build()
            val cities = CityManager(context, moshi).getCities()
            Log.d("Widget", "cities count: ${cities.size}")
            cities.forEach { Log.d("Widget", "city: id=${it.id} name=${it.name} isCurrent=${it.isCurrentLocation}") }

            val city = cities.firstOrNull { it.isCurrentLocation }
            Log.d("Widget", "selected city: ${city?.name}")

            val weather = city?.let {
                val w = WeatherCache(context).load(it.id)
                Log.d("Widget", "weather cache: ${w != null}")
                w
            }
            Log.d("Widget", "updating widget with weather=${weather != null}, city=${city?.name}")
            WeatherWidgetUpdater.updateAll(context, weather, city?.name)
        } catch (e: Exception) {
            Log.e("Widget", "onUpdate error", e)
            WeatherWidgetUpdater.updateAll(context, null, null)
        }
        try { enqueueWorker(context) } catch (_: Exception) {}
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        try { enqueueWorker(context) } catch (_: Exception) {}
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        try { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) } catch (_: Exception) {}
    }

    companion object {

        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    val moshi = com.squareup.moshi.Moshi.Builder().build()
                    val cities = com.skypulse.weather.data.CityManager(context, moshi).getCities()
                    val city = cities.firstOrNull { it.isCurrentLocation }
                    val weather = city?.let { com.skypulse.weather.data.WeatherCache(context).load(it.id) }
                    WeatherWidgetUpdater.updateAll(context, weather, city?.name)
                }
            } catch (_: Exception) {}
        }
        private const val WORK_NAME = "weather_widget_periodic"

        fun enqueueWorker(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWidgetWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}