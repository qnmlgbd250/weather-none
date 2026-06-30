package com.skypulse.weather.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val isCurrentLocation: Boolean = false,
    val sortOrder: Int = 0
)
