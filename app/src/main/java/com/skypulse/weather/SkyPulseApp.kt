package com.skypulse.weather

import android.app.Application
import androidx.work.Configuration

class SkyPulseApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
