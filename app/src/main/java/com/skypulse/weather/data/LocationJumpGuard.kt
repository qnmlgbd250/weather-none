package com.skypulse.weather.data

object LocationJumpGuard {
    const val PENDING_TTL_MS = 30 * 60 * 1000L

    private const val CACHE_PROTECTION_MAX_AGE_MS = 30 * 60 * 1000L
    private const val SUSPICIOUS_JUMP_METERS = 1_500f
    private const val LOW_CONFIDENCE_ACCURACY_METERS = 200f
    private const val PENDING_CONFIRM_DISTANCE_METERS = 500f

    fun shouldHoldForConfirmation(
        distanceFromCachedMeters: Float,
        newAccuracyMeters: Float,
        cacheAgeMillis: Long,
        highAccuracy: Boolean
    ): Boolean {
        if (highAccuracy) return false
        if (cacheAgeMillis < 0L || cacheAgeMillis > CACHE_PROTECTION_MAX_AGE_MS) return false
        if (distanceFromCachedMeters < SUSPICIOUS_JUMP_METERS) return false
        return isLowConfidenceAccuracy(newAccuracyMeters)
    }

    fun isConfirmedByPending(
        distanceFromPendingMeters: Float,
        pendingAgeMillis: Long
    ): Boolean {
        return pendingAgeMillis in 0L..PENDING_TTL_MS &&
            distanceFromPendingMeters <= PENDING_CONFIRM_DISTANCE_METERS
    }

    fun isPendingExpired(pendingAgeMillis: Long): Boolean {
        return pendingAgeMillis !in 0L..PENDING_TTL_MS
    }

    private fun isLowConfidenceAccuracy(accuracyMeters: Float): Boolean {
        return accuracyMeters <= 0f || accuracyMeters > LOW_CONFIDENCE_ACCURACY_METERS
    }
}
