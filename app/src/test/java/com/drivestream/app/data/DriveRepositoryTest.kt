package com.drivestream.app.data

import com.drivestream.app.AppError
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.DriveFileList
import com.drivestream.app.network.DriveRemoteDataSource
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DriveRepositoryTest {

    private lateinit var mockRemoteDataSource: DriveRemoteDataSource
    private lateinit var fakeFileCacheDao: FakeFileCacheDao
    private lateinit var json: Json
    private lateinit var repository: DriveRepository

    class FakeFileCacheDao : FileCacheDao {
        val storage = mutableMapOf<String, FileCacheEntity>()

        override suspend fun getCache(folderId: String): FileCacheEntity? = storage[folderId]

        override suspend fun saveCache(cache: FileCacheEntity) {
            storage[cache.folderId] = cache
        }

        override suspend fun invalidateFolder(folderId: String) {
            storage.remove(folderId)
        }

        override suspend fun deleteExpired(expiryTimestamp: Long) {
            storage.values.removeAll { it.cachedAt < expiryTimestamp }
        }

        override suspend fun clearAll() {
            storage.clear()
        }
    }

    private val sampleFiles = listOf(
        DriveFile(id = "folder_1", name = "Series", mimeType = DriveFile.FOLDER_MIME_TYPE, isFolder = true),
        DriveFile(id = "video_1", name = "Ep1.mp4", mimeType = "video/mp4", size = 1000L)
    )

    @BeforeEach
    fun setUp() {
        mockRemoteDataSource = mockk()
        fakeFileCacheDao = FakeFileCacheDao()
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        repository = DriveRepository(
            remoteDataSource = mockRemoteDataSource,
            fileCacheDao = fakeFileCacheDao,
            json = json,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    @DisplayName("getFolderFiles returns fresh cached data without remote network call")
    fun getFolderFilesFreshCacheHit() = runTest {
        val cachedJson = json.encodeToString(sampleFiles)
        fakeFileCacheDao.saveCache(
            FileCacheEntity(
                folderId = "root",
                filesJson = cachedJson,
                cachedAt = System.currentTimeMillis() // freshly cached
            )
        )

        val result = repository.getFolderFiles(folderId = "root", forceRefresh = false)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().files).hasSize(2)
        assertThat(result.getOrThrow().files[0].id).isEqualTo("folder_1")

        coVerify(exactly = 0) { mockRemoteDataSource.listFiles(any(), any(), any()) }
    }

    @Test
    @DisplayName("getFolderFiles calls remote and updates cache when cache is empty")
    fun getFolderFilesCacheMiss() = runTest {
        coEvery {
            mockRemoteDataSource.listFiles("root", null, any())
        } returns Result.success(DriveFileList(files = sampleFiles, nextPageToken = "token_next"))

        val result = repository.getFolderFiles(folderId = "root", forceRefresh = false)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().files).hasSize(2)

        // Cache was updated
        val cached = fakeFileCacheDao.getCache("root")
        assertThat(cached).isNotNull()
        assertThat(cached!!.filesJson).contains("Ep1.mp4")

        coVerify(exactly = 1) { mockRemoteDataSource.listFiles("root", null, any()) }
    }

    @Test
    @DisplayName("getFolderFiles bypasses fresh cache when forceRefresh is true")
    fun getFolderFilesForceRefresh() = runTest {
        val cachedJson = json.encodeToString(sampleFiles)
        fakeFileCacheDao.saveCache(
            FileCacheEntity(folderId = "root", filesJson = cachedJson, cachedAt = System.currentTimeMillis())
        )

        val updatedFiles = listOf(
            DriveFile(id = "video_new", name = "Ep2.mp4", mimeType = "video/mp4", size = 2000L)
        )
        coEvery {
            mockRemoteDataSource.listFiles("root", null, any())
        } returns Result.success(DriveFileList(files = updatedFiles))

        val result = repository.getFolderFiles(folderId = "root", forceRefresh = true)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().files[0].id).isEqualTo("video_new")

        coVerify(exactly = 1) { mockRemoteDataSource.listFiles("root", null, any()) }
    }

    @Test
    @DisplayName("getFolderFiles falls back to stale cache on network failure")
    fun getFolderFilesOfflineFallback() = runTest {
        val staleJson = json.encodeToString(sampleFiles)
        fakeFileCacheDao.saveCache(
            FileCacheEntity(
                folderId = "root",
                filesJson = staleJson,
                cachedAt = System.currentTimeMillis() - 1000_000L // expired
            )
        )

        coEvery {
            mockRemoteDataSource.listFiles("root", null, any())
        } returns Result.failure(AppError.NetworkUnavailable(java.io.IOException("No internet connection")))

        val result = repository.getFolderFiles(folderId = "root", forceRefresh = false)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().files).hasSize(2)
    }

    @Test
    @DisplayName("findSubtitleForVideo returns null placeholder as specified in S2-07")
    fun findSubtitleReturnsNull() = runTest {
        val result = repository.findSubtitleForVideo("video_123")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isNull()
    }
}
