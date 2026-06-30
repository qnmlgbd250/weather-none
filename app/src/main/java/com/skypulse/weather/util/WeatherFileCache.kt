package com.skypulse.weather.util

import android.content.Context
import com.skypulse.weather.model.WeatherResponse
import com.squareup.moshi.Moshi
import java.io.File

/**
 * 轻量文件缓存，用于跨进程天气数据共享（Widget）。
 *
 * 取代 WeatherCache（SharedPreferences），解决：
 * - SharedPreferences 与 Room 的数据不一致
 * - 跨进程 DataStore 不可用
 *
 * 写入方：WeatherSyncManager / WeatherViewModel
 * 读取方：WeatherWidgetProvider（无 DI 注入的静态方法）
 */
object WeatherFileCache {

    private const val DIR_NAME = "weather_cache"
    private const val FILE_PREFIX = "weather_"
    private const val FILE_SUFFIX = ".json"

    private val adapter by lazy { Moshi.Builder().build().adapter(WeatherResponse::class.java) }

    fun save(context: Context, cityId: String, weather: WeatherResponse) {
        try {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$FILE_PREFIX$cityId$FILE_SUFFIX")
            file.writeText(adapter.toJson(weather))
        } catch (_: Exception) {}
    }

    fun load(context: Context, cityId: String): WeatherResponse? {
        return try {
            val file = File(context.filesDir, "$DIR_NAME/$FILE_PREFIX$cityId$FILE_SUFFIX")
            if (!file.exists()) return null
            adapter.fromJson(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun delete(context: Context, cityId: String) {
        try {
            val file = File(context.filesDir, "$DIR_NAME/$FILE_PREFIX$cityId$FILE_SUFFIX")
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }
}
