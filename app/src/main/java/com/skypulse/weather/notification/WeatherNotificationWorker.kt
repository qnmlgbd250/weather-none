package com.skypulse.weather.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.R
import com.skypulse.weather.api.CaiyunApi
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.repository.WeatherRepository
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class WeatherNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
            val moshi = Moshi.Builder().build()
            val cities = CityManager(context, moshi).getCities()
            val city = cities.firstOrNull { it.isCurrentLocation } ?: return Result.success()

            val api = createCaiyunApi(moshi)
            val repo = WeatherRepository(api)
            val weather = repo.getWeather(city.longitude, city.latitude).getOrNull() ?: return Result.success()
            WeatherCache(context).save(city.id, weather)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createChannel(nm)

            val realtime = weather.result?.realtime
            val daily = weather.result?.daily
            val alerts = weather.result?.alert?.content

            // Rain alert
            if (prefs.getBoolean("rain_alert", true)) {
                val skycon = realtime?.skycon
                if (skycon?.contains("RAIN") == true || skycon?.contains("STORM") == true) {
                    sendNotification(nm, 1, "雨水提醒", "当前天气: " + getWeatherDesc(skycon))
                }
            }

            // Weather warning alert
            if (prefs.getBoolean("warning_alert", true)) {
                alerts?.forEach { alert ->
                    val title = alert.title?.replace(Regex("^.*发布"), "")?.trim()
                    if (!title.isNullOrBlank()) {
                        sendNotification(nm, 2, "气象预警", title)
                    }
                }
            }

            // Temperature change alert
            if (prefs.getBoolean("temp_change_alert", false)) {
                val temps = daily?.temperature
                if (temps != null && temps.size >= 2) {
                    val today = temps[0].max
                    val yesterday = temps[1].max
                    if (today != null && yesterday != null) {
                        val diff = kotlin.math.abs(today - yesterday)
                        if (diff >= 8) {
                            sendNotification(nm, 3, "变温提醒", "今日温差较大, 最高温: " + today + "°C")
                        }
                    }
                }
            }

            // Wind alert
            if (prefs.getBoolean("wind_alert", false)) {
                val windSpeed = realtime?.wind?.speed ?: 0.0
                if (windSpeed >= 10.8) {
                    sendNotification(nm, 4, "大风提醒", "当前风速: " + windSpeed + "m/s")
                }
            }

            // Extreme weather alert
            if (prefs.getBoolean("typhoon_alert", true)) {
                if (realtime?.skycon == "STORM_RAIN") {
                    sendNotification(nm, 5, "极端天气", "当前为暴雨天气, 请尽量避免外出")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "天气提醒", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(nm: NotificationManager, id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }

    private fun getWeatherDesc(skycon: String?): String {
        return when (skycon) {
            "LIGHT_RAIN" -> "小雨"
            "MODERATE_RAIN" -> "中雨"
            "HEAVY_RAIN" -> "大雨"
            "STORM_RAIN" -> "暴雨"
            else -> "雨"
        }
    }

    private fun createCaiyunApi(moshi: Moshi): CaiyunApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://wrapper.cyapi.cn/")
            .client(client)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(CaiyunApi::class.java)
    }

    companion object {
        const val CHANNEL_ID = "weather_alerts"
        const val WORK_NAME = "weather_notification_periodic"
    }
}
