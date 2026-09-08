package com.drivestream.app.data

import android.content.Context
import com.drivestream.app.download.DownloadManager
import com.drivestream.app.download.DownloadProgress
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DownloadRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var dao: DownloadedVideoDao
    private lateinit var downloadManager: DownloadManager
    private lateinit var repository: DownloadRepository

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        downloadManager = mockk(relaxed = true)

        every { downloadManager.getDownloadDir() } returns tempDir
        every { downloadManager.progressFlow } returns MutableStateFlow(emptyMap())

        repository = DownloadRepository(
            context = context,
            downloadedVideoDao = dao,
            downloadManager = downloadManager
        )
    }

    @Test
    @DisplayName("getCompletedDownload returns entity when file exists on disk")
    fun getCompletedDownloadWhenFileExists() = runTest {
        val testFile = File(tempDir, "Completed.mp4")
        testFile.writeText("test-video-bytes")

        val entity = DownloadedVideoEntity(
            fileId = "c1",
            fileName = "Completed.mp4",
            localPath = testFile.absolutePath,
            fileSize = 16L,
            downloadedBytes = 16L,
            downloadedAt = 1000L,
            status = DownloadStatus.COMPLETED
        )

        coEvery { dao.getById("c1") } returns entity

        val result = repository.getCompletedDownload("c1")
        assertThat(result).isNotNull()
        assertThat(result?.fileId).isEqualTo("c1")
    }

    @Test
    @DisplayName("getCompletedDownload returns null when file is missing from disk")
    fun getCompletedDownloadWhenFileMissing() = runTest {
        val nonExistentFile = File(tempDir, "Deleted.mp4")

        val entity = DownloadedVideoEntity(
            fileId = "c2",
            fileName = "Deleted.mp4",
            localPath = nonExistentFile.absolutePath,
            fileSize = 100L,
            downloadedBytes = 100L,
            downloadedAt = 1000L,
            status = DownloadStatus.COMPLETED
        )

        coEvery { dao.getById("c2") } returns entity

        val result = repository.getCompletedDownload("c2")
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("getCompletedDownload returns null when status is not COMPLETED")
    fun getCompletedDownloadWhenNotCompleted() = runTest {
        val testFile = File(tempDir, "Partial.mp4")
        testFile.writeText("partial")

        val entity = DownloadedVideoEntity(
            fileId = "c3",
            fileName = "Partial.mp4",
            localPath = testFile.absolutePath,
            fileSize = 100L,
            downloadedBytes = 7L,
            downloadedAt = 1000L,
            status = DownloadStatus.DOWNLOADING
        )

        coEvery { dao.getById("c3") } returns entity

        val result = repository.getCompletedDownload("c3")
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("enqueueDownload calls downloadManager and starts service")
    fun enqueueDownloadDelegation() = runTest {
        repository.enqueueDownload("v_abc", "Video.mp4", 5000L)

        coVerify { downloadManager.enqueue("v_abc", "Video.mp4", 5000L) }
        io.mockk.verify { context.startService(any()) }
    }

    @Test
    @DisplayName("pauseDownload and cancelDownload delegate to manager")
    fun pauseAndCancelDelegation() = runTest {
        repository.pauseDownload("v1")
        coVerify { downloadManager.pause("v1") }

        repository.cancelDownload("v1")
        coVerify { downloadManager.cancel("v1") }

        repository.deleteDownload("v1")
        coVerify { downloadManager.delete("v1") }
    }

    @Test
    @DisplayName("totalStorageUsed delegates to dao flow")
    fun totalStorageUsedDelegation() = runTest {
        every { dao.getTotalStorageUsed() } returns flowOf(1024L * 1024L * 500L)

        val total = repository.totalStorageUsed.first()
        assertThat(total).isEqualTo(1024L * 1024L * 500L)
    }
}
