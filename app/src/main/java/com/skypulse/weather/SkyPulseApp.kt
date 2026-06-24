package com.skypulse.weather

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.skypulse.weather.notification.WeatherNotificationScheduler
import com.skypulse.weather.util.FileLogger
import com.skypulse.weather.widget.WeatherWidgetProvider

@HiltAndroidApp
class SkyPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 临时: 初始化文件日志 (测试完毕后删除此行)
        FileLogger.init(this)

        WeatherNotificationScheduler.scheduleIfNeeded(this)
        WeatherWidgetProvider.enqueueWorker(this)
    }
}
