package com.drivestream.app.maintenance

import android.content.Context
import android.content.SharedPreferences
import com.drivestream.app.data.AppDatabase
import com.drivestream.app.data.DownloadedVideoDao
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.data.DownloadStatus
import com.drivestream.app.data.FileCacheDao
import com.drivestream.app.data.WatchHistoryDao
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HousekeepingManagerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var fileCacheDao: FileCacheDao
    private lateinit var watchHistoryDao: WatchHistoryDao
    private lateinit var downloadedVideoDao: DownloadedVideoDao
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var housekeepingManager: HousekeepingManager

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        db = mockk(relaxed = true)
        fileCacheDao = mockk(relaxed = true)
        watchHistoryDao = mockk(relaxed = true)
        downloadedVideoDao = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { db.fileCacheDao() } returns fileCacheDao
        every { db.watchHistoryDao() } returns watchHistoryDao
        every { db.downloadedVideoDao() } returns downloadedVideoDao

        every { context.filesDir } returns tempDir
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor

        housekeepingManager = HousekeepingManager(
            context = context,
            db = db,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    @DisplayName("performDailyCleanup deletes expired cache and trims watch history")
    fun performDailyCleanupDeletesCacheAndHistory() = runTest(testDispatcher) {
        every { sharedPreferences.getLong(any(), 0L) } returns 0L

        housekeepingManager.performDailyCleanup(force = true)

        coVerify { fileCacheDao.deleteExpired(any()) }
        coVerify { watchHistoryDao.keepRecent(HousekeepingManager.MAX_HISTORY_ENTRIES) }
    }

    @Test
    @DisplayName("performDailyCleanup deletes crash logs older than 7 days")
    fun performDailyCleanupDeletesOldCrashLogs() = runTest(testDispatcher) {
        val crashDir = File(tempDir, "crashes")
        crashDir.mkdirs()

        val oldLog = File(crashDir, "crash_old.txt")
        oldLog.writeText("old crash")
        // Set timestamp to 10 days ago
        oldLog.setLastModified(System.currentTimeMillis() - (10L * 24 * 60 * 60 * 1000))

        val freshLog = File(crashDir, "crash_fresh.txt")
        freshLog.writeText("fresh crash")
        freshLog.setLastModified(System.currentTimeMillis())

        housekeepingManager.performDailyCleanup(force = true)

        assertThat(oldLog.exists()).isFalse()
        assertThat(freshLog.exists()).isTrue()
    }

    @Test
    @DisplayName("performDailyCleanup removes orphan downloaded video records when file is missing")
    fun performDailyCleanupRemovesOrphanDownloads() = runTest(testDispatcher) {
        val nonExistentPath = File(tempDir, "MissingVideo.mp4").absolutePath
        val orphanEntity = DownloadedVideoEntity(
            fileId = "orphan_1",
            fileName = "MissingVideo.mp4",
            localPath = nonExistentPath,
            fileSize = 1000L,
            downloadedBytes = 1000L,
            downloadedAt = 100L,
            status = DownloadStatus.COMPLETED
        )

        val existingFile = File(tempDir, "RealVideo.mp4")
        existingFile.writeText("data")
        val validEntity = DownloadedVideoEntity(
            fileId = "valid_1",
            fileName = "RealVideo.mp4",
            localPath = existingFile.absolutePath,
            fileSize = 4L,
            downloadedBytes = 4L,
            downloadedAt = 200L,
            status = DownloadStatus.COMPLETED
        )

        coEvery { downloadedVideoDao.getCompletedList() } returns listOf(orphanEntity, validEntity)

        housekeepingManager.performDailyCleanup(force = true)

        coVerify { downloadedVideoDao.delete("orphan_1") }
        coVerify(exactly = 0) { downloadedVideoDao.delete("valid_1") }
    }
}
