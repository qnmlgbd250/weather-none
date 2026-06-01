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
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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
                val cache = WeatherCache(context, moshi)
                val api = createCaiyunApi(moshi)
                val repo = WeatherRepository(api)
                val cached = cache.load(city.id)
                if (cached == null) {
                    repo.getWeather(city.longitude, city.latitude).getOrNull()?.let {
                        cache.save(city.id, it)
                    }
                } else {
                    repo.getWeather(city.longitude, city.latitude).getOrNull()?.let {
                        cache.save(city.id, it)
                    }
                }
            }
            WeatherWidgetUpdater.updateAll(context)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun createCaiyunApi(moshi: Moshi): CaiyunApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://wrapper.cyapi.cn/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(CaiyunApi::class.java)
    }
}
