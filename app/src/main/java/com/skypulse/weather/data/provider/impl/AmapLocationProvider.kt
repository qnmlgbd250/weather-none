package com.skypulse.weather.data.provider.impl

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.skypulse.weather.data.provider.ILocationProvider
import com.skypulse.weather.data.provider.model.SimpleLocation
import com.skypulse.weather.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AmapLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ILocationProvider {

    companion object {
        private const val TAG = "AmapLocationProvider"
        private const val AMAP_CALLBACK_GRACE_MS = 1_500L
        private var privacyAgreed = false

        fun ensurePrivacyAgreed(context: Context) {
            if (!privacyAgreed) {
                try {
                    AMapLocationClient.updatePrivacyShow(context, true, true)
                    AMapLocationClient.updatePrivacyAgree(context, true)
                    privacyAgreed = true
                } catch (_: Exception) {}
            }
        }
    }

    private data class TimedNullableResult<T>(
        val value: T?
    )

    override suspend fun requestLocation(highAccuracy: Boolean, timeoutMillis: Long): SimpleLocation? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        ensurePrivacyAgreed(context)
        // 为了确保步行/驾车等商业场景高精度，高德定位通常均使用 Hight_Accuracy，通过模式参数动态匹配
        val targetMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy

        val startMs = android.os.SystemClock.elapsedRealtime()
        FileLogger.locI(TAG, "amap_start: mode=$targetMode, timeout=${timeoutMillis}ms")

        try {
            val result = withTimeoutOrNull(timeoutMillis + AMAP_CALLBACK_GRACE_MS) {
                val client = AMapLocationClient(context)
                val option = AMapLocationClientOption().apply {
                    isOnceLocation = true
                    isNeedAddress = true
                    locationMode = targetMode
                    httpTimeOut = timeoutMillis
                }
                client.setLocationOption(option)

                TimedNullableResult(suspendCancellableCoroutine { cont ->
                    client.setLocationListener { location ->
                        if (cont.isActive) {
                            if (location != null && location.errorCode == 0) {
                                Log.i(TAG, "AMap 定位成功: ${location.latitude}, ${location.longitude}")
                                FileLogger.i(TAG, "AMap 定位成功: lat=${location.latitude}, lon=${location.longitude}, " +
                                    "city=${location.city}, district=${location.district}, " +
                                    "aoi=${location.aoiName}, street=${location.street}")
                                FileLogger.locI(TAG, "amap_success: elapsed=${elapsedSince(startMs)}ms, ${location.locSummary()}, address=${location.address.safeLogValue()}")
                                cont.resume(location)
                            } else {
                                Log.w(TAG, "AMap 定位失败: errorCode=${location?.errorCode}, errorDetail=${location?.locationDetail}")
                                FileLogger.w(TAG, "AMap 定位失败: errorCode=${location?.errorCode}, errorDetail=${location?.locationDetail}")
                                FileLogger.locW(TAG, "amap_failed: elapsed=${elapsedSince(startMs)}ms, errorCode=${location?.errorCode}, errorInfo=${location?.errorInfo.safeLogValue()}, detail=${location?.locationDetail.safeLogValue()}")
                                cont.resume(null)
                            }
                        }
                        client.stopLocation()
                        client.onDestroy()
                    }
                    client.startLocation()
                    cont.invokeOnCancellation {
                        client.stopLocation()
                        client.onDestroy()
                    }
                })
            }
            if (result == null) {
                Log.w(TAG, "AMap 定位硬超时: ${timeoutMillis + AMAP_CALLBACK_GRACE_MS}ms")
                FileLogger.w(TAG, "AMap 定位硬超时: ${timeoutMillis + AMAP_CALLBACK_GRACE_MS}ms")
                FileLogger.locW(TAG, "amap_timeout: elapsed=${elapsedSince(startMs)}ms, timeout=${timeoutMillis + AMAP_CALLBACK_GRACE_MS}ms")
            }
            result?.value?.toSimpleLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AMap location failed", e)
            FileLogger.locE(TAG, "amap_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message.safeLogValue()}", e)
            null
        }
    }

    private fun elapsedSince(startMs: Long): Long = android.os.SystemClock.elapsedRealtime() - startMs

    private fun AMapLocation.locSummary(): String {
        return "lat=${latitude}, lon=${longitude}, accuracy=${accuracy}m, " +
            "city=${city.safeLogValue()}, district=${district.safeLogValue()}, " +
            "aoi=${aoiName.safeLogValue()}, street=${street.safeLogValue()}, " +
            "streetNum=${streetNum.safeLogValue()}"
    }

    private fun String?.safeLogValue(maxLength: Int = 80): String {
        val value = this?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() } ?: "null"
        return if (value.length <= maxLength) value else value.take(maxLength) + "..."
    }

    private fun AMapLocation.toSimpleLocation(): SimpleLocation {
        return SimpleLocation(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            time = time,
            provider = provider,
            cityName = city,
            districtName = district,
            aoiName = aoiName,
            street = street,
            streetNum = streetNum,
            address = address
        )
    }
}
