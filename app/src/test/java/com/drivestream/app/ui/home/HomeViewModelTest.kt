package com.drivestream.app.ui.home

import com.drivestream.app.auth.GoogleAuthManager
import com.drivestream.app.data.DownloadStatus
import com.drivestream.app.data.DownloadedVideoDao
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.data.FavoriteEntity
import com.drivestream.app.data.FavoritesRepository
import com.drivestream.app.data.SettingsRepository
import com.drivestream.app.data.WatchHistoryDao
import com.drivestream.app.data.WatchHistoryEntity
import com.drivestream.app.testing.FakeFavoriteDao
import com.drivestream.app.testing.FakeSharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    private val favoriteDao = FakeFavoriteDao()
    private val settingsRepository = SettingsRepository(FakeSharedPreferences())
    private val favoritesRepository = FavoritesRepository(favoriteDao, testDispatcher)

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

    private val sampleFavorite = FavoriteEntity(
        fileId = "fav1",
        fileName = "Favorite.mp4",
        isFolder = false,
        fileSize = 7000L,
        thumbnailUrl = null,
        durationMs = 60_000L,
        resolution = "1080p",
        modifiedAtEpochMs = 1500L,
        addedAt = 3000L
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
            googleAuthManager = googleAuthManager,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("uiState emits combined continue watching, recent history, and downloaded videos")
    fun uiStateEmitsCombinedData() = runTest(testDispatcher) {
        val state = viewModel.uiState.first { !it.isLoading }

        assertThat(state.continueWatching).hasSize(1)
        assertThat(state.continueWatching.first().fileId).isEqualTo("w1")
        assertThat(state.downloadedVideos).hasSize(1)
        assertThat(state.downloadedVideos.first().fileId).isEqualTo("d1")
    }

    @Test
    @DisplayName("signOut delegates to googleAuthManager")
    fun signOutDelegation() = runTest(testDispatcher) {
        coEvery { googleAuthManager.signOut() } returns Result.success(Unit)

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { googleAuthManager.signOut() }
    }

    @Test
    @DisplayName("favorites from the repository are exposed in the home state")
    fun favoritesAreExposed() = runTest(testDispatcher) {
        favoriteDao.upsert(sampleFavorite)

        val state = viewModel.uiState.first { it.favorites.isNotEmpty() }

        assertThat(state.favorites).hasSize(1)
        assertThat(state.favorites.first().fileId).isEqualTo("fav1")
    }

    @Test
    @DisplayName("removing a favorite clears it from the home state")
    fun removedFavoriteDisappears() = runTest(testDispatcher) {
        favoriteDao.upsert(sampleFavorite)
        viewModel.uiState.first { it.favorites.isNotEmpty() }

        favoriteDao.delete("fav1")

        val state = viewModel.uiState.first { it.favorites.isEmpty() && !it.isLoading }
        assertThat(state.favorites).isEmpty()
    }
}
