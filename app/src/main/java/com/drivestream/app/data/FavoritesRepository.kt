package com.drivestream.app.data

import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    val favorites: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

    fun favoriteIds(): Flow<List<String>> = favoriteDao.getFavoriteIds()

    suspend fun toggle(file: DriveFile) = withContext(ioDispatcher) {
        val existing = favoriteDao.getById(file.id)
        if (existing == null) {
            favoriteDao.upsert(file.toFavoriteEntity())
        } else {
            favoriteDao.delete(file.id)
        }
    }

    suspend fun remove(fileId: String) = withContext(ioDispatcher) {
        favoriteDao.delete(fileId)
    }
}

private fun DriveFile.toFavoriteEntity(): FavoriteEntity = FavoriteEntity(
    fileId = id,
    fileName = name,
    isFolder = isFolder,
    fileSize = size,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    resolution = resolution?.label,
    modifiedAtEpochMs = modifiedAtEpochMs,
    addedAt = System.currentTimeMillis()
)
