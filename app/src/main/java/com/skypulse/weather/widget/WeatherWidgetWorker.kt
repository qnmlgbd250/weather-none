package com.skypulse.weather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skypulse.weather.api.CaiyunApi
import com.skypulse.weather.data.CityManager
import com.skypulse.weather.data.WeatherCache
import com.skypulse.weather.repository.WeatherRepository
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class WeatherWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val moshi = Moshi.Builder().build()
            val cities = CityManager(context, moshi).getCities()
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()

            if (city != null) {
                val cache = WeatherCache(context)
                // Try to fetch fresh data from API
                try {
                    val api = createCaiyunApi(moshi)
                    val repo = WeatherRepository(api)
                    val result = repo.getWeather(city.longitude, city.latitude)
                    result.getOrNull()?.let { cache.save(city.id, it) }
                } catch (_: Exception) {
                    // API failed, use cached data
                }
                val weather = cache.load(city.id)
                WeatherWidgetUpdater.updateAll(context, weather, city.name)
            } else {
                WeatherWidgetUpdater.updateAll(context, null, null)
            }
            Result.success()
        } catch (_: Exception) {
            WeatherWidgetUpdater.updateAll(applicationContext, null, null)
            Result.success()
        }
    }

    private fun createCaiyunApi(moshi: Moshi): CaiyunApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://wrapper.cyapi.cn/")
            .client(client)
            .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(CaiyunApi::class.java)
    }
}