package com.drivestream.app.download

import android.content.Context
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.data.DownloadStatus
import com.drivestream.app.data.DownloadedVideoDao
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.network.RateLimiter
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var tokenManager: TokenManager
    private lateinit var fakeDao: FakeDownloadedVideoDao
    private lateinit var rateLimiter: RateLimiter
    private lateinit var context: Context
    private lateinit var downloadManager: DownloadManager

    private val testDispatcher = StandardTestDispatcher()

    class FakeDownloadedVideoDao : DownloadedVideoDao {
        val map = mutableMapOf<String, DownloadedVideoEntity>()
        private val flow = MutableStateFlow<Map<String, DownloadedVideoEntity>>(emptyMap())

        private fun notifyChange() {
            flow.value = map.toMap()
        }

        override fun getAllDownloads(): Flow<List<DownloadedVideoEntity>> =
            flow.map { it.values.toList() }

        override fun getCompletedDownloads(): Flow<List<DownloadedVideoEntity>> =
            flow.map { it.values.filter { e -> e.status == DownloadStatus.COMPLETED } }

        override suspend fun getCompletedList(): List<DownloadedVideoEntity> =
            map.values.filter { it.status == DownloadStatus.COMPLETED }

        override suspend fun getById(fileId: String): DownloadedVideoEntity? = map[fileId]

        override suspend fun getByStatus(status: DownloadStatus): List<DownloadedVideoEntity> =
            map.values.filter { it.status == status }

        override fun getTotalStorageUsed(): Flow<Long> =
            flow.map { it.values.filter { e -> e.status == DownloadStatus.COMPLETED }.sumOf { e -> e.fileSize } }

        override suspend fun upsert(entity: DownloadedVideoEntity) {
            map[entity.fileId] = entity
            notifyChange()
        }

        override suspend fun updateProgress(fileId: String, bytes: Long, status: DownloadStatus) {
            val existing = map[fileId]
            if (existing != null) {
                map[fileId] = existing.copy(downloadedBytes = bytes, status = status)
                notifyChange()
            }
        }

        override suspend fun delete(fileId: String) {
            map.remove(fileId)
            notifyChange()
        }

        override suspend fun clearAll() {
            map.clear()
            notifyChange()
        }
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient = OkHttpClient()
        tokenManager = mockk()
        fakeDao = FakeDownloadedVideoDao()
        rateLimiter = RateLimiter()
        context = mockk()

        every { context.getExternalFilesDir(any()) } returns tempDir
        every { context.filesDir } returns tempDir
        coEvery { tokenManager.getValidAccessToken() } returns Result.success("valid_token_123")

        downloadManager = DownloadManager(
            context = context,
            okHttpClient = okHttpClient,
            tokenManager = tokenManager,
            downloadedVideoDao = fakeDao,
            rateLimiter = rateLimiter,
            ioDispatcher = testDispatcher
        )
        downloadManager.endpointTemplate = mockWebServer.url("/files/{fileId}?alt=media").toString()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    @DisplayName("enqueue starts download and completes with local file on success")
    fun startDownloadSuccess() = runTest(testDispatcher) {
        val payload = "video-binary-content-12345"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.length.toString())
                .setBody(payload)
        )

        downloadManager.enqueue("file_1", "Movie.mp4", payload.length.toLong())
        advanceUntilIdle()

        val record = fakeDao.getById("file_1")
        assertThat(record).isNotNull()
        assertThat(record!!.status).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(record.downloadedBytes).isEqualTo(payload.length.toLong())

        val targetFile = File(record.localPath)
        assertThat(targetFile.exists()).isTrue()
        assertThat(targetFile.readText()).isEqualTo(payload)

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer valid_token_123")
    }

    @Test
    @DisplayName("mid-stream 401 triggers token refresh and retries successfully")
    fun midStreamTokenRefresh() = runTest(testDispatcher) {
        // First request receives 401
        mockWebServer.enqueue(
            MockResponse().setResponseCode(401).setBody("Unauthorized")
        )
        // Second request after refresh receives 200
        val payload = "refreshed-stream-data"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.length.toString())
                .setBody(payload)
        )

        coEvery { tokenManager.refreshAccessToken() } returns Result.success("new_token_789")

        downloadManager.enqueue("file_2", "Video2.mp4", payload.length.toLong())
        advanceUntilIdle()

        val record = fakeDao.getById("file_2")
        assertThat(record?.status).isEqualTo(DownloadStatus.COMPLETED)

        assertThat(mockWebServer.requestCount).isEqualTo(2)
        val first = mockWebServer.takeRequest()
        val second = mockWebServer.takeRequest()
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer valid_token_123")
        assertThat(second.getHeader("Authorization")).isEqualTo("Bearer new_token_789")
    }

    @Test
    @DisplayName("pause preserves downloaded bytes and resume sends Range header")
    fun pauseAndResumeWithRange() = runTest(testDispatcher) {
        val chunk1 = "part-one-"
        val chunk2 = "part-two"

        // Emulate part 1 downloaded and paused
        val targetFile = File(tempDir, "PausedVideo.mp4")
        val partFile = File("${targetFile.absolutePath}.part")
        partFile.writeText(chunk1)

        val totalSize = (chunk1.length + chunk2.length).toLong()
        fakeDao.upsert(
            DownloadedVideoEntity(
                fileId = "file_3",
                fileName = "PausedVideo.mp4",
                localPath = targetFile.absolutePath,
                fileSize = totalSize,
                downloadedBytes = chunk1.length.toLong(),
                downloadedAt = System.currentTimeMillis(),
                status = DownloadStatus.PAUSED
            )
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes ${chunk1.length}-${totalSize - 1}/$totalSize")
                .setHeader("Content-Length", chunk2.length.toString())
                .setBody(chunk2)
        )

        downloadManager.resume("file_3")
        advanceUntilIdle()

        val record = fakeDao.getById("file_3")
        assertThat(record?.status).isEqualTo(DownloadStatus.COMPLETED)
        assertThat(record?.downloadedBytes).isEqualTo(totalSize)

        val recorded = mockWebServer.takeRequest()
        assertThat(recorded.getHeader("Range")).isEqualTo("bytes=${chunk1.length}-")

        assertThat(targetFile.exists()).isTrue()
        assertThat(targetFile.readText()).isEqualTo(chunk1 + chunk2)
    }

    @Test
    @DisplayName("cancel deletes partial file and removes DB record")
    fun cancelDeletesPartialFileAndDb() = runTest(testDispatcher) {
        val targetFile = File(tempDir, "ToCancel.mp4")
        val partFile = File("${targetFile.absolutePath}.part")
        partFile.writeText("some-partial-bytes")

        fakeDao.upsert(
            DownloadedVideoEntity(
                fileId = "file_4",
                fileName = "ToCancel.mp4",
                localPath = targetFile.absolutePath,
                fileSize = 1000L,
                downloadedBytes = 18L,
                downloadedAt = System.currentTimeMillis(),
                status = DownloadStatus.PAUSED
            )
        )

        downloadManager.cancel("file_4")
        advanceUntilIdle()

        assertThat(fakeDao.getById("file_4")).isNull()
        assertThat(partFile.exists()).isFalse()
    }
}
