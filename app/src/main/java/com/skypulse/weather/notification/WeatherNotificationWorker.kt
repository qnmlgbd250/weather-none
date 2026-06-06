package com.skypulse.weather.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    companion object {
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

            // Initialize deduplicator and clean up expired records
            val dedup = NotificationDeduplicator(context)
            dedup.cleanup()

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

            val minutely = weather.result?.minutely
            val minutelyDesc = minutely?.description ?: ""
            val precipIntensity = realtime?.precipitation?.local?.intensity ?: 0.0
            val precipIntensityDesc = when {
                precipIntensity >= 0.15 -> "\u5f3a\u96e8"
                precipIntensity >= 0.08 -> "\u4e2d\u96e8"
                precipIntensity >= 0.03 -> "\u5c0f\u96e8"
                precipIntensity > 0 -> "\u6bdb\u6bdb\u96e8"
                else -> weatherDesc
            }
            // Rain alert — precipitation focused
            if (prefs.getBoolean("rain_alert", true)) {
                if (skycon.contains("RAIN") || skycon.contains("STORM")) {
                    if (dedup.shouldNotifyRain()) {
                        val title = "\u77ed\u4e34\u964d\u6c34\u63d0\u9192\u2014\u2014$precipIntensityDesc"
                        val body = if (minutelyDesc.isNotBlank()) {
                            minutelyDesc
                        } else {
                            "\u5f53\u524d\u964d\u6c34\u5f3a\u5ea6: $precipIntensityDesc\uff0c\u8bf7\u6ce8\u610f\u51fa\u884c\u5e26\u4f1e"
                        }
                        sendNotification(nm, 1, title, body)
                    }
                }
            }

            // Weather warning alert — skip blue level, dedup by title
            if (prefs.getBoolean("warning_alert", true)) {
                alerts?.forEach { alert ->
                    val level = alert.level ?: ""
                    val title = alert.title ?: ""
                    
                    // Skip blue level alerts - check both level field and title
                    val isBlueLevel = level.contains("\u84dd") || 
                                     level.contains("\u84dd\u8272") ||
                                     title.contains("\u84dd\u8272") ||
                                     title.contains("\u84dd\u7ea7")
                    
                    if (isBlueLevel) {
                        return@forEach
                    }
                    
                    val cleanTitle = title
                        ?.replace(Regex("\\[.*?\\]"), "")
                        ?.replace(Regex("^.*\u53d1\u5e03"), "")
                        ?.trim()
                    if (!cleanTitle.isNullOrBlank()) {
                        if (dedup.shouldNotifyWarning(cleanTitle)) {
                            // Use alert description as body, truncate to 2 lines
                            val description = alert.description ?: ""
                            val body = if (!description.isNullOrBlank()) {
                                truncateToTwoLines(description)
                            } else {
                                cleanTitle
                            }
                            sendNotification(nm, 2, cleanTitle, body)
                        }
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
                        val diff = today - yesterday
                        val absDiff = kotlin.math.abs(diff)
                        if (absDiff >= 8) {
                            if (dedup.shouldNotifyTempChange()) {
                                val direction = if (diff > 0) "\u5347\u6e29" else "\u964d\u6e29"
                                val title = "\u53d8\u6e29\u63d0\u9192\u2014\u2014\u5267\u70c8$direction"
                                val body = "\u4eca\u65e5\u6700\u9ad8\u6e29 ${today}\u00b0C\uff0c\u6bd4\u6628\u65e5${direction}${absDiff}\u00b0C\uff0c\u8bf7\u6ce8\u610f\u589e\u51cf\u8863\u7269"
                                sendNotification(nm, 3, title, body)
                            }
                        }
                    }
                }
            }
            // Wind alert
            if (prefs.getBoolean("wind_alert", false)) {
                if (windSpeed >= 10.8) {
                    if (dedup.shouldNotifyWind()) {
                        val windLevel = when {
                            windSpeed >= 24.5 -> "9\u7ea7"
                            windSpeed >= 20.8 -> "8\u7ea7"
                            windSpeed >= 17.2 -> "7\u7ea7"
                            windSpeed >= 13.9 -> "6\u7ea7"
                            windSpeed >= 10.8 -> "5\u7ea7"
                            else -> ""
                        }
                        val title = "\u5927\u98ce\u63d0\u9192\u2014\u2014$windLevel\u5927\u98ce"
                        val body = "\u5f53\u524d\u98ce\u901f ${windSpeed}m/s\uff0c\u8bf7\u6ce8\u610f\u9632\u98ce\uff0c\u907f\u514d\u9ad8\u7a7a\u4f5c\u4e1a"
                        sendNotification(nm, 4, title, body)
                    }
                }
            }
            // Extreme weather alert
            if (prefs.getBoolean("typhoon_alert", true)) {
                if (skycon == "STORM_RAIN") {
                    if (dedup.shouldNotifyExtreme()) {
                        val title = "\u6781\u7aef\u5929\u6c14\u63d0\u9192\u2014\u2014\u66b4\u96e8"
                        val body = "\u5f53\u524d\u5df2\u51fa\u73b0\u66b4\u96e8\u5929\u6c14\uff0c\u6700\u9ad8\u6e29 ${maxTemp}\u00b0C\uff0c\u8bf7\u5c3d\u91cf\u907f\u514d\u5916\u51fa\uff0c\u6ce8\u610f\u5b89\u5168"
                        sendNotification(nm, 5, title, body)
                    }
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
            .setContentIntent(createMainActivityIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }

    private fun createMainActivityIntent(): PendingIntent {
        val intent = Intent(applicationContext, com.skypulse.weather.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Truncate text to at most 2 lines, adding ellipsis if truncated.
     * Approximates ~50 chars per line for Chinese text.
     */
    private fun truncateToTwoLines(text: String, maxCharsPerLine: Int = 50): String {
        val maxTotal = maxCharsPerLine * 2
        val cleanText = text.replace("\r\n", "\n").replace("\r", "\n").trim()
        
        // Split by existing newlines
        val lines = cleanText.split("\n")
        
        val result = StringBuilder()
        var lineCount = 0
        var charCount = 0
        
        for (line in lines) {
            if (lineCount >= 2) break
            
            if (line.isEmpty()) {
                if (lineCount < 2) {
                    result.append("\n")
                    lineCount++
                }
                continue
            }
            
            // If this single line exceeds max chars per line, need to wrap
            if (line.length > maxCharsPerLine) {
                val firstPart = line.take(maxCharsPerLine)
                val secondPart = line.drop(maxCharsPerLine)
                
                if (lineCount == 0) {
                    result.append(firstPart)
                    result.append("\n")
                    lineCount++
                    
                    if (secondPart.length > maxCharsPerLine) {
                        // Need ellipsis on second line
                        result.append(secondPart.take(maxCharsPerLine - 1)).append("\u2026")
                        lineCount++
                        break
                    } else {
                        result.append(secondPart)
                        lineCount++
                    }
                } else if (lineCount == 1) {
                    result.append(firstPart.take(maxCharsPerLine - 1)).append("\u2026")
                    lineCount++
                    break
                }
            } else {
                if (lineCount > 0) {
                    result.append("\n")
                }
                result.append(line)
                lineCount++
            }
            
            charCount += line.length
            if (charCount >= maxTotal) break
        }
        
        // If we exceeded the limit, add ellipsis
        val resultText = result.toString().trimEnd()
        return if (resultText.length > maxTotal) {
            resultText.take(maxTotal - 1) + "\u2026"
        } else {
            resultText
        }
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