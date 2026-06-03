package com.skypulse.weather.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
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

    companion object {
        private const val TAG = "NotificationWorker"
        const val CHANNEL_ID = "weather_alerts"
        const val WORK_NAME = "weather_notification_periodic"
    }

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

            val skycon = realtime?.skycon ?: "UNKNOWN"
            val temp = realtime?.temperature?.toInt() ?: 0
            val humidity = realtime?.humidity?.let { (it * 100).toInt() } ?: 0
            val windSpeed = realtime?.wind?.speed ?: 0.0
            val weatherDesc = getWeatherDesc(skycon)
            
            val minTemp = daily?.temperature?.firstOrNull()?.min?.toInt() ?: 0
            val maxTemp = daily?.temperature?.firstOrNull()?.max?.toInt() ?: 0
            
            // Rain alert
            if (prefs.getBoolean("rain_alert", true)) {
                if (skycon.contains("RAIN") || skycon.contains("STORM")) {
                    val body = "${city.name} $weatherDesc ${temp}\u00b0C | ${minTemp}\u00b0/${maxTemp}\u00b0 | \u6e7f\u5ea6${humidity}% | \u98ce\u901f${windSpeed}m/s"
                    sendNotification(nm, 1, "\u77ed\u4e34\u96e8\u6c34\u63d0\u9192", body)
                }
            }

            // Weather warning alert - skip blue level
            if (prefs.getBoolean("warning_alert", true)) {
                alerts?.forEach { alert ->
                    val level = alert.level ?: ""
                    val title = alert.title ?: ""
                    
                    Log.d(TAG, "Alert: title=$title, level=$level")
                    
                    // Skip blue level alerts - check both level field and title
                    val isBlueLevel = level.contains("\u84dd") || 
                                     level.contains("\u84dd\u8272") ||
                                     title.contains("\u84dd\u8272") ||
                                     title.contains("\u84dd\u7ea7")
                    
                    if (isBlueLevel) {
                        Log.d(TAG, "Skipping blue alert: $title")
                        return@forEach
                    }
                    
                    val cleanTitle = title
                        ?.replace(Regex("\\[.*?\\]"), "")
                        ?.replace(Regex("^.*\u53d1\u5e03"), "")
                        ?.trim()
                    if (!cleanTitle.isNullOrBlank()) {
                        val body = "${city.name} $weatherDesc ${temp}\u00b0C | ${minTemp}\u00b0/${maxTemp}\u00b0"
                        sendNotification(nm, 2, cleanTitle, body)
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
                            val body = "${city.name} $weatherDesc ${temp}\u00b0C | \u4eca\u65e5\u6700\u9ad8\u6e29: ${today}\u00b0C"
                            sendNotification(nm, 3, "\u53d8\u6e29\u63d0\u9192", body)
                        }
                    }
                }
            }

            // Wind alert
            if (prefs.getBoolean("wind_alert", false)) {
                if (windSpeed >= 10.8) {
                    val body = "${city.name} $weatherDesc ${temp}\u00b0C | \u5f53\u524d\u98ce\u901f: ${windSpeed}m/s"
                    sendNotification(nm, 4, "\u5927\u98ce\u63d0\u9192", body)
                }
            }

            // Extreme weather alert
            if (prefs.getBoolean("typhoon_alert", true)) {
                if (skycon == "STORM_RAIN") {
                    val body = "${city.name} \u66b4\u96e8 ${temp}\u00b0C | ${minTemp}\u00b0/${maxTemp}\u00b0 | \u8bf7\u5c3d\u91cf\u907f\u514d\u5916\u51fa"
                    sendNotification(nm, 5, "\u6781\u7aef\u5929\u6c14\u63d0\u9192", body)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in doWork", e)
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
}