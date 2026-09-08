package com.drivestream.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "file_cache")
data class FileCacheEntity(
    @PrimaryKey val folderId: String,
    val filesJson: String,         // JSON serialized list
    val cachedAt: Long             // epoch millis
)

@Dao
interface FileCacheDao {
    @Query("SELECT * FROM file_cache WHERE folderId = :folderId LIMIT 1")
    suspend fun getCache(folderId: String): FileCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCache(cache: FileCacheEntity)

    @Query("DELETE FROM file_cache WHERE folderId = :folderId")
    suspend fun invalidateFolder(folderId: String)

    @Query("DELETE FROM file_cache WHERE cachedAt < :expiryTimestamp")
    suspend fun deleteExpired(expiryTimestamp: Long)

    @Query("DELETE FROM file_cache")
    suspend fun clearAll()
}
