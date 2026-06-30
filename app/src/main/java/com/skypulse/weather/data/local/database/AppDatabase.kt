package com.skypulse.weather.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WeatherEntity::class, CityEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun cityDao(): CityDao

    companion object {
        /**
         * Migration(1, 2): 添加 cities 表。
         * 新用户：自动创建。
         * 老用户：空表，首次启动时从 SharedPreferences 迁移。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cities` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `longitude` REAL NOT NULL,
                        `latitude` REAL NOT NULL,
                        `isCurrentLocation` INTEGER NOT NULL DEFAULT 0,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )"""
                )
            }
        }
    }
}
