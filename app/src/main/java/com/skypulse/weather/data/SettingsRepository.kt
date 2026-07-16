package com.skypulse.weather.data

import android.content.Context
import android.content.SharedPreferences
import com.skypulse.weather.notification.WeatherNotificationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherSettings(
    val rainAlert: Boolean = true,
    val warningAlert: Boolean = true,
    val tempChangeAlert: Boolean = false,
    val windAlert: Boolean = false,
    val typhoonAlert: Boolean = true,
    val showHourlyAqi: Boolean = true,
    val showHourlyUv: Boolean = true,
    val showHourlyWind: Boolean = true,
    val showHourlyWindGust: Boolean = false,
    val showCardDetail: Boolean = true,
    val showCardSunriseSunset: Boolean = true,
    val showCardMinutely: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(
        WeatherNotificationScheduler.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<WeatherSettings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = readSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setRainAlert(enabled: Boolean) = updateBoolean(KEY_RAIN_ALERT, enabled)
    fun setWarningAlert(enabled: Boolean) = updateBoolean(KEY_WARNING_ALERT, enabled)
    fun setTempChangeAlert(enabled: Boolean) = updateBoolean(KEY_TEMP_CHANGE_ALERT, enabled)
    fun setWindAlert(enabled: Boolean) = updateBoolean(KEY_WIND_ALERT, enabled)
    fun setTyphoonAlert(enabled: Boolean) = updateBoolean(KEY_TYPHOON_ALERT, enabled)
    fun setShowHourlyAqi(enabled: Boolean) = updateBoolean(KEY_SHOW_HOURLY_AQI, enabled)
    fun setShowHourlyUv(enabled: Boolean) = updateBoolean(KEY_SHOW_HOURLY_UV, enabled)
    fun setShowHourlyWind(enabled: Boolean) = updateBoolean(KEY_SHOW_HOURLY_WIND, enabled)
    fun setShowHourlyWindGust(enabled: Boolean) = updateBoolean(KEY_SHOW_HOURLY_WIND_GUST, enabled)
    fun setShowCardDetail(enabled: Boolean) = updateBoolean(KEY_SHOW_CARD_DETAIL, enabled)
    fun setShowCardSunriseSunset(enabled: Boolean) = updateBoolean(KEY_SHOW_CARD_SUNRISE_SUNSET, enabled)
    fun setShowCardMinutely(enabled: Boolean) = updateBoolean(KEY_SHOW_CARD_MINUTELY, enabled)

    private fun updateBoolean(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
        _settings.value = readSettings()
    }

    private fun readSettings(): WeatherSettings = WeatherSettings(
        rainAlert = prefs.getBoolean(KEY_RAIN_ALERT, true),
        warningAlert = prefs.getBoolean(KEY_WARNING_ALERT, true),
        tempChangeAlert = prefs.getBoolean(KEY_TEMP_CHANGE_ALERT, false),
        windAlert = prefs.getBoolean(KEY_WIND_ALERT, false),
        typhoonAlert = prefs.getBoolean(KEY_TYPHOON_ALERT, true),
        showHourlyAqi = prefs.getBoolean(KEY_SHOW_HOURLY_AQI, true),
        showHourlyUv = prefs.getBoolean(KEY_SHOW_HOURLY_UV, true),
        showHourlyWind = prefs.getBoolean(KEY_SHOW_HOURLY_WIND, true),
        showHourlyWindGust = prefs.getBoolean(KEY_SHOW_HOURLY_WIND_GUST, false),
        showCardDetail = prefs.getBoolean(KEY_SHOW_CARD_DETAIL, true),
        showCardSunriseSunset = prefs.getBoolean(KEY_SHOW_CARD_SUNRISE_SUNSET, true),
        showCardMinutely = prefs.getBoolean(KEY_SHOW_CARD_MINUTELY, true)
    )

    companion object {
        private const val KEY_RAIN_ALERT = "rain_alert"
        private const val KEY_WARNING_ALERT = "warning_alert"
        private const val KEY_TEMP_CHANGE_ALERT = "temp_change_alert"
        private const val KEY_WIND_ALERT = "wind_alert"
        private const val KEY_TYPHOON_ALERT = "typhoon_alert"
        private const val KEY_SHOW_HOURLY_AQI = "show_hourly_aqi"
        private const val KEY_SHOW_HOURLY_UV = "show_hourly_uv"
        private const val KEY_SHOW_HOURLY_WIND = "show_hourly_wind"
        private const val KEY_SHOW_HOURLY_WIND_GUST = "show_hourly_wind_gust"
        private const val KEY_SHOW_CARD_DETAIL = "show_card_detail"
        private const val KEY_SHOW_CARD_SUNRISE_SUNSET = "show_card_sunrise_sunset"
        private const val KEY_SHOW_CARD_MINUTELY = "show_card_minutely"
    }
}