package com.drivestream.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val localPath: String,
    val fileSize: Long,            // bytes
    val downloadedBytes: Long,     // bytes downloaded so far
    val downloadedAt: Long,        // epoch millis
    val status: DownloadStatus
)

@Dao
interface DownloadedVideoDao {
    @Query("SELECT * FROM downloaded_videos ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE status = 'COMPLETED' ORDER BY downloadedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloaded_videos WHERE status = 'COMPLETED'")
    suspend fun getCompletedList(): List<DownloadedVideoEntity>

    @Query("SELECT * FROM downloaded_videos WHERE fileId = :fileId LIMIT 1")
    suspend fun getById(fileId: String): DownloadedVideoEntity?

    @Query("SELECT * FROM downloaded_videos WHERE status = :status")
    suspend fun getByStatus(status: DownloadStatus): List<DownloadedVideoEntity>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM downloaded_videos WHERE status = 'COMPLETED'")
    fun getTotalStorageUsed(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedVideoEntity)

    @Query("UPDATE downloaded_videos SET downloadedBytes = :bytes, status = :status WHERE fileId = :fileId")
    suspend fun updateProgress(fileId: String, bytes: Long, status: DownloadStatus)

    @Query("DELETE FROM downloaded_videos WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query("DELETE FROM downloaded_videos")
    suspend fun clearAll()
}
