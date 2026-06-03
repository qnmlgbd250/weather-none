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

            // Build weather summary
            val skycon = realtime?.skycon ?: "UNKNOWN"
            val temp = realtime?.temperature?.toInt() ?: 0
            val humidity = realtime?.humidity?.let { (it * 100).toInt() } ?: 0
            val windSpeed = realtime?.wind?.speed ?: 0.0
            val weatherDesc = getWeatherDesc(skycon)
            
            val minTemp = daily?.temperature?.firstOrNull()?.min?.toInt() ?: 0
            val maxTemp = daily?.temperature?.firstOrNull()?.max?.toInt() ?: 0
            
            val summary = "${city.name} $weatherDesc ${temp}°C | ${minTemp}°/${maxTemp}° | 湿度${humidity}% | 风速${windSpeed}m/s"

            // Always send test notification
            sendNotification(nm, 99, "SkyPulse 天气提醒测试", summary)

            // Rain alert
            if (prefs.getBoolean("rain_alert", true)) {
                if (skycon.contains("RAIN") || skycon.contains("STORM")) {
                    sendNotification(nm, 1, "\u77ed\u4e34\u96e8\u6c34\u63d0\u9192", "\u5f53\u524d\u5929\u6c14: " + weatherDesc)
                }
            }

            // Weather warning alert
            if (prefs.getBoolean("warning_alert", true)) {
                alerts?.forEach { alert ->
                    val title = alert.title?.replace(Regex("^.*\u53d1\u5e03"), "")?.trim()
                    if (!title.isNullOrBlank()) {
                        sendNotification(nm, 2, "\u6c14\u8c61\u9884\u8b66", title)
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
                            sendNotification(nm, 3, "\u53d8\u6e29\u63d0\u9192", "\u4eca\u65e5\u6e29\u5dee\u8f83\u5927, \u6700\u9ad8\u6e29: " + today + "°C")
                        }
                    }
                }
            }

            // Wind alert
            if (prefs.getBoolean("wind_alert", false)) {
                if (windSpeed >= 10.8) {
                    sendNotification(nm, 4, "\u5927\u98ce\u63d0\u9192", "\u5f53\u524d\u98ce\u901f: " + windSpeed + "m/s")
                }
            }

            // Extreme weather alert
            if (prefs.getBoolean("typhoon_alert", true)) {
                if (skycon == "STORM_RAIN") {
                    sendNotification(nm, 5, "\u6781\u7aef\u5929\u6c14", "\u5f53\u524d\u4e3a\u66b4\u96e8\u5929\u6c14, \u8bf7\u5c3d\u91cf\u907f\u514d\u5916\u51fa")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "\u5929\u6c14\u63d0\u9192", NotificationManager.IMPORTANCE_DEFAULT)
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
            "CLEAR_DAY" -> "\u6674"
            "CLEAR_NIGHT" -> "\u6674"
            "PARTLY_CLOUDY_DAY" -> "\u591a\u4e91"
            "PARTLY_CLOUDY_NIGHT" -> "\u591a\u4e91"
            "CLOUDY" -> "\u9634"
            "LIGHT_RAIN" -> "\u5c0f\u96e8"
            "MODERATE_RAIN" -> "\u4e2d\u96e8"
            "HEAVY_RAIN" -> "\u5927\u96e8"
            "STORM_RAIN" -> "\u66b4\u96e8"
            "FOG" -> "\u96fe"
            "LIGHT_SNOW" -> "\u5c0f\u96ea"
            "MODERATE_SNOW" -> "\u4e2d\u96ea"
            "HEAVY_SNOW" -> "\u5927\u96ea"
            "STORM_SNOW" -> "\u66b4\u96ea"
            "WIND" -> "\u5927\u98ce"
            else -> "\u672a\u77e5"
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