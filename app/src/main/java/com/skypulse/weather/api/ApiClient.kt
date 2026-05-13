package com.skypulse.weather.api

import com.skypulse.weather.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 8,
        @Query("accept-language") language: String = "zh-CN"
    ): List<NominatimResult>
}

object ApiClient {

    const val CAIYUN_TOKEN = "Y2FpeXVuIGFuZHJpb2QgYXBp"

    private const val BASE_URL = "https://wrapper.cyapi.cn/"
    private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"

    private val moshi = Moshi.Builder()
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val nominatimClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "SkyPulse-Weather-Android/1.8.0")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val nominatimRetrofit = Retrofit.Builder()
        .baseUrl(NOMINATIM_BASE_URL)
        .client(nominatimClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val caiyunApi: CaiyunApi = retrofit.create(CaiyunApi::class.java)
    val nominatimApi: NominatimApi = nominatimRetrofit.create(NominatimApi::class.java)
}
