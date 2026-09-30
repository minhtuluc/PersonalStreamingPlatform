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
        val cachedJson = json.encodeToString(DriveFileList(files = sampleFiles))
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
        } returns Result.success(DriveFileList(files = sampleFiles))

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
        val cachedJson = json.encodeToString(DriveFileList(files = sampleFiles))
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
        val staleJson = json.encodeToString(DriveFileList(files = sampleFiles))
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
    fun collectsEveryPageBeforeCachingAndReusesCompleteSnapshot() = runTest {
        val largest = DriveFile("largest", "Z-last.mp4", "video/mp4", size = 9_000_000_000L)
        coEvery { mockRemoteDataSource.listFiles("root", null, any()) } returns
            Result.success(DriveFileList(sampleFiles, "page2"))
        coEvery { mockRemoteDataSource.listFiles("root", "page2", any()) } returns
            Result.success(DriveFileList(emptyList(), "page3"))
        coEvery { mockRemoteDataSource.listFiles("root", "page3", any()) } returns
            Result.success(DriveFileList(listOf(sampleFiles.last(), largest)))

        val result = repository.getFolderFiles("root").getOrThrow()
        assertThat(result.files.map { it.id }).containsExactly("folder_1", "video_1", "largest").inOrder()
        assertThat(result.nextPageToken).isNull()
        assertThat(repository.getFolderFiles("root").getOrThrow()).isEqualTo(result)
        coVerify(exactly = 1) { mockRemoteDataSource.listFiles("root", null, any()) }
        coVerify(exactly = 1) { mockRemoteDataSource.listFiles("root", "page3", any()) }
    }

    @Test
    fun rejectsLegacyFirstPageCache() = runTest {
        fakeFileCacheDao.saveCache(
            FileCacheEntity("root", json.encodeToString(sampleFiles), System.currentTimeMillis())
        )
        val complete = sampleFiles + DriveFile("last", "Z.mp4", "video/mp4", size = 9999L)
        coEvery { mockRemoteDataSource.listFiles("root", null, any()) } returns Result.success(DriveFileList(complete))

        assertThat(repository.getFolderFiles("root").getOrThrow().files).isEqualTo(complete)
        coVerify(exactly = 1) { mockRemoteDataSource.listFiles("root", null, any()) }
    }

    @Test
    fun laterPageFailureDoesNotCachePartialFolder() = runTest {
        coEvery { mockRemoteDataSource.listFiles("root", null, any()) } returns
            Result.success(DriveFileList(sampleFiles, "page2"))
        coEvery { mockRemoteDataSource.listFiles("root", "page2", any()) } returns
            Result.failure(java.io.IOException("Disconnected"))

        assertThat(repository.getFolderFiles("root").isFailure).isTrue()
        assertThat(fakeFileCacheDao.getCache("root")).isNull()
    }

    @Test
    fun failedRefreshKeepsPreviousCompleteCache() = runTest {
        val oldCache = FileCacheEntity("root", json.encodeToString(DriveFileList(sampleFiles)), 1L)
        fakeFileCacheDao.saveCache(oldCache)
        coEvery { mockRemoteDataSource.listFiles("root", null, any()) } returns
            Result.success(DriveFileList(emptyList(), "page2"))
        coEvery { mockRemoteDataSource.listFiles("root", "page2", any()) } returns
            Result.failure(java.io.IOException("Disconnected"))

        assertThat(repository.getFolderFiles("root", forceRefresh = true).getOrThrow().files).isEqualTo(sampleFiles)
        assertThat(fakeFileCacheDao.getCache("root")).isEqualTo(oldCache)
    }

    @Test
    fun repeatedPageTokenFailsInsteadOfLoopingOrCachingPartialData() = runTest {
        coEvery { mockRemoteDataSource.listFiles("root", any(), any()) } returns
            Result.success(DriveFileList(sampleFiles, "repeated"))

        assertThat(repository.getFolderFiles("root").isFailure).isTrue()
        assertThat(fakeFileCacheDao.getCache("root")).isNull()
        coVerify(exactly = 2) { mockRemoteDataSource.listFiles("root", any(), any()) }
    }

    @Test
    fun searchCollectsAllPagesEvenWhenFirstPageIsEmpty() = runTest {
        coEvery { mockRemoteDataSource.searchVideos("movie", null, any()) } returns
            Result.success(DriveFileList(emptyList(), "page2"))
        coEvery { mockRemoteDataSource.searchVideos("movie", "page2", any()) } returns
            Result.success(DriveFileList(sampleFiles))

        val result = repository.searchVideos("movie").getOrThrow()
        assertThat(result.files).isEqualTo(sampleFiles)
        assertThat(result.nextPageToken).isNull()
    }

    @Test
    @DisplayName("findSubtitleForVideo returns null placeholder as specified in S2-07")
    fun findSubtitleReturnsNull() = runTest {
        val result = repository.findSubtitleForVideo("video_123")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isNull()
    }
}
