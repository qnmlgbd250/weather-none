package com.skypulse.weather.data

import android.location.LocationManager

object LocationTrustPolicy {
    private const val TRUSTED_CACHE_ACCURACY_METERS = 100f
    private const val STRONG_FIRST_FIX_ACCURACY_METERS = 80f
    private const val GPS_FIRST_FIX_ACCURACY_METERS = 120f

    fun isTrustedCachedAccuracy(accuracyMeters: Float): Boolean {
        return accuracyMeters > 0f && accuracyMeters <= TRUSTED_CACHE_ACCURACY_METERS
    }

    fun isStrongFirstFix(provider: String?, accuracyMeters: Float): Boolean {
        if (accuracyMeters <= 0f) return false
        if (provider == LocationManager.GPS_PROVIDER) {
            return accuracyMeters <= GPS_FIRST_FIX_ACCURACY_METERS
        }
        return accuracyMeters <= STRONG_FIRST_FIX_ACCURACY_METERS
    }

    fun needsHighAccuracyConfirmation(cachedAccuracyMeters: Float?): Boolean {
        return cachedAccuracyMeters == null || !isTrustedCachedAccuracy(cachedAccuracyMeters)
    }
}
