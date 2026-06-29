package com.skypulse.weather.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WeatherNotificationScheduler {
    const val PREFS_NAME = "notification_prefs"

    private val alertDefaults = mapOf(
        "rain_alert" to true,
        "warning_alert" to true,
        "temp_change_alert" to false,
        "wind_alert" to false,
        "typhoon_alert" to true
    )

    fun scheduleIfNeeded(context: Context) {
        val appContext = context.applicationContext
        if (!hasAnyAlertEnabled(appContext)) {
            cancel(appContext)
            return
        }

        val request = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WeatherNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )

        // High-frequency worker for time-sensitive alerts (rain, warnings)
        val urgentRequest = PeriodicWorkRequestBuilder<UrgentNotificationWorker>(10, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UrgentNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            urgentRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(WeatherNotificationWorker.WORK_NAME)
            cancelUniqueWork(UrgentNotificationWorker.WORK_NAME)
        }
    }

    fun hasAnyAlertEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return alertDefaults.any { (key, defaultValue) ->
            prefs.getBoolean(key, defaultValue)
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        val appContext = context.applicationContext
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        return runtimePermissionGranted &&
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
}
