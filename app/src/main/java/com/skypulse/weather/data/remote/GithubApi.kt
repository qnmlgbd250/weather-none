package com.skypulse.weather.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Headers

@JsonClass(generateAdapter = true)
data class GithubReleaseResponse(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "html_url") val htmlUrl: String
)

interface GithubApi {
    @Headers("Accept: application/vnd.github.v3+json")
    @GET("repos/qnmlgbd250/weather-none/releases/latest")
    suspend fun getLatestRelease(): GithubReleaseResponse
}
