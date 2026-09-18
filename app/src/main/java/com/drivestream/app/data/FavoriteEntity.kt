package com.drivestream.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val isFolder: Boolean,
    val fileSize: Long,
    val thumbnailUrl: String?,
    val durationMs: Long?,
    val resolution: String?,
    val modifiedAtEpochMs: Long,
    val addedAt: Long
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT fileId FROM favorites")
    fun getFavoriteIds(): Flow<List<String>>

    @Query("SELECT * FROM favorites WHERE fileId = :fileId LIMIT 1")
    suspend fun getById(fileId: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
