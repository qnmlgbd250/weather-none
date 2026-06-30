package com.skypulse.weather.util

import android.content.Context
import com.skypulse.weather.model.City
import com.squareup.moshi.Moshi
import java.io.File

/**
 * 城市列表的文件缓存，用于 Widget 跨进程兼容。
 *
 * WidgetProvider 是静态 BroadcastReceiver，无法使用 Hilt 注入 Room。
 * 通过文件缓存保持与 Room 的数据同步。
 *
 * 写入方：CityRepository（每次 saveCities 时同步写入）
 * 读取方：WeatherWidgetProvider（静态方法，无 DI）
 */
object CityFileCache {

    private const val FILE_NAME = "cities_cache.json"

    private val adapter by lazy { Moshi.Builder().build().adapter(City::class.java) }

    fun save(context: Context, cities: List<City>) {
        try {
            val json = buildString {
                append("[")
                cities.forEachIndexed { index, city ->
                    if (index > 0) append(",")
                    append(adapter.toJson(city))
                }
                append("]")
            }
            File(context.filesDir, FILE_NAME).writeText(json)
        } catch (_: Exception) {}
    }

    fun load(context: Context): List<City> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            val json = file.readText()
            if (json.isBlank() || json == "[]") return emptyList()
            val cities = mutableListOf<City>()
            val reader = com.squareup.moshi.JsonReader.of(okio.Buffer().writeUtf8(json))
            reader.beginArray()
            while (reader.hasNext()) {
                adapter.fromJson(reader)?.let { cities.add(it) }
            }
            reader.endArray()
            cities
        } catch (_: Exception) {
            emptyList()
        }
    }
}
