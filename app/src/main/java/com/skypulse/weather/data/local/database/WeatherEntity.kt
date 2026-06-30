package com.skypulse.weather.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather")
data class WeatherEntity(
    @PrimaryKey
    val cityId: String,
    val responseJson: String,
    val lastUpdated: Long
)
