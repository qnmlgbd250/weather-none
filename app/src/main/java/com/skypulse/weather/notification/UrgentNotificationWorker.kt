package com.skypulse.weather.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.R
import com.skypulse.weather.api.CaiyunApi
import com.skypulse.weather.data.CityDataStore
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.LocationManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.model.City
import com.skypulse.weather.repository.WeatherRepository
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class UrgentNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "weather_urgent_periodic"
        private const val TAG = "UrgentNotifWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            if (!WeatherNotificationScheduler.hasAnyAlertEnabled(context)) {
                return Result.success()
            }
            if (!WeatherNotificationScheduler.canPostNotifications(context)) {
                Log.w(TAG, "Notification permission disabled, skipping weather alerts")
                return Result.success()
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createChannel(nm)
            if (!isAlertChannelEnabled(nm)) {
                Log.w(TAG, "Weather alert channel disabled, skipping weather alerts")
                return Result.success()
            }

            val prefs = context.getSharedPreferences(WeatherNotificationScheduler.PREFS_NAME, Context.MODE_PRIVATE)
            val moshi = Moshi.Builder().build()
            val cityManager = CityManager(context, moshi)
            val cityDataStore = CityDataStore(context, moshi)
            val cities = cityDataStore.getCities().ifEmpty { cityManager.getCities() }
            val city = resolveNotificationCity(context, cities, cityDataStore, cityManager)
                ?: return Result.success()

            val api = createCaiyunApi(moshi)
            val repo = WeatherRepository(api)
            val weather = repo.getWeather(city.longitude, city.latitude).getOrNull() ?: return Result.success()
            WeatherCache(context).save(city.id, weather)

            // Initialize deduplicator and clean up expired records
            val dedup = NotificationDeduplicator(context)
            dedup.cleanup()

            val realtime = weather.result?.realtime
            val alerts = weather.result?.alert?.content

            val minutely = weather.result?.minutely
            val minutelyDesc = minutely?.description ?: ""
            val precip2h = minutely?.precipitation_2h
            val minutelyOk = minutely?.status == "ok"
            // Filter radar noise: require >= 0.01 intensity AND at least 3 consecutive minutes
            val hasMinutelyRain = minutelyOk && !precip2h.isNullOrEmpty() && run {
                var maxConsecutive = 0
                var current = 0
                for (v in precip2h) {
                    if (v >= 0.01) { current++; if (current > maxConsecutive) maxConsecutive = current }
                    else { current = 0 }
                }
                maxConsecutive >= 3
            }
            // Use minutely max intensity for display, fallback to realtime
            val maxMinutelyPrecip = precip2h?.maxOrNull() ?: 0.0
            val realtimePrecip = realtime?.precipitation?.local?.intensity ?: 0.0
            val effectivePrecip = maxOf(maxMinutelyPrecip, realtimePrecip)
            val precipIntensityDesc = when {
                effectivePrecip >= 0.15 -> "\u5f3a\u96e8"
                effectivePrecip >= 0.08 -> "\u4e2d\u96e8"
                effectivePrecip >= 0.03 -> "\u5c0f\u96e8"
                effectivePrecip > 0 -> "\u6bdb\u6bdb\u96e8"
                else -> "降水"
            }
            // Rain alert \u2014 based on minutely precipitation data, not skycon
            if (prefs.getBoolean("rain_alert", true)) {
                if (hasMinutelyRain) {
                    if (dedup.shouldNotifyRain()) {
                        val title = buildNotificationTitle("\u77ed\u4e34\u964d\u6c34\u63d0\u9192", precipIntensityDesc)
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
                        .replace(Regex("\\[.*?\\]"), "")
                        .replace(Regex("^.*(?:\u53d1\u5e03|\u53d8\u66f4|\u89e3\u9664|\u7ee7\u7eed|\u66f4\u65b0)"), "")
                        .replace(Regex("\u9884\u8b66.*$"), "\u9884\u8b66")
                        .trim()
                    if (cleanTitle.isNotBlank()) {
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

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Urgent notification check failed", e)
            Result.retry()
        }
    }

    private suspend fun resolveNotificationCity(
        context: Context,
        cities: List<City>,
        cityDataStore: CityDataStore,
        cityManager: CityManager
    ): City? {
        val currentCity = cities.firstOrNull { it.isCurrentLocation }
        val locationManager = LocationManager(context)
        val amapLocation = if (locationManager.hasBackgroundLocationPermission()) {
            try {
                locationManager.requestAmapLocation()
            } catch (e: Exception) {
                Log.w(TAG, "Location fetch failed, using saved city", e)
                null
            }
        } else {
            Log.w(TAG, "Background location permission not granted, using cache")
            null
        }

        if (amapLocation != null) {
            val lon = amapLocation.longitude
            val lat = amapLocation.latitude
            val locationName = locationManager.resolveLocationName(amapLocation)
            locationManager.saveCachedLocation(locationName, lon, lat)
            return saveCurrentLocationCity(
                cities = cities,
                currentCity = currentCity,
                cityDataStore = cityDataStore,
                cityManager = cityManager,
                name = locationName,
                longitude = lon,
                latitude = lat
            )
        }

        locationManager.getCachedLocation()?.let { cached ->
            return saveCurrentLocationCity(
                cities = cities,
                currentCity = currentCity,
                cityDataStore = cityDataStore,
                cityManager = cityManager,
                name = cached.name,
                longitude = cached.longitude,
                latitude = cached.latitude
            )
        }

        if (currentCity != null) {
            if (currentCity.longitude == LocationManager.DEFAULT_LONGITUDE &&
                currentCity.latitude == LocationManager.DEFAULT_LATITUDE) {
                Log.w(TAG, "No trusted location and city is default Beijing, skipping notifications")
                return null
            }
            return currentCity
        }

        return cities.firstOrNull()
    }

    private suspend fun saveCurrentLocationCity(
        cities: List<City>,
        currentCity: City?,
        cityDataStore: CityDataStore,
        cityManager: CityManager,
        name: String,
        longitude: Double,
        latitude: Double
    ): City {
        val updatedCity = (currentCity ?: City(
            id = "current_location",
            name = name,
            longitude = longitude,
            latitude = latitude,
            isCurrentLocation = true
        )).copy(
            name = name,
            longitude = longitude,
            latitude = latitude,
            isCurrentLocation = true
        )
        val updatedCities = if (currentCity != null) {
            cities.map { if (it.isCurrentLocation) updatedCity else it }
        } else {
            listOf(updatedCity) + cities
        }
        cityDataStore.saveCities(updatedCities)
        cityManager.saveCities(updatedCities)
        return updatedCity
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }


    private fun createChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(WeatherNotificationWorker.CHANNEL_ID, "\u5929\u6c14\u63d0\u9192", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
    }

    private fun isAlertChannelEnabled(nm: NotificationManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            nm.getNotificationChannel(WeatherNotificationWorker.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun buildNotificationTitle(prefix: String, detail: String): String {
        val cleanDetail = detail.trim()
        return if (cleanDetail.isBlank()) prefix else "$prefix · $cleanDetail"
    }

    @Suppress("MissingPermission")
    private fun sendNotification(nm: NotificationManager, id: Int, title: String, body: String) {
        if (!WeatherNotificationScheduler.canPostNotifications(applicationContext)) return

        val notification = NotificationCompat.Builder(applicationContext, WeatherNotificationWorker.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(createMainActivityIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
