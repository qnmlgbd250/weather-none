package com.skypulse.weather.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.R
import com.skypulse.weather.data.MembershipRepository
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import com.skypulse.weather.sync.RefreshPolicy
import com.skypulse.weather.sync.RefreshManager
import com.skypulse.weather.sync.SyncReason
import com.skypulse.weather.util.WeatherUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 定期天气通知 Worker（30分钟周期）。
 *
 * Phase 3 架构：从 Room 读取天气数据，不直接请求 API。
 * 如果数据过期，委托 SyncManager 刷新。
 */
@HiltWorker
class WeatherNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeatherRepository,
    private val cityRepository: CityRepository,
    private val refreshManager: RefreshManager,
    private val membershipRepository: MembershipRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "weather_alerts"
        const val WORK_NAME = "weather_notification_periodic"
        private const val TAG = "WeatherNotifWorker"
        private const val KEY_TEMP_BASELINE_DATE = "temp_baseline_date"
        private const val KEY_TEMP_BASELINE_MAX = "temp_baseline_max"
    }

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            if (!WeatherNotificationScheduler.hasAnyAlertEnabled(context)) {
                WeatherNotificationScheduler.cancel(context)
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
            val isPremium = membershipRepository.isPremium.value
            val cities = cityRepository.getCities()
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
                ?: return Result.success()

            // 检查数据是否过期（Repository 决定缓存策略）
            val isCacheStale = repository.isCacheStale(city.id, RefreshPolicy.NOTIFICATION_CACHE_TTL_MS)

            if (isCacheStale) {
                // 数据过期，通过 RefreshManager 请求同步
                try {
                    refreshManager.requestSync(SyncReason.PERIODIC)
                } catch (e: Exception) {
                    Log.w(TAG, "Sync failed", e)
                }
            }

            // 从 Room 读取天气数据
            val weather = repository.getWeatherFromCache(city.id) ?: return Result.success()

            // Initialize deduplicator and clean up expired records
            val dedup = NotificationDeduplicator(context)
            dedup.cleanup()

            val realtime = weather.result?.realtime
            val daily = weather.result?.daily

            val skycon = realtime?.skycon ?: "UNKNOWN"
            val windSpeed = realtime?.wind?.speed ?: 0.0
            val gustSpeed = weather.result?.hourly?.gust?.firstOrNull { it.value != null }?.value
            val weatherDesc = getWeatherDesc(skycon)

            val maxTemp = WeatherUtils.todayTemperature(daily)?.max?.toInt() ?: 0

            val minutely = weather.result?.minutely
            val minutelyDesc = minutely?.description ?: ""
            val precip2h = minutely?.precipitation_2h
            val minutelyOk = minutely?.status == "ok"
            val hasMinutelyRain = minutelyOk && !precip2h.isNullOrEmpty() && run {
                var maxConsecutive = 0
                var current = 0
                for (v in precip2h) {
                    if (v >= 0.01) { current++; if (current > maxConsecutive) maxConsecutive = current }
                    else { current = 0 }
                }
                maxConsecutive >= 3
            }
            val maxMinutelyPrecip = precip2h?.maxOrNull() ?: 0.0
            val realtimePrecip = realtime?.precipitation?.local?.intensity ?: 0.0
            val effectivePrecip = maxOf(maxMinutelyPrecip, realtimePrecip)
            val precipIntensityDesc = when {
                effectivePrecip >= 0.15 -> "强雨"
                effectivePrecip >= 0.08 -> "中雨"
                effectivePrecip >= 0.03 -> "小雨"
                effectivePrecip > 0 -> "毛毛雨"
                else -> weatherDesc
            }
            if (isPremium && prefs.getBoolean("rain_alert", true)) {
                if (hasMinutelyRain) {
                    if (dedup.shouldNotifyRain()) {
                        val title = buildNotificationTitle("短临降水提醒", precipIntensityDesc)
                        val body = if (minutelyDesc.isNotBlank()) {
                            minutelyDesc
                        } else {
                            "当前降水强度: $precipIntensityDesc，请注意出行带伞"
                        }
                        sendNotification(nm, 1, title, body)
                    }
                }
            }

            if (prefs.getBoolean("temp_change_alert", false)) {
                val todayTemp = WeatherUtils.todayTemperature(daily)
                maybeNotifyTemperatureChange(
                    prefs = prefs,
                    dedup = dedup,
                    nm = nm,
                    todayMax = todayTemp?.max,
                    todayDate = todayTemp?.date
                )
            }
            if (prefs.getBoolean("wind_alert", false)) {
                val windAlert = WindAlertPolicy.evaluate(windSpeed, gustSpeed)
                if (windAlert != null) {
                    if (dedup.shouldNotifyWind()) {
                        val detail = if (windAlert.isGust) {
                            "${windAlert.level}阵风"
                        } else {
                            "${windAlert.level}大风"
                        }
                        val title = buildNotificationTitle("大风提醒", detail)
                        val body = if (windAlert.isGust) {
                            "当前阵风 ${windAlert.speed}km/h，请注意防风，避免高空作业"
                        } else {
                            "当前风速 ${windAlert.speed}km/h，请注意防风，避免高空作业"
                        }
                        sendNotification(nm, 4, title, body)
                    }
                }
            }
            if (isPremium && prefs.getBoolean("typhoon_alert", true)) {
                if (skycon == "STORM_RAIN") {
                    if (dedup.shouldNotifyExtreme()) {
                        val title = buildNotificationTitle("极端天气提醒", "暴雨")
                        val body = "当前已出现暴雨天气，最高温 ${maxTemp}°C，请尽量避免外出，注意安全"
                        sendNotification(nm, 5, title, body)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun maybeNotifyTemperatureChange(
        prefs: SharedPreferences,
        dedup: NotificationDeduplicator,
        nm: NotificationManager,
        todayMax: Double?,
        todayDate: String?
    ) {
        if (todayMax == null) return

        val date = normalizeDate(todayDate)
        val baselineDate = prefs.getString(KEY_TEMP_BASELINE_DATE, null)
        val baselineMax = prefs.getString(KEY_TEMP_BASELINE_MAX, null)?.toDoubleOrNull()

        if (baselineDate != null && baselineMax != null && isPreviousDay(baselineDate, date)) {
            val diff = todayMax - baselineMax
            val absDiff = kotlin.math.abs(diff)
            if (absDiff >= 8 && dedup.shouldNotifyTempChange()) {
                val direction = if (diff > 0) "升温" else "降温"
                val title = buildNotificationTitle("变温提醒", "剧烈$direction")
                val body = "今日最高温 ${kotlin.math.round(todayMax).toInt()}°C，比昨日${direction}${kotlin.math.round(absDiff).toInt()}°C，请注意增减衣物"
                sendNotification(nm, 3, title, body)
            }
        }

        prefs.edit()
            .putString(KEY_TEMP_BASELINE_DATE, date)
            .putString(KEY_TEMP_BASELINE_MAX, todayMax.toString())
            .apply()
    }

    private fun normalizeDate(date: String?): String {
        return date?.take(10)?.takeIf { it.length == 10 }
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    }

    private fun isPreviousDay(previous: String, current: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            val currentDate = format.parse(current) ?: return false
            val calendar = Calendar.getInstance(Locale.CHINA)
            calendar.time = currentDate
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            format.format(calendar.time) == previous
        } catch (_: Exception) {
            false
        }
    }

    private fun createChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "天气提醒", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
    }

    private fun isAlertChannelEnabled(nm: NotificationManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            nm.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun buildNotificationTitle(prefix: String, detail: String): String {
        val cleanDetail = detail.trim()
        return if (cleanDetail.isBlank()) prefix else "$prefix · $cleanDetail"
    }

    @Suppress("MissingPermission")
    private fun sendNotification(nm: NotificationManager, id: Int, title: String, body: String) {
        if (!WeatherNotificationScheduler.canPostNotifications(applicationContext)) return

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

    private fun getWeatherDesc(skycon: String?): String {
        return when (skycon) {
            "CLEAR_DAY" -> "晴"
            "CLEAR_NIGHT" -> "晴"
            "PARTLY_CLOUDY_DAY" -> "多云"
            "PARTLY_CLOUDY_NIGHT" -> "多云"
            "CLOUDY" -> "阴"
            "LIGHT_RAIN" -> "小雨"
            "MODERATE_RAIN" -> "中雨"
            "HEAVY_RAIN" -> "大雨"
            "STORM_RAIN" -> "暴雨"
            "FOG" -> "雾"
            "LIGHT_SNOW" -> "小雪"
            "MODERATE_SNOW" -> "中雪"
            "HEAVY_SNOW" -> "大雪"
            "STORM_SNOW" -> "暴雪"
            "WIND" -> "大风"
            else -> "未知"
        }
    }
}
