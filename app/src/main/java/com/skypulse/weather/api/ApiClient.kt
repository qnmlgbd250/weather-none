package com.skypulse.weather.api

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kept for backward compatibility with widget and other non-DI entry points.
 * Prefer injecting CaiyunApi via Hilt in Activity/ViewModel code.
 */
@Singleton
class ApiClient @Inject constructor(
    val moshi: Moshi,
    val caiyunApi: CaiyunApi,
    val okHttpClient: OkHttpClient
)
