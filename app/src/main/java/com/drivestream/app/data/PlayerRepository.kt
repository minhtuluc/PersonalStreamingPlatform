package com.drivestream.app.data

import com.drivestream.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepository @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun savePlaybackPosition(
        fileId: String,
        fileName: String,
        positionMs: Long,
        durationMs: Long,
        thumbnailUrl: String? = null,
        resolution: String? = null,
        fileSize: Long = 0L
    ) = withContext(ioDispatcher) {
        val entity = WatchHistoryEntity(
            fileId = fileId,
            fileName = fileName,
            lastPosition = positionMs,
            duration = durationMs,
            lastWatched = System.currentTimeMillis(),
            thumbnailUrl = thumbnailUrl,
            resolution = resolution,
            fileSize = fileSize
        )
        watchHistoryDao.upsert(entity)
    }

    suspend fun getSavedPosition(fileId: String): Long = withContext(ioDispatcher) {
        val entry = watchHistoryDao.getByFileId(fileId) ?: return@withContext 0L
        val isValidResume = entry.lastPosition > MIN_RESUME_THRESHOLD_MS &&
            (entry.duration - entry.lastPosition) > MIN_REMAINING_THRESHOLD_MS
        return@withContext if (isValidResume) entry.lastPosition else 0L
    }

    fun getContinueWatching(): Flow<List<WatchHistoryEntity>> {
        return watchHistoryDao.getContinueWatching()
    }

    fun getRecentHistory(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<WatchHistoryEntity>> {
        return watchHistoryDao.getRecentHistory(limit)
    }

    suspend fun deleteHistory(fileId: String) = withContext(ioDispatcher) {
        watchHistoryDao.delete(fileId)
    }

    companion object {
        const val MIN_RESUME_THRESHOLD_MS = 5_000L // 5 seconds
        const val MIN_REMAINING_THRESHOLD_MS = 10_000L // 10 seconds before video end
        const val DEFAULT_RECENT_LIMIT = 20
    }
}
