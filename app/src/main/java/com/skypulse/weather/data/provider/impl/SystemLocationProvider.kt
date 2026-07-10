package com.skypulse.weather.data.provider.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.skypulse.weather.data.provider.ILocationProvider
import com.skypulse.weather.data.provider.model.SimpleLocation
import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SystemLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ILocationProvider {

    companion object {
        private const val TAG = "SystemLocProvider"
        private const val GOOD_ACCURACY_METERS = 60f
        private const val ACCEPTABLE_ACCURACY_METERS = 120f
        private const val RECENT_ACTIVE_FIX_MS = 60 * 1000L
        private const val RECENT_LAST_KNOWN_MS = 90 * 1000L
    }

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun requestLocation(highAccuracy: Boolean, timeoutMillis: Long): SimpleLocation? = withContext(Dispatchers.IO) {
        val startMs = android.os.SystemClock.elapsedRealtime()
        FileLogger.locI(TAG, "system_location_start: timeout=${timeoutMillis}ms, highAccuracy=$highAccuracy")
        if (!hasLocationPermission()) {
            Log.w(TAG, "requestLocation: 无定位权限，跳过")
            FileLogger.locW(TAG, "system_location_no_permission: elapsed=${elapsedSince(startMs)}ms")
            return@withContext null
        }

        // 1. 尝试 GMS Fused Location
        val hasGms = try {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (_: Exception) {
            false
        }

        FileLogger.locI(TAG, "system_location_gms_check: elapsed=${elapsedSince(startMs)}ms, hasGms=$hasGms")

        if (hasGms) {
            Log.i(TAG, "尝试 GMS FusedLocation, highAccuracy=$highAccuracy...")
            val fusedStartMs = android.os.SystemClock.elapsedRealtime()
            val fused = try {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                    requestFusedLocation(timeoutMillis, highAccuracy)
                }
            } catch (e: Exception) {
                Log.w(TAG, "GMS FusedLocation timeout or exception: ${e.message}")
                FileLogger.locW(TAG, "fused_exception: elapsed=${elapsedSince(fusedStartMs)}ms, message=${e.message}")
                null
            }
            if (fused != null) {
                Log.i(TAG, "GMS FusedLocation 定位成功: lat=${fused.latitude}, lon=${fused.longitude}")
                FileLogger.locI(TAG, "fused_success: elapsed=${elapsedSince(fusedStartMs)}ms, total=${elapsedSince(startMs)}ms, ${fused.locSummary()}")
                return@withContext fused.toSimpleLocation()
            }
            FileLogger.locW(TAG, "fused_failed_or_timeout: elapsed=${elapsedSince(fusedStartMs)}ms, timeout=${timeoutMillis}ms")
            Log.w(TAG, "GMS FusedLocation 失败或超时，降级尝试原生 LocationManager...")
        }

        // 2. 尝试原生 LocationManager
        val nativeStartMs = android.os.SystemClock.elapsedRealtime()
        val native = requestNativeLocation(timeoutMillis, highAccuracy)
        if (native != null) {
            FileLogger.locI(TAG, "native_success_after_system_fallback: elapsed=${elapsedSince(nativeStartMs)}ms, total=${elapsedSince(startMs)}ms, ${native.locSummary()}")
            native.toSimpleLocation()
        } else {
            FileLogger.locW(TAG, "native_failed_after_system_fallback: elapsed=${elapsedSince(nativeStartMs)}ms, total=${elapsedSince(startMs)}ms")
            null
        }
    }

    private suspend fun requestFusedLocation(timeoutMillis: Long, highAccuracy: Boolean): Location? {
        val startMs = android.os.SystemClock.elapsedRealtime()
        return try {
            val location = suspendCancellableCoroutine<Location?> { cont ->
                try {
                    val hasFine = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val priority = if (hasFine) {
                        if (highAccuracy) {
                            Priority.PRIORITY_HIGH_ACCURACY
                        } else {
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY
                        }
                    } else {
                        Priority.PRIORITY_LOW_POWER
                    }
                    val requestInterval = if (highAccuracy) 1000L else timeoutMillis
                    FileLogger.locD(TAG, "fused_request: priority=$priority, interval=${requestInterval}ms, maxUpdates=${if (highAccuracy) 4 else 1}, hasFine=$hasFine")
                    val request = LocationRequest.Builder(priority, requestInterval)
                        .setMaxUpdates(if (highAccuracy) 4 else 1)
                        .setMinUpdateIntervalMillis(requestInterval)
                        .build()

                    var bestLocation: Location? = null
                    var finished = false
                    val handler = android.os.Handler(android.os.Looper.getMainLooper())
                    lateinit var callback: LocationCallback

                    fun finish() {
                        if (finished) return
                        finished = true
                        handler.removeCallbacksAndMessages(null)
                        try {
                            fusedLocationClient.removeLocationUpdates(callback)
                        } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(bestLocation)
                    }

                    val finishRunnable = Runnable { finish() }

                    callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val locations = result.locations.ifEmpty { listOfNotNull(result.lastLocation) }
                            locations.forEach { loc ->
                                FileLogger.locD(TAG, "fused_callback_location: elapsed=${elapsedSince(startMs)}ms, ${loc.locSummary()}")
                                bestLocation = betterLocation(bestLocation, loc)
                            }
                            val best = bestLocation
                            if (!highAccuracy || best.isGoodEnough()) {
                                finish()
                            }
                        }
                    }

                    cont.invokeOnCancellation {
                        finished = true
                        handler.removeCallbacksAndMessages(null)
                        try {
                            fusedLocationClient.removeLocationUpdates(callback)
                        } catch (_: Exception) {}
                    }

                    if (highAccuracy) {
                        handler.postDelayed(finishRunnable, timeoutMillis)
                    }
                    fusedLocationClient.requestLocationUpdates(
                        request,
                        callback,
                        android.os.Looper.getMainLooper()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "FusedLocation 请求异常", e)
                    FileLogger.locE(TAG, "fused_request_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
            FileLogger.locI(TAG, "fused_complete: elapsed=${elapsedSince(startMs)}ms, result=${location?.locSummary() ?: "null"}")
            location
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "FusedLocation 异常", e)
            FileLogger.locE(TAG, "fused_exception_outer: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
            null
        }
    }

    private suspend fun requestNativeLocation(timeoutMillis: Long, highAccuracy: Boolean = false): Location? {
        val startMs = android.os.SystemClock.elapsedRealtime()
        FileLogger.locI(TAG, "native_start: timeout=${timeoutMillis}ms, highAccuracy=$highAccuracy")
        return try {
            val nativeLocManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (nativeLocManager == null) {
                Log.w(TAG, "NativeLocation: 无法获取系统 LocationManager")
                FileLogger.locW(TAG, "native_no_manager: elapsed=${elapsedSince(startMs)}ms")
                return null
            }

            // 1. 优先检查并复用近期有效的高精度 LastKnownLocation
            val gpsLastKnown = try {
                nativeLocManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            } catch (_: SecurityException) { null }
            if (gpsLastKnown != null && shouldUseLastKnown(gpsLastKnown, highAccuracy)) {
                Log.i(TAG, "NativeLocation: 使用近期的 GPS lastKnown: lat=${gpsLastKnown.latitude}, lon=${gpsLastKnown.longitude}")
                FileLogger.locI(TAG, "native_last_known_gps_used: elapsed=${elapsedSince(startMs)}ms, ${gpsLastKnown.locSummary()}")
                return gpsLastKnown
            }
            gpsLastKnown?.let { FileLogger.locD(TAG, "native_last_known_gps_rejected: elapsed=${elapsedSince(startMs)}ms, ${it.locSummary()}") }

            val netLastKnown = try {
                nativeLocManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) { null }
            if (netLastKnown != null && shouldUseLastKnown(netLastKnown, highAccuracy)) {
                Log.i(TAG, "NativeLocation: 使用近期的 Network lastKnown: lat=${netLastKnown.latitude}, lon=${netLastKnown.longitude}")
                FileLogger.locI(TAG, "native_last_known_network_used: elapsed=${elapsedSince(startMs)}ms, ${netLastKnown.locSummary()}")
                return netLastKnown
            }
            netLastKnown?.let { FileLogger.locD(TAG, "native_last_known_network_rejected: elapsed=${elapsedSince(startMs)}ms, ${it.locSummary()}") }

            val providers = nativeLocManager.getProviders(true)
            FileLogger.locI(TAG, "native_enabled_providers: elapsed=${elapsedSince(startMs)}ms, providers=${providers.joinToString()}")
            if (providers.isEmpty()) {
                Log.w(TAG, "NativeLocation: 无任何可用的 Location Provider")
                FileLogger.locW(TAG, "native_no_enabled_providers: elapsed=${elapsedSince(startMs)}ms")
                return null
            }

            // 2. 双通道并行定位策略：常规场景取最快；前台强定位采样后择优。
            Log.i(TAG, "NativeLocation: 启动 GPS & Network 双通道并行定位, highAccuracy=$highAccuracy, 超时=${timeoutMillis}ms")
            val activeLoc = if (highAccuracy) {
                requestBestParallelLocation(nativeLocManager, providers, timeoutMillis)
            } else {
                requestParallelLocation(nativeLocManager, providers, timeoutMillis)
            }
            if (activeLoc != null) {
                Log.i(TAG, "NativeLocation: 并行定位成功: provider=${activeLoc.provider}, lat=${activeLoc.latitude}, lon=${activeLoc.longitude}")
                FileLogger.locI(TAG, "native_parallel_success: elapsed=${elapsedSince(startMs)}ms, ${activeLoc.locSummary()}")
                return activeLoc
            }
            FileLogger.locW(TAG, "native_parallel_failed: elapsed=${elapsedSince(startMs)}ms, timeout=${timeoutMillis}ms")

            // 3. 尝试 Passive 定位作为最后原生兜底
            if (providers.contains(android.location.LocationManager.PASSIVE_PROVIDER)) {
                Log.i(TAG, "NativeLocation: 并行定位均超时或失败，尝试 Passive 定位...")
                val passiveLoc = requestSingleProviderLocation(nativeLocManager, android.location.LocationManager.PASSIVE_PROVIDER, 2000L)
                if (passiveLoc != null) {
                    Log.i(TAG, "NativeLocation: Passive 定位成功")
                    FileLogger.locI(TAG, "native_passive_success: elapsed=${elapsedSince(startMs)}ms, ${passiveLoc.locSummary()}")
                    return passiveLoc
                }
                FileLogger.locW(TAG, "native_passive_failed: elapsed=${elapsedSince(startMs)}ms")
            }

            Log.w(TAG, "NativeLocation: 所有并行及兜底定位方式均已失败")
            FileLogger.locW(TAG, "native_all_failed: elapsed=${elapsedSince(startMs)}ms")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "NativeLocation 发生异常", e)
            FileLogger.locE(TAG, "native_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
            null
        }
    }

    private fun shouldUseLastKnown(location: Location, highAccuracy: Boolean): Boolean {
        val age = System.currentTimeMillis() - location.time
        if (age < 0L) return false
        return if (highAccuracy) {
            age <= RECENT_LAST_KNOWN_MS && location.accuracyOrDefault() <= GOOD_ACCURACY_METERS
        } else {
            val maxAge = if (location.provider == android.location.LocationManager.GPS_PROVIDER) {
                20 * 60 * 1000L
            } else {
                15 * 60 * 1000L
            }
            age <= maxAge
        }
    }

    private suspend fun requestBestParallelLocation(
        nativeLocManager: android.location.LocationManager,
        providers: List<String>,
        timeoutMillis: Long
    ): Location? {
        val startMs = android.os.SystemClock.elapsedRealtime()
        return try {
            suspendCancellableCoroutine<Location?> { cont ->
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                var bestLocation: Location? = null
                var finished = false
                lateinit var listener: android.location.LocationListener
                lateinit var finishRunnable: Runnable

                fun finish() {
                    if (finished) return
                    finished = true
                    handler.removeCallbacks(finishRunnable)
                    try {
                        nativeLocManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(bestLocation)
                }

                finishRunnable = Runnable { finish() }
                listener = object : android.location.LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        bestLocation = betterLocation(bestLocation, loc)
                        Log.i(TAG, "BestParallelLocation: 收到 ${loc.provider}, accuracy=${loc.accuracyOrDefault()}m, best=${bestLocation?.provider}")
                        FileLogger.locD(TAG, "best_parallel_callback: elapsed=${elapsedSince(startMs)}ms, candidate=${loc.locSummary()}, best=${bestLocation?.locSummary() ?: "null"}")
                        if (bestLocation.isGoodEnough()) {
                            handler.removeCallbacks(finishRunnable)
                            finish()
                        }
                    }

                    @Deprecated("Deprecated in API")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                cont.invokeOnCancellation {
                    finished = true
                    handler.removeCallbacks(finishRunnable)
                    try {
                        nativeLocManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                }

                try {
                    var registeredAny = false
                    if (providers.contains(android.location.LocationManager.GPS_PROVIDER)) {
                        FileLogger.locD(TAG, "best_parallel_register: provider=gps")
                        nativeLocManager.requestLocationUpdates(
                            android.location.LocationManager.GPS_PROVIDER,
                            0L, 0f, listener, android.os.Looper.getMainLooper()
                        )
                        registeredAny = true
                    }
                    if (providers.contains(android.location.LocationManager.NETWORK_PROVIDER)) {
                        FileLogger.locD(TAG, "best_parallel_register: provider=network")
                        nativeLocManager.requestLocationUpdates(
                            android.location.LocationManager.NETWORK_PROVIDER,
                            0L, 0f, listener, android.os.Looper.getMainLooper()
                        )
                        registeredAny = true
                    }

                    if (registeredAny) {
                        handler.postDelayed(finishRunnable, timeoutMillis)
                    } else if (cont.isActive) {
                        cont.resume(null)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "requestBestParallelLocation SecurityException", e)
                    FileLogger.locE(TAG, "best_parallel_security_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
                    if (cont.isActive) cont.resume(null)
                } catch (e: Exception) {
                    Log.e(TAG, "requestBestParallelLocation Exception", e)
                    FileLogger.locE(TAG, "best_parallel_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "requestBestParallelLocation error", e)
            FileLogger.locE(TAG, "best_parallel_outer_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
            null
        }
    }

    private suspend fun requestParallelLocation(
        nativeLocManager: android.location.LocationManager,
        providers: List<String>,
        timeoutMillis: Long
    ): Location? {
        val startMs = android.os.SystemClock.elapsedRealtime()
        return try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val lock = Any()
                    var finished = false
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            synchronized(lock) {
                                if (!finished) {
                                    finished = true
                                    Log.i(TAG, "ParallelLocation: 收到定位数据来自 ${loc.provider}, lat=${loc.latitude}, lon=${loc.longitude}")
                                    FileLogger.locI(TAG, "parallel_callback_first: elapsed=${elapsedSince(startMs)}ms, ${loc.locSummary()}")
                                    if (cont.isActive) cont.resume(loc)
                                    try {
                                        nativeLocManager.removeUpdates(this)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        @Deprecated("Deprecated in API")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    cont.invokeOnCancellation {
                        synchronized(lock) {
                            finished = true
                        }
                        try {
                            nativeLocManager.removeUpdates(listener)
                        } catch (_: Exception) {}
                    }

                    try {
                        var registeredAny = false
                        if (providers.contains(android.location.LocationManager.GPS_PROVIDER)) {
                            FileLogger.locD(TAG, "parallel_register: provider=gps")
                            nativeLocManager.requestLocationUpdates(
                                android.location.LocationManager.GPS_PROVIDER,
                                0L, 0f, listener, android.os.Looper.getMainLooper()
                            )
                            registeredAny = true
                        }
                        if (providers.contains(android.location.LocationManager.NETWORK_PROVIDER)) {
                            FileLogger.locD(TAG, "parallel_register: provider=network")
                            nativeLocManager.requestLocationUpdates(
                                android.location.LocationManager.NETWORK_PROVIDER,
                                0L, 0f, listener, android.os.Looper.getMainLooper()
                            )
                            registeredAny = true
                        }

                        if (!registeredAny) {
                            FileLogger.locW(TAG, "parallel_no_provider_registered: elapsed=${elapsedSince(startMs)}ms")
                            if (cont.isActive) cont.resume(null)
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "requestParallelLocation SecurityException", e)
                        FileLogger.locE(TAG, "parallel_security_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
                        if (cont.isActive) cont.resume(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "requestParallelLocation Exception", e)
                        FileLogger.locE(TAG, "parallel_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "requestParallelLocation error", e)
            FileLogger.locE(TAG, "parallel_outer_exception: elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
            null
        }
    }

    private suspend fun requestSingleProviderLocation(
        nativeLocManager: android.location.LocationManager,
        provider: String,
        timeoutMillis: Long
    ): Location? {
        val startMs = android.os.SystemClock.elapsedRealtime()
        return try {
            kotlinx.coroutines.withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        val lock = Any()
                        var finished = false
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(loc: Location) {
                                synchronized(lock) {
                                    if (finished) return
                                    finished = true
                                    FileLogger.locI(TAG, "single_provider_callback: provider=$provider, elapsed=${elapsedSince(startMs)}ms, ${loc.locSummary()}")
                                    if (cont.isActive) cont.resume(loc)
                                    try {
                                        nativeLocManager.removeUpdates(this)
                                    } catch (_: Exception) {}
                                }
                            }
                            @Deprecated("Deprecated in API")
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }

                        cont.invokeOnCancellation {
                            synchronized(lock) {
                                finished = true
                            }
                            try {
                                nativeLocManager.removeUpdates(listener)
                            } catch (_: Exception) {}
                        }

                        nativeLocManager.requestLocationUpdates(
                            provider,
                            0L,
                            0f,
                            listener,
                            android.os.Looper.getMainLooper()
                        )
                    } catch (e: SecurityException) {
                        Log.e(TAG, "requestSingleProviderLocation SecurityException", e)
                        FileLogger.locE(TAG, "single_provider_security_exception: provider=$provider, elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
                        if (cont.isActive) cont.resume(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "requestSingleProviderLocation Exception", e)
                        FileLogger.locE(TAG, "single_provider_exception: provider=$provider, elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "requestSingleProviderLocation error", e)
            FileLogger.locE(TAG, "single_provider_outer_exception: provider=$provider, elapsed=${elapsedSince(startMs)}ms, message=${e.message}", e)
            null
        }
    }

    private fun betterLocation(current: Location?, candidate: Location?): Location? {
        if (candidate == null) return current
        if (current == null) return candidate
        val currentAccuracy = current.accuracyOrDefault()
        val candidateAccuracy = candidate.accuracyOrDefault()
        val candidateIsFresh = candidate.time > current.time + 2_000L
        val candidateIsMuchMoreAccurate = candidateAccuracy + 20f < currentAccuracy
        val candidateIsAccurateAndFresh = candidateAccuracy <= currentAccuracy + 25f && candidateIsFresh
        val candidateUsesGps = candidate.provider == android.location.LocationManager.GPS_PROVIDER &&
            current.provider != android.location.LocationManager.GPS_PROVIDER &&
            candidateAccuracy <= ACCEPTABLE_ACCURACY_METERS

        return if (candidateIsMuchMoreAccurate || candidateIsAccurateAndFresh || candidateUsesGps) {
            candidate
        } else {
            current
        }
    }

    private fun Location?.isGoodEnough(): Boolean {
        val location = this ?: return false
        val age = System.currentTimeMillis() - location.time
        return location.accuracyOrDefault() <= GOOD_ACCURACY_METERS && age in 0L..RECENT_ACTIVE_FIX_MS
    }

    private fun Location.accuracyOrDefault(): Float {
        return if (hasAccuracy()) accuracy else Float.MAX_VALUE
    }

    private fun elapsedSince(startMs: Long): Long = android.os.SystemClock.elapsedRealtime() - startMs

    private fun Location.locSummary(): String {
        val ageMs = System.currentTimeMillis() - time
        return "provider=$provider, lat=${latitude}, lon=${longitude}, " +
            "accuracy=${accuracyOrDefault()}m, age=${ageMs}ms, time=$time"
    }

    private fun Location.toSimpleLocation(): SimpleLocation {
        return SimpleLocation(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracyOrDefault(),
            time = time,
            provider = provider
        )
    }
}
