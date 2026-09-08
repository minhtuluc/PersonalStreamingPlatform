package com.drivestream.app.ui.home

import app.cash.turbine.test
import com.drivestream.app.auth.GoogleAuthManager
import com.drivestream.app.data.DownloadStatus
import com.drivestream.app.data.DownloadedVideoDao
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.data.WatchHistoryDao
import com.drivestream.app.data.WatchHistoryEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var watchHistoryDao: WatchHistoryDao
    private lateinit var downloadedVideoDao: DownloadedVideoDao
    private lateinit var googleAuthManager: GoogleAuthManager
    private lateinit var viewModel: HomeViewModel

    private val sampleWatchHistory = WatchHistoryEntity(
        fileId = "w1",
        fileName = "ContinueVideo.mp4",
        lastPosition = 50_000L,
        duration = 120_000L,
        lastWatched = 1000L,
        thumbnailUrl = null,
        resolution = "1080p",
        fileSize = 5000L
    )

    private val sampleDownload = DownloadedVideoEntity(
        fileId = "d1",
        fileName = "OfflineVideo.mp4",
        localPath = "/data/OfflineVideo.mp4",
        fileSize = 10_000L,
        downloadedBytes = 10_000L,
        downloadedAt = 2000L,
        status = DownloadStatus.COMPLETED
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        watchHistoryDao = mockk(relaxed = true)
        downloadedVideoDao = mockk(relaxed = true)
        googleAuthManager = mockk(relaxed = true)

        every { watchHistoryDao.getContinueWatching() } returns flowOf(listOf(sampleWatchHistory))
        every { watchHistoryDao.getRecentHistory(any()) } returns flowOf(listOf(sampleWatchHistory))
        every { downloadedVideoDao.getCompletedDownloads() } returns flowOf(listOf(sampleDownload))

        viewModel = HomeViewModel(
            watchHistoryDao = watchHistoryDao,
            downloadedVideoDao = downloadedVideoDao,
            googleAuthManager = googleAuthManager
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("uiState emits combined continue watching, recent history, and downloaded videos")
    fun uiStateEmitsCombinedData() = runTest(testDispatcher) {
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.continueWatching).hasSize(1)
            assertThat(state.continueWatching.first().fileId).isEqualTo("w1")
            assertThat(state.downloadedVideos).hasSize(1)
            assertThat(state.downloadedVideos.first().fileId).isEqualTo("d1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("signOut delegates to googleAuthManager")
    fun signOutDelegation() = runTest(testDispatcher) {
        coEvery { googleAuthManager.signOut() } returns Result.success(Unit)

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { googleAuthManager.signOut() }
    }
}
