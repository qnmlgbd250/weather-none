package com.skypulse.weather.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.skypulse.weather.data.ActivationResult
import com.skypulse.weather.data.MembershipRepository
import com.skypulse.weather.data.SettingsRepository
import com.skypulse.weather.data.WeatherSettings
import com.skypulse.weather.notification.WeatherNotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val membershipRepository: MembershipRepository
) : ViewModel() {

    val settings: StateFlow<WeatherSettings> = settingsRepository.settings

    val isPremium: StateFlow<Boolean> = membershipRepository.isPremium

    fun activateCode(code: String): ActivationResult {
        return membershipRepository.activateCode(code)
    }

    fun getDeviceId(): String = membershipRepository.getDeviceId()

    fun getActivatedAt(): Long = membershipRepository.getActivatedAt()

    fun setRainAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setRainAlert(enabled)
    }

    fun setWarningAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setWarningAlert(enabled)
    }

    fun setTempChangeAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setTempChangeAlert(enabled)
    }

    fun setWindAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setWindAlert(enabled)
    }

    fun setTyphoonAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setTyphoonAlert(enabled)
    }

    fun setShowHourlyAqi(enabled: Boolean) = settingsRepository.setShowHourlyAqi(enabled)
    fun setShowHourlyUv(enabled: Boolean) = settingsRepository.setShowHourlyUv(enabled)
    fun setShowHourlyWind(enabled: Boolean) = settingsRepository.setShowHourlyWind(enabled)
    fun setShowHourlyWindGust(enabled: Boolean) = settingsRepository.setShowHourlyWindGust(enabled)
    fun setShowCardDetail(enabled: Boolean) = settingsRepository.setShowCardDetail(enabled)
    fun setShowCardSunriseSunset(enabled: Boolean) = settingsRepository.setShowCardSunriseSunset(enabled)
    fun setShowCardMinutely(enabled: Boolean) = settingsRepository.setShowCardMinutely(enabled)

    private fun updateAlertSetting(update: () -> Unit) {
        update()
        WeatherNotificationScheduler.scheduleIfNeeded(appContext)
    }
}