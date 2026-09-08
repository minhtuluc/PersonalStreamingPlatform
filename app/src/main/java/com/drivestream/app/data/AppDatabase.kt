package com.drivestream.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WatchHistoryEntity::class,
        FileCacheEntity::class,
        DownloadedVideoEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun downloadedVideoDao(): DownloadedVideoDao

    companion object {
        const val DATABASE_NAME = "drivestream.db"
    }
}
