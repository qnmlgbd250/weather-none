package com.skypulse.weather.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {

    @Query("SELECT * FROM cities ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CityEntity>>

    @Query("SELECT * FROM cities ORDER BY sortOrder ASC")
    suspend fun getAll(): List<CityEntity>

    @Query("SELECT * FROM cities WHERE id = :cityId")
    suspend fun getById(cityId: String): CityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(city: CityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cities: List<CityEntity>)

    @Query("DELETE FROM cities WHERE id = :cityId")
    suspend fun delete(cityId: String)

    @Query("DELETE FROM cities")
    suspend fun deleteAll()
}
