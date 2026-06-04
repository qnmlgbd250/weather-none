package com.skypulse.weather.widget

import android.content.Context
import android.util.Log
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
                // 先用缓存立即刷新 UI
                val cached = cache.load(city.id)
                WeatherWidgetUpdater.updateAll(context, cached, city.name)

                // 再从 API 拉取最新数据
                try {
                    val api = createCaiyunApi(moshi)
                    val repo = WeatherRepository(api)
                    val result = repo.getWeather(city.longitude, city.latitude)
                    result.getOrNull()?.let { fresh ->
                        cache.save(city.id, fresh)
                        // 用最新数据再次刷新 UI
                        WeatherWidgetUpdater.updateAll(context, fresh, city.name)
                    }
                } catch (e: Exception) {
                    Log.w("WidgetWorker", "API fetch failed, using cache", e)
                }
            } else {
                WeatherWidgetUpdater.updateAll(context, null, null)
            }
            Result.success()
        } catch (_: Exception) {
            try { WeatherWidgetUpdater.updateAll(applicationContext, null, null) } catch (_: Exception) {}
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