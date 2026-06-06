package com.skypulse.weather

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.skypulse.weather.notification.WeatherNotificationScheduler

@HiltAndroidApp
class SkyPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WeatherNotificationScheduler.scheduleIfNeeded(this)
    }
}
