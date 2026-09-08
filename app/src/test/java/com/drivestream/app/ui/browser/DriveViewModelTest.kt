package com.drivestream.app.ui.browser

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.drivestream.app.AppError
import com.drivestream.app.data.DriveRepository
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.DriveFileList
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
        val viewModel = DriveViewModel(driveRepository, savedStateHandle)

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
        val viewModel = DriveViewModel(driveRepository, savedStateHandle)

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
        val viewModel = DriveViewModel(driveRepository, savedStateHandle)
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
        val viewModel = DriveViewModel(driveRepository, savedStateHandle)
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
        val viewModel = DriveViewModel(driveRepository, savedStateHandle)
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
}
