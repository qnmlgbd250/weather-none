package com.skypulse.weather.widget

object WidgetRefreshPolicy {
    const val PERIODIC_REFRESH_MINUTES = 10L
    const val MOVEMENT_THRESHOLD_METERS = 1_000f
    const val WEATHER_TTL_MILLIS = 30 * 60 * 1000L

    fun hasMovedSignificantly(distanceMeters: Float?): Boolean {
        return distanceMeters == null || distanceMeters >= MOVEMENT_THRESHOLD_METERS
    }

    fun isWeatherCacheStale(lastFetchTimeMillis: Long?, nowMillis: Long): Boolean {
        return lastFetchTimeMillis == null ||
            nowMillis - lastFetchTimeMillis >= WEATHER_TTL_MILLIS
    }

    fun shouldFetchWeather(
        distanceMeters: Float?,
        lastFetchTimeMillis: Long?,
        nowMillis: Long
    ): Boolean {
        return hasMovedSignificantly(distanceMeters) ||
            isWeatherCacheStale(lastFetchTimeMillis, nowMillis)
    }
}
