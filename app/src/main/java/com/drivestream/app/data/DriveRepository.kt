package com.drivestream.app.data

import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.DriveFileList
import com.drivestream.app.di.IoDispatcher
import com.drivestream.app.network.DriveRemoteDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepository @Inject constructor(
    private val remoteDataSource: DriveRemoteDataSource,
    private val fileCacheDao: FileCacheDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun getFolderFiles(
        folderId: String,
        pageToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<DriveFileList> = withContext(ioDispatcher) {
        val isFirstPage = pageToken == null
        if (isFirstPage && !forceRefresh) {
            val cachedResult = getFreshCacheOrNull(folderId)
            if (cachedResult != null) {
                return@withContext Result.success(cachedResult)
            }
        }

        // A first-page request is a complete metadata snapshot. Sorting a partial
        // alphabetically paged list cannot identify the largest file in a folder.
        val remoteResult = if (isFirstPage) {
            collectAllPages { token -> remoteDataSource.listFiles(folderId = folderId, pageToken = token) }
        } else {
            remoteDataSource.listFiles(folderId = folderId, pageToken = pageToken)
        }
        if (remoteResult.isSuccess) {
            val fileList = remoteResult.getOrThrow()
            if (isFirstPage) {
                persistCache(folderId = folderId, files = fileList.files)
            }
            Result.success(fileList)
        } else {
            // Offline fallback: try stale cache on network error for first page
            if (isFirstPage) {
                val staleCache = getStaleCacheOrNull(folderId)
                if (staleCache != null) {
                    return@withContext Result.success(staleCache)
                }
            }
            remoteResult
        }
    }

    suspend fun searchVideos(
        query: String,
        pageToken: String? = null
    ): Result<DriveFileList> = withContext(ioDispatcher) {
        if (pageToken == null) {
            collectAllPages { token -> remoteDataSource.searchVideos(query = query, pageToken = token) }
        } else {
            remoteDataSource.searchVideos(query = query, pageToken = pageToken)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun collectAllPages(
        fetchPage: suspend (String?) -> Result<DriveFileList>
    ): Result<DriveFileList> {
        val files = linkedMapOf<String, DriveFile>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        do {
            currentCoroutineContext().ensureActive()
            val result = fetchPage(token)
            currentCoroutineContext().ensureActive()
            if (result.isFailure) return result
            val page = result.getOrThrow()
            page.files.forEach { files[it.id] = it }
            token = page.nextPageToken?.takeIf { it.isNotBlank() }
            if (token != null && !seenTokens.add(token)) {
                return Result.failure(IllegalStateException("Drive returned a repeated page token"))
            }
            // Empty/short pages may still have a continuation token.
        } while (token != null)
        return Result.success(DriveFileList(files = files.values.toList()))
    }

    suspend fun findSubtitleForVideo(fileId: String): Result<DriveFile?> = withContext(ioDispatcher) {
        // Clean zero-bloat placeholder as per S2-07 architecture contract
        if (fileId.isBlank()) return@withContext Result.success(null)
        Result.success(null)
    }

    suspend fun invalidateFolderCache(folderId: String) = withContext(ioDispatcher) {
        fileCacheDao.invalidateFolder(folderId)
    }

    suspend fun clearAllCache() = withContext(ioDispatcher) {
        fileCacheDao.clearAll()
    }

    private suspend fun getFreshCacheOrNull(folderId: String): DriveFileList? {
        val cached = fileCacheDao.getCache(folderId) ?: return null
        val now = System.currentTimeMillis()
        val isFresh = (now - cached.cachedAt) < CACHE_TTL_MS
        return if (isFresh) {
            decodeCachedFiles(cached.filesJson)
        } else {
            null
        }
    }

    private suspend fun getStaleCacheOrNull(folderId: String): DriveFileList? {
        val cached = fileCacheDao.getCache(folderId) ?: return null
        return decodeCachedFiles(cached.filesJson)
    }

    private fun decodeCachedFiles(jsonString: String): DriveFileList? {
        return try {
            // Old array caches only held page one. Reject them rather than
            // silently treating that partial listing as a complete folder.
            json.decodeFromString<DriveFileList>(jsonString)
                .takeIf { it.nextPageToken == null }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun persistCache(folderId: String, files: List<DriveFile>) {
        try {
            val jsonString = json.encodeToString(DriveFileList(files = files))
            val entity = FileCacheEntity(
                folderId = folderId,
                filesJson = jsonString,
                cachedAt = System.currentTimeMillis()
            )
            fileCacheDao.saveCache(entity)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Cache write failure shouldn't abort the operation
        }
    }

    companion object {
        const val CACHE_TTL_MS = 300_000L // 5 minutes
    }
}
