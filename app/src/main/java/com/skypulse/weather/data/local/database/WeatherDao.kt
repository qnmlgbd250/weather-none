package com.skypulse.weather.data.local.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Upsert
    suspend fun upsert(entity: WeatherEntity)

    @Query("DELETE FROM weather WHERE cityId = :cityId")
    suspend fun delete(cityId: String)

    @Query("SELECT * FROM weather WHERE cityId = :cityId")
    fun observeWeather(cityId: String): Flow<WeatherEntity?>

    @Query("SELECT * FROM weather WHERE cityId = :cityId")
    suspend fun getWeather(cityId: String): WeatherEntity?

    @Query("SELECT * FROM weather")
    suspend fun getAllWeather(): List<WeatherEntity>

    @Query("SELECT * FROM weather")
    fun observeAllWeather(): Flow<List<WeatherEntity>>
}
