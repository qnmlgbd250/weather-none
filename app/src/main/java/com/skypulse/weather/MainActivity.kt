package com.skypulse.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skypulse.weather.ui.screen.WeatherScreen
import com.skypulse.weather.ui.theme.SkyPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkyPulseTheme {
                WeatherScreen()
            }
        }
    }
}
