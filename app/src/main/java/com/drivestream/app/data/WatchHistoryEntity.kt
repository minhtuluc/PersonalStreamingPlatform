package com.drivestream.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val lastPosition: Long,        // milliseconds
    val duration: Long,            // milliseconds
    val lastWatched: Long,         // epoch millis
    val thumbnailUrl: String?,
    val resolution: String?,       // e.g. "3840x2160"
    val fileSize: Long             // bytes
)

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatched DESC")
    fun getAllHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE lastPosition > 0 AND lastPosition < duration ORDER BY lastWatched DESC")
    fun getContinueWatching(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY lastWatched DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE fileId = :fileId LIMIT 1")
    suspend fun getByFileId(fileId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query(
        "DELETE FROM watch_history WHERE fileId NOT IN " +
            "(SELECT fileId FROM watch_history ORDER BY lastWatched DESC LIMIT :limit)"
    )
    suspend fun keepRecent(limit: Int = 200)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}
