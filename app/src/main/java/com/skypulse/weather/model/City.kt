package com.skypulse.weather.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class City(
    val id: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val isCurrentLocation: Boolean = false
)
