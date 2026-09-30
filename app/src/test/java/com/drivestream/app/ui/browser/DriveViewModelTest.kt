package com.drivestream.app.ui.browser

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.drivestream.app.AppError
import com.drivestream.app.data.DriveRepository
import com.drivestream.app.data.FavoritesRepository
import com.drivestream.app.data.SettingsRepository
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.DriveFileList
import com.drivestream.app.testing.FakeFavoriteDao
import com.drivestream.app.testing.FakeSharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val driveRepository: DriveRepository = mockk()
    private val settingsRepository = SettingsRepository(FakeSharedPreferences())
    private val favoritesRepository = FavoritesRepository(FakeFavoriteDao(), testDispatcher)

    private val sampleFiles = listOf(
        DriveFile(id = "f1", name = "Movies", mimeType = DriveFile.FOLDER_MIME_TYPE, isFolder = true),
        DriveFile(id = "v1", name = "Test.mp4", mimeType = "video/mp4", size = 5000L)
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("loads folder files on init and updates state to success")
    fun initLoadsFilesSuccessfully() = runTest(testDispatcher) {
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = null, forceRefresh = false)
        } returns Result.success(DriveFileList(files = sampleFiles, nextPageToken = "next1"))

        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "root", "folderName" to "My Drive"))
        val viewModel = DriveViewModel(
            driveRepository,
            savedStateHandle,
            settingsRepository,
            favoritesRepository
        )

        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.files).hasSize(2)
            assertThat(state.nextPageToken).isEqualTo("next1")
            assertThat(state.errorMessage).isNull()
        }
    }

    @Test
    @DisplayName("sets error message when file loading fails")
    fun initFailsWithError() = runTest(testDispatcher) {
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = null, forceRefresh = false)
        } returns Result.failure(AppError.NetworkUnavailable(java.io.IOException("Network error occurred")))

        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "root", "folderName" to "My Drive"))
        val viewModel = DriveViewModel(
            driveRepository,
            savedStateHandle,
            settingsRepository,
            favoritesRepository
        )

        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.files).isEmpty()
            assertThat(state.errorMessage).isNotNull()
        }
    }

    @Test
    @DisplayName("loadNextPage appends files to current state")
    fun loadNextPageAppendsFiles() = runTest(testDispatcher) {
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = null, forceRefresh = false)
        } returns Result.success(DriveFileList(files = sampleFiles, nextPageToken = "next1"))

        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "root"))
        val viewModel = DriveViewModel(
            driveRepository,
            savedStateHandle,
            settingsRepository,
            favoritesRepository
        )
        advanceUntilIdle()

        val moreFiles = listOf(
            DriveFile(id = "v2", name = "Test2.mp4", mimeType = "video/mp4", size = 8000L)
        )
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = "next1", forceRefresh = false)
        } returns Result.success(DriveFileList(files = moreFiles, nextPageToken = null))

        viewModel.loadNextPage()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.files).hasSize(3)
            assertThat(state.nextPageToken).isNull()
            assertThat(state.isLoadingMore).isFalse()
        }
    }

    @Test
    @DisplayName("search query executes debounced search and updates files")
    fun debouncedSearchSuccess() = runTest(testDispatcher) {
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = null, forceRefresh = false)
        } returns Result.success(DriveFileList(files = emptyList()))

        val searchResult = listOf(
            DriveFile(id = "s1", name = "Matrix.mkv", mimeType = "video/x-matroska", size = 7000L)
        )
        coEvery {
            driveRepository.searchVideos(query = "Matrix", pageToken = null)
        } returns Result.success(DriveFileList(files = searchResult))

        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "root"))
        val viewModel = DriveViewModel(
            driveRepository,
            savedStateHandle,
            settingsRepository,
            favoritesRepository
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Matrix")
        advanceTimeBy(DriveViewModel.SEARCH_DEBOUNCE_MS + 50L)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isSearching).isTrue()
            assertThat(state.files).hasSize(1)
            assertThat(state.files[0].name).isEqualTo("Matrix.mkv")
        }
    }

    @Test
    @DisplayName("clearSearch restores folder files")
    fun clearSearchRestoresFiles() = runTest(testDispatcher) {
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = null, forceRefresh = false)
        } returns Result.success(DriveFileList(files = sampleFiles))

        coEvery {
            driveRepository.searchVideos("Avatar")
        } returns Result.success(DriveFileList(files = emptyList()))

        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "root"))
        val viewModel = DriveViewModel(
            driveRepository,
            savedStateHandle,
            settingsRepository,
            favoritesRepository
        )
        advanceUntilIdle()

        viewModel.onSearchQueryChange("Avatar")
        advanceTimeBy(DriveViewModel.SEARCH_DEBOUNCE_MS + 50L)
        advanceUntilIdle()

        viewModel.clearSearch()
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isSearching).isFalse()
            assertThat(state.searchQuery).isEmpty()
            assertThat(state.files).hasSize(2)
        }
    }

    @Test
    @DisplayName("sorts files by name ascending and descending while keeping folders on top")
    fun sortFilesByName() = runTest(testDispatcher) {
        val testFiles = listOf(
            DriveFile(id = "v1", name = "Zootopia.mp4", mimeType = "video/mp4", size = 1000L),
            DriveFile(id = "f1", name = "B_Folder", mimeType = DriveFile.FOLDER_MIME_TYPE, isFolder = true),
            DriveFile(id = "v2", name = "Avatar.mkv", mimeType = "video/mp4", size = 2000L),
            DriveFile(id = "f2", name = "A_Folder", mimeType = DriveFile.FOLDER_MIME_TYPE, isFolder = true)
        )
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = null, forceRefresh = false)
        } returns Result.success(DriveFileList(files = testFiles))

        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "root"))
        val viewModel = DriveViewModel(
            driveRepository,
            savedStateHandle,
            settingsRepository,
            favoritesRepository
        )
        advanceUntilIdle()

        // Default NAME_ASC: Folders A_Folder, B_Folder, then Videos Avatar, Zootopia
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.files.map { it.name }).containsExactly(
                "A_Folder", "B_Folder", "Avatar.mkv", "Zootopia.mp4"
            ).inOrder()
        }

        // Switch to NAME_DESC: Folders B_Folder, A_Folder, then Videos Zootopia, Avatar
        viewModel.setSortOption(com.drivestream.app.data.model.FileSortOption.NAME_DESC)
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.sortOption).isEqualTo(com.drivestream.app.data.model.FileSortOption.NAME_DESC)
            assertThat(state.files.map { it.name }).containsExactly(
                "B_Folder", "A_Folder", "Zootopia.mp4", "Avatar.mkv"
            ).inOrder()
        }
    }

    @Test
    @DisplayName("sorts files by size descending and ascending")
    fun sortFilesBySize() = runTest(testDispatcher) {
        val testFiles = listOf(
            DriveFile(id = "v1", name = "Small.mp4", mimeType = "video/mp4", size = 100L),
            DriveFile(id = "f1", name = "Folder", mimeType = DriveFile.FOLDER_MIME_TYPE, isFolder = true),
            DriveFile(id = "v2", name = "Large.mp4", mimeType = "video/mp4", size = 5000L)
        )
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = null, forceRefresh = false)
        } returns Result.success(DriveFileList(files = testFiles))

        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "root"))
        val viewModel = DriveViewModel(
            driveRepository,
            savedStateHandle,
            settingsRepository,
            favoritesRepository
        )
        advanceUntilIdle()

        viewModel.setSortOption(com.drivestream.app.data.model.FileSortOption.SIZE_DESC)
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.files.map { it.name }).containsExactly(
                "Folder", "Large.mp4", "Small.mp4"
            ).inOrder()
        }

        viewModel.setSortOption(com.drivestream.app.data.model.FileSortOption.SIZE_ASC)
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.files.map { it.name }).containsExactly(
                "Folder", "Small.mp4", "Large.mp4"
            ).inOrder()
        }
    }

    @Test
    @DisplayName("preparePlaylist populates PlayerPlaylistManager with video files only")
    fun preparePlaylistPopulatesVideosOnly() = runTest(testDispatcher) {
        val playlistManager = com.drivestream.app.player.PlayerPlaylistManager()
        val testFiles = listOf(
            DriveFile(id = "f1", name = "Folder", mimeType = DriveFile.FOLDER_MIME_TYPE, isFolder = true),
            DriveFile(id = "v1", name = "Video1.mp4", mimeType = "video/mp4", size = 100L),
            DriveFile(id = "v2", name = "Video2.mp4", mimeType = "video/mp4", size = 200L)
        )
        coEvery {
            driveRepository.getFolderFiles(folderId = "root", pageToken = null, forceRefresh = false)
        } returns Result.success(DriveFileList(files = testFiles))

        val savedStateHandle = SavedStateHandle(mapOf("folderId" to "root"))
        val viewModel = DriveViewModel(
            driveRepository = driveRepository,
            savedStateHandle = savedStateHandle,
            settingsRepository = settingsRepository,
            favoritesRepository = favoritesRepository,
            playlistManager = playlistManager
        )
        advanceUntilIdle()

        viewModel.preparePlaylist()
        assertThat(playlistManager.getPlaylist()).hasSize(2)
        assertThat(playlistManager.getPlaylist().map { it.id }).containsExactly("v1", "v2").inOrder()
    }
    @Test
    fun sortsLargestFileFromLaterRemotePageAndRestoresItFromCache() = runTest(testDispatcher) {
        val remote = mockk<com.drivestream.app.network.DriveRemoteDataSource>()
        val cache = com.drivestream.app.data.DriveRepositoryTest.FakeFileCacheDao()
        val actualRepository = DriveRepository(remote, cache, kotlinx.serialization.json.Json, testDispatcher)
        val firstPage = (1..1000).map { DriveFile("v$it", "A$it.mp4", "video/mp4", size = it.toLong()) }
        val largest = DriveFile("largest", "Z-last.mp4", "video/mp4", size = 9_000_000_000L)
        coEvery { remote.listFiles("root", null, any()) } returns Result.success(DriveFileList(firstPage, "last"))
        coEvery { remote.listFiles("root", "last", any()) } returns Result.success(DriveFileList(listOf(largest)))
        settingsRepository.setSortOption(com.drivestream.app.data.model.FileSortOption.SIZE_DESC)
        val model = DriveViewModel(actualRepository, SavedStateHandle(), settingsRepository, favoritesRepository)
        advanceUntilIdle()
        assertThat(model.uiState.value.files.first()).isEqualTo(largest)
        assertThat(model.uiState.value.files).hasSize(1001)
        assertThat(model.uiState.value.nextPageToken).isNull()
        val reopened = DriveViewModel(actualRepository, SavedStateHandle(), settingsRepository, favoritesRepository)
        advanceUntilIdle()
        assertThat(reopened.uiState.value.files.first()).isEqualTo(largest)
        assertThat(reopened.uiState.value.files).hasSize(1001)
        coVerify(exactly = 1) { remote.listFiles("root", null, any()) }
    }

    @Test
    fun oldFolderRequestCannotOverwriteNewSearch() = runTest(testDispatcher) {
        val pending = CompletableDeferred<DriveFileList>()
        coEvery { driveRepository.getFolderFiles("root", null, false) } coAnswers {
            withContext(NonCancellable) { Result.success(pending.await()) }
        }
        val searchFile = DriveFile("search", "New.mp4", "video/mp4")
        coEvery { driveRepository.searchVideos("New", null) } returns Result.success(DriveFileList(listOf(searchFile)))
        val model = DriveViewModel(driveRepository, SavedStateHandle(), settingsRepository, favoritesRepository)
        runCurrent()
        model.onSearchQueryChange("New")
        advanceUntilIdle()
        pending.complete(DriveFileList(sampleFiles))
        advanceUntilIdle()
        assertThat(model.uiState.value.files).containsExactly(searchFile)
        assertThat(model.uiState.value.searchQuery).isEqualTo("New")
        assertThat(model.uiState.value.isSearching).isTrue()
    }

    @Test
    fun refreshDiscardsPendingOldPageAndBlocksAdditionalPagination() = runTest(testDispatcher) {
        coEvery { driveRepository.getFolderFiles("root", null, false) } returns
            Result.success(DriveFileList(sampleFiles, "next"))
        val pending = CompletableDeferred<DriveFileList>()
        coEvery { driveRepository.getFolderFiles("root", "next", false) } coAnswers {
            withContext(NonCancellable) { Result.success(pending.await()) }
        }
        val refreshed = CompletableDeferred<DriveFileList>()
        coEvery { driveRepository.getFolderFiles("root", null, true) } coAnswers { Result.success(refreshed.await()) }
        val model = DriveViewModel(driveRepository, SavedStateHandle(), settingsRepository, favoritesRepository)
        advanceUntilIdle()
        model.loadNextPage()
        model.loadNextPage()
        runCurrent()
        model.refresh()
        model.loadNextPage()
        runCurrent()
        assertThat(model.uiState.value.isRefreshing).isTrue()
        refreshed.complete(DriveFileList(sampleFiles))
        advanceUntilIdle()
        pending.complete(DriveFileList(listOf(DriveFile("obsolete", "Old.mp4", "video/mp4"))))
        advanceUntilIdle()
        assertThat(model.uiState.value.files).isEqualTo(sampleFiles)
        assertThat(model.uiState.value.isLoadingMore).isFalse()
        coVerify(exactly = 1) { driveRepository.getFolderFiles("root", "next", false) }
    }

    @Test
    fun sizeSortHasStableTiesAndDoesNotRequestNetworkAgain() = runTest(testDispatcher) {
        val files = listOf(
            DriveFile("z", "Z.mp4", "video/mp4", size = 100L),
            DriveFile("a", "A.mp4", "video/mp4", size = 100L),
            DriveFile("big", "Big.mp4", "video/mp4", size = 5_000_000_000L)
        )
        coEvery { driveRepository.getFolderFiles("root", null, false) } returns Result.success(DriveFileList(files))
        val model = DriveViewModel(driveRepository, SavedStateHandle(), settingsRepository, favoritesRepository)
        advanceUntilIdle()
        model.setSortOption(com.drivestream.app.data.model.FileSortOption.SIZE_DESC)
        assertThat(model.uiState.value.files.map { it.id }).containsExactly("big", "a", "z").inOrder()
        model.setSortOption(com.drivestream.app.data.model.FileSortOption.SIZE_ASC)
        assertThat(model.uiState.value.files.map { it.id }).containsExactly("a", "z", "big").inOrder()
        coVerify(exactly = 1) { driveRepository.getFolderFiles("root", null, false) }
    }

}
