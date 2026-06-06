package com.skypulse.weather

import android.app.Application
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import com.skypulse.weather.notification.WeatherNotificationWorker
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class SkyPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        enqueueNotificationWorker()
    }

    private fun enqueueNotificationWorker() {
        val request = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeatherNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}