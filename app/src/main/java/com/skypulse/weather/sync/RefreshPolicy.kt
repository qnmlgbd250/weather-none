package com.skypulse.weather.sync

/**
 * Centralized refresh timing policy.
 *
 * The constants mirror the previous in-place values so existing refresh behavior
 * stays unchanged while the decisions become testable and easier to audit.
 */
object RefreshPolicy {
    /** Global minimum interval for RefreshManager requests. */
    const val GLOBAL_SYNC_INTERVAL_MS = 90_000L

    /** Foreground/app-level cache TTL used by RefreshManager. */
    const val WEATHER_TTL_MS = 5 * 60 * 1000L

    /** Per-city freshness window used by WeatherSyncManager and UI refresh checks. */
    const val CITY_RATE_LIMIT_MS = 90_000L

    /** Short dedupe window for queued same-city same-coordinate refreshes. */
    const val SAME_COORDINATE_DEDUPE_MS = 5_000L

    /** Periodic notification worker cache TTL. */
    const val NOTIFICATION_CACHE_TTL_MS = 25 * 60 * 1000L

    /** Time-sensitive notification worker cache TTL. */
    const val URGENT_NOTIFICATION_CACHE_TTL_MS = 8 * 60 * 1000L

    fun isGlobalRateLimited(
        nowMillis: Long,
        lastSuccessMillis: Long,
        force: Boolean
    ): Boolean {
        return !force && nowMillis - lastSuccessMillis < GLOBAL_SYNC_INTERVAL_MS
    }

    fun shouldSkipFreshCache(isCacheStale: Boolean, force: Boolean): Boolean {
        return !force && !isCacheStale
    }

    fun isCityRateLimited(
        nowMillis: Long,
        lastFetchMillis: Long?
    ): Boolean {
        return lastFetchMillis != null && nowMillis - lastFetchMillis < CITY_RATE_LIMIT_MS
    }

    fun isSameCoordinateDedupeWindow(
        nowMillis: Long,
        lastFetchMillis: Long?
    ): Boolean {
        return lastFetchMillis != null && nowMillis - lastFetchMillis < SAME_COORDINATE_DEDUPE_MS
    }
}
