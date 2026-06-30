package com.skypulse.weather

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.skypulse.weather.notification.WeatherNotificationScheduler
import com.skypulse.weather.util.FileLogger
import com.amap.api.location.AMapLocationClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.skypulse.weather.widget.WeatherWidgetProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SkyPulseApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        FileLogger.initCrashHandler()

        WeatherNotificationScheduler.scheduleIfNeeded(this)
        WeatherWidgetProvider.enqueueWorker(this)

        // 预热 AMap SDK：冷启动时 SDK 内部初始化需要较长时间，
        // 提前触发初始化，避免首次定位请求超时
        preWarmAmapSdk()
    }

    /**
     * 在后台线程预热 AMap SDK。
     * 通过创建→启动→销毁一个 AMapLocationClient 来触发 SDK 内部初始化。
     * 用户完成引导页（至少需要几秒交互）后 SDK 已就绪。
     */
    private fun preWarmAmapSdk() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                AMapLocationClient.updatePrivacyShow(this@SkyPulseApp, true, true)
                AMapLocationClient.updatePrivacyAgree(this@SkyPulseApp, true)
                val client = AMapLocationClient(this@SkyPulseApp)
                client.setLocationOption(
                    com.amap.api.location.AMapLocationClientOption()
                        .apply {
                            isOnceLocation = true
                            isNeedAddress = false
                        }
                )
                client.startLocation()
                delay(2_000L)
                client.stopLocation()
                client.onDestroy()
                Log.d("SkyPulseApp", "AMap SDK 预热完成")
            } catch (e: Exception) {
                Log.w("SkyPulseApp", "AMap SDK 预热失败", e)
            }
        }
    }
}
