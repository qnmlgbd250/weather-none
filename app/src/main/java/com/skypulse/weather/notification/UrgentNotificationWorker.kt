package com.skypulse.weather.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.skypulse.weather.util.FileLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 高频紧急通知 Worker（10分钟周期）。
 *
 * Phase 3 架构：从 Room 读取天气和预警数据，不直接请求 API。
 * 如果数据过期，委托 SyncManager 刷新。
 */
@HiltWorker
class UrgentNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeatherRepository,
    private val cityRepository: CityRepository,
    private val refreshManager: RefreshManager,
    private val membershipRepository: MembershipRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "weather_urgent_periodic"
        private const val TAG = "UrgentNotifWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            FileLogger.i(TAG, "doWork: 开始执行")
            if (!WeatherNotificationScheduler.hasAnyAlertEnabled(context)) {
                FileLogger.i(TAG, "doWork: 所有通知已关闭，跳过")
                return Result.success()
            }
            if (!WeatherNotificationScheduler.canPostNotifications(context)) {
                FileLogger.w(TAG, "doWork: 通知权限未授予，跳过")
                return Result.success()
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createChannel(nm)
            if (!isAlertChannelEnabled(nm)) {
                FileLogger.w(TAG, "doWork: 通知渠道已禁用，跳过")
                return Result.success()
            }

            val prefs = context.getSharedPreferences(WeatherNotificationScheduler.PREFS_NAME, Context.MODE_PRIVATE)
            val isPremium = membershipRepository.isPremium.value
            val cities = cityRepository.getCities()
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
                ?: return Result.success()

            // 检查数据是否过期（Repository 决定缓存策略）
            val isCacheStale = repository.isCacheStale(city.id, RefreshPolicy.URGENT_NOTIFICATION_CACHE_TTL_MS)
            FileLogger.i(TAG, "doWork: 缓存过期=$isCacheStale, city=${city.name}")

            if (isCacheStale) {
                // 数据过期，通过 RefreshManager 请求同步
                try {
                    refreshManager.requestSync(SyncReason.PERIODIC)
                } catch (e: Exception) {
                    FileLogger.w(TAG, "doWork: 同步失败 - ${e.message}")
                }
            }

            // 从 Room 读取天气数据
            val weather = repository.getWeatherFromCache(city.id)
            if (weather == null) {
                FileLogger.w(TAG, "doWork: Room 无天气数据，跳过")
                return Result.success()
            }

            // Initialize deduplicator and clean up expired records
            val dedup = NotificationDeduplicator(context)
            dedup.cleanup()

            val realtime = weather.result?.realtime
            val alerts = weather.result?.alert?.content
            FileLogger.i(TAG, "doWork: alerts.size=${alerts?.size ?: 0}, " +
                "titles=${alerts?.map { it.title }}")

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
                else -> "降水"
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

            // Weather warning alert — dedup by title
            if (isPremium && prefs.getBoolean("warning_alert", true)) {
                alerts?.forEach { alert ->
                    if (alert.status != "active") return@forEach

                    val title = alert.title ?: ""

                    val cleanTitle = title
                        .replace(Regex("\\[.*?\\]"), "")
                        .replace(Regex("^.*(?:发布|变更|继续|更新)"), "")
                        .replace(Regex("预警.*$"), "预警")
                        .trim()
                    if (cleanTitle.isNotBlank()) {
                        val warningKey = WarningNotificationKey.from(alert, cleanTitle)
                        val shouldNotify = dedup.shouldNotifyWarningEvent(warningKey)
                        FileLogger.i(TAG, "doWork: 预警 cleanTitle=$cleanTitle, shouldNotify=$shouldNotify, key=$warningKey")
                        if (shouldNotify) {
                            val description = alert.description ?: ""
                            val body = if (!description.isNullOrBlank()) {
                                truncateToTwoLines(description)
                            } else {
                                cleanTitle
                            }
                            FileLogger.i(TAG, "doWork: 发送预警通知 title=$cleanTitle")
                            sendNotification(nm, 2, cleanTitle, body)
                        }
                    }
                }
            }

            FileLogger.i(TAG, "doWork: 执行完成")
            Result.success()
        } catch (e: Exception) {
            FileLogger.e(TAG, "doWork: 异常 - ${e.message}", e)
            Log.w(TAG, "Urgent notification check failed", e)
            Result.retry()
        }
    }

    private fun createChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(WeatherNotificationWorker.CHANNEL_ID, "天气提醒", NotificationManager.IMPORTANCE_DEFAULT)
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

    private fun truncateToTwoLines(text: String, maxCharsPerLine: Int = 50): String {
        val maxTotal = maxCharsPerLine * 2
        val cleanText = text.replace("\r\n", "\n").replace("\r", "\n").trim()

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

            if (line.length > maxCharsPerLine) {
                val firstPart = line.take(maxCharsPerLine)
                val secondPart = line.drop(maxCharsPerLine)

                if (lineCount == 0) {
                    result.append(firstPart)
                    result.append("\n")
                    lineCount++

                    if (secondPart.length > maxCharsPerLine) {
                        result.append(secondPart.take(maxCharsPerLine - 1)).append("…")
                        lineCount++
                        break
                    } else {
                        result.append(secondPart)
                        lineCount++
                    }
                } else if (lineCount == 1) {
                    result.append(firstPart.take(maxCharsPerLine - 1)).append("…")
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

        val resultText = result.toString().trimEnd()
        return if (resultText.length > maxTotal) {
            resultText.take(maxTotal - 1) + "…"
        } else {
            resultText
        }
    }
}
