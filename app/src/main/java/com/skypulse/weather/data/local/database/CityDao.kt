package com.skypulse.weather.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Transaction
    suspend fun replaceAll(cities: List<CityEntity>) {
        deleteAll()
        upsertAll(cities)
    }

    @Transaction
    suspend fun deleteAndReorder(cityId: String) {
        delete(cityId)
        val remaining = getAll()
        val reordered = remaining.mapIndexed { index, entity ->
            entity.copy(sortOrder = index)
        }
        upsertAll(reordered)
    }
}
