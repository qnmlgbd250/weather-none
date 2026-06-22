package com.skypulse.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.permissionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sky_pulse_permission")

@Singleton
class PermissionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return context.permissionDataStore.data.map { prefs ->
            prefs[ONBOARDING_COMPLETED] ?: false
        }.first()
    }

    suspend fun setOnboardingCompleted() {
        context.permissionDataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = true
        }
    }
}