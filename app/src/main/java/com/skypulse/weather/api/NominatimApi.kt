package com.skypulse.weather.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NominatimResult(
    @Json(name = "place_id") val placeId: Long,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "lat") val lat: String,
    @Json(name = "lon") val lon: String,
    @Json(name = "type") val type: String?,
    @Json(name = "address") val address: NominatimAddress?
)

@JsonClass(generateAdapter = true)
data class NominatimAddress(
    @Json(name = "city") val city: String?,
    @Json(name = "town") val town: String?,
    @Json(name = "county") val county: String?,
    @Json(name = "state") val state: String?,
    @Json(name = "country") val country: String?
)
