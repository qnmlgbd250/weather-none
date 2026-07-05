package com.skypulse.weather.repository

import com.skypulse.weather.data.local.database.WeatherDao
import com.skypulse.weather.data.local.database.WeatherEntity
import com.skypulse.weather.data.remote.WeatherRemoteDataSource
import com.skypulse.weather.model.HourlyAqiValue
import com.skypulse.weather.model.HourlySkycon
import com.skypulse.weather.model.HourlyUvItem
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.model.HourlyWind
import com.skypulse.weather.model.WeatherResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 天气数据仓库 — 唯一的数据入口。
 *
 * 职责：
 * - 通过 WeatherRemoteDataSource 获取网络数据
 * - 通过 WeatherDao 管理 Room 缓存（SSOT）
 * - 提供 Flow 供 UI 观察
 *
 * 不直接依赖 WeatherApiService，网络请求由 RemoteDataSource 封装。
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val remoteDataSource: WeatherRemoteDataSource,
    private val weatherDao: WeatherDao,
    private val moshi: Moshi
) {
    private val weatherAdapter = moshi.adapter(WeatherResponse::class.java)

    // ============ Network (via RemoteDataSource) ============

    /**
     * 从网络获取天气数据。
     * 包含小时数据过滤（过滤当前时间之前的小时数据）。
     */
    suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean = false
    ): Result<WeatherResponse> {
        val result = remoteDataSource.getWeather(longitude, latitude, includeYesterday)
        return result.map { response ->
            if (includeYesterday) response.withCurrentHourlyWindow() else response
        }
    }

    // ============ Room Cache (SSOT) ============

    /**
     * 观察指定城市的天气数据（Room Flow）。
     * 当 Repository 写入新数据时，所有观察者自动收到更新。
     */
    fun observeWeather(cityId: String): Flow<WeatherEntity?> {
        return weatherDao.observeWeather(cityId)
    }

    /**
     * 观察所有城市的天气数据。
     */
    fun observeAllWeather(): Flow<List<WeatherEntity>> {
        return weatherDao.observeAllWeather()
    }

    /**
     * 从 Room 读取缓存（不检查 TTL），用于立即显示。
     */
    suspend fun getWeatherFromCache(cityId: String): WeatherResponse? = withContext(Dispatchers.Default) {
        val entity = weatherDao.getWeather(cityId) ?: return@withContext null
        parseWeatherEntity(entity)
    }

    fun parseWeatherEntity(entity: WeatherEntity): WeatherResponse? {
        return try {
            weatherAdapter.fromJson(entity.responseJson)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 检查指定城市的缓存是否过期。
     *
     * @param cityId 城市 ID
     * @param ttlMs 缓存有效期（毫秒）
     * @return true 表示缓存已过期或不存在，需要刷新
     */
    suspend fun isCacheStale(cityId: String, ttlMs: Long): Boolean {
        val entity = weatherDao.getWeather(cityId) ?: return true
        return System.currentTimeMillis() - entity.lastUpdated >= ttlMs
    }

    /**
     * 获取指定城市缓存的最后更新时间戳。
     * 用于替代 WeatherSyncManager.getLastFetchTime()，
     * 基于 Room 持久化时间戳（重启后不丢失）。
     */
    suspend fun getLastFetchTime(cityId: String): Long {
        val entity = weatherDao.getWeather(cityId) ?: return 0L
        return entity.lastUpdated
    }

    /**
     * 将天气数据写入 Room 缓存。
     */
    suspend fun saveWeatherToCache(cityId: String, weather: WeatherResponse) = withContext(Dispatchers.Default) {
        val json = weatherAdapter.toJson(weather)
        weatherDao.upsert(
            WeatherEntity(
                cityId = cityId,
                responseJson = json,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    /**
     * 删除指定城市的缓存。
     */
    suspend fun deleteWeatherCache(cityId: String) {
        weatherDao.delete(cityId)
    }

    // ============ Hourly Window Filtering ============

    private fun WeatherResponse.withCurrentHourlyWindow(): WeatherResponse {
        val hourly = result?.hourly ?: return this
        val threshold = currentHour()
        val filteredHourly = hourly.copy(
            precipitation = hourly.precipitation?.filterHourlyValuesFrom(threshold),
            temperature = hourly.temperature?.filterHourlyValuesFrom(threshold),
            apparent_temperature = hourly.apparent_temperature?.filterHourlyValuesFrom(threshold),
            wind = hourly.wind?.filterHourlyWindFrom(threshold),
            gust = hourly.gust?.filterHourlyValuesFrom(threshold),
            humidity = hourly.humidity?.filterHourlyValuesFrom(threshold),
            cloudrate = hourly.cloudrate?.filterHourlyValuesFrom(threshold),
            skycon = hourly.skycon?.filterHourlySkyconFrom(threshold),
            pressure = hourly.pressure?.filterHourlyValuesFrom(threshold),
            visibility = hourly.visibility?.filterHourlyValuesFrom(threshold),
            dswrf = hourly.dswrf?.filterHourlyValuesFrom(threshold),
            air_quality = hourly.air_quality?.copy(
                aqi = hourly.air_quality.aqi?.filterHourlyAqiFrom(threshold),
                pm25 = hourly.air_quality.pm25?.filterHourlyValuesFrom(threshold)
            ),
            life_index = hourly.life_index?.copy(
                ultraviolet = hourly.life_index.ultraviolet?.filterHourlyUvFrom(threshold)
            )
        )
        return copy(result = result.copy(hourly = filteredHourly))
    }

    private fun WeatherResponse.currentHour(): OffsetDateTime {
        val offset = resultOffset()
        val epochSeconds = server_time ?: Instant.now().epochSecond
        return Instant.ofEpochSecond(epochSeconds)
            .atOffset(offset)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
    }

    private fun WeatherResponse.resultOffset(): ZoneOffset {
        return ZoneOffset.ofTotalSeconds(tzshift ?: 8 * 60 * 60)
    }

    private fun parseDateTime(value: String?): OffsetDateTime? {
        if (value == null) return null
        return try {
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (_: Exception) {
            null
        }
    }

    private fun List<HourlyValue>.filterHourlyValuesFrom(threshold: OffsetDateTime): List<HourlyValue> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }

    private fun List<HourlyWind>.filterHourlyWindFrom(threshold: OffsetDateTime): List<HourlyWind> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }

    private fun List<HourlySkycon>.filterHourlySkyconFrom(threshold: OffsetDateTime): List<HourlySkycon> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }

    private fun List<HourlyAqiValue>.filterHourlyAqiFrom(threshold: OffsetDateTime): List<HourlyAqiValue> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }

    private fun List<HourlyUvItem>.filterHourlyUvFrom(threshold: OffsetDateTime): List<HourlyUvItem> {
        return filter { item ->
            parseDateTime(item.datetime)?.let { !it.isBefore(threshold) } ?: true
        }
    }
}
