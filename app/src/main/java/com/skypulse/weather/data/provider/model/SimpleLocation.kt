package com.skypulse.weather.data.provider.model

data class SimpleLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val time: Long = 0L,
    val provider: String? = null,
    val cityName: String? = null,
    val districtName: String? = null,
    val aoiName: String? = null,
    val street: String? = null,
    val streetNum: String? = null,
    val address: String? = null
)
