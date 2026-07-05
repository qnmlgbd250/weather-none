package com.skypulse.weather.notification

internal data class WindAlertInfo(
    val speed: Double,
    val level: String,
    val isGust: Boolean
)

internal object WindAlertPolicy {
    private const val ALERT_THRESHOLD_KMH = 38.9

    fun evaluate(windSpeed: Double?, gustSpeed: Double?): WindAlertInfo? {
        val wind = candidate(windSpeed, isGust = false)
        val gust = candidate(gustSpeed, isGust = true)
        return listOfNotNull(wind, gust).maxByOrNull { it.speed }
    }

    private fun candidate(speed: Double?, isGust: Boolean): WindAlertInfo? {
        val value = speed ?: return null
        if (value < ALERT_THRESHOLD_KMH) return null
        return WindAlertInfo(
            speed = value,
            level = windLevel(value),
            isGust = isGust
        )
    }

    private fun windLevel(speed: Double): String {
        return when {
            speed >= 88.2 -> "9级"
            speed >= 74.9 -> "8级"
            speed >= 61.9 -> "7级"
            speed >= 50.0 -> "6级"
            else -> "5级"
        }
    }
}
