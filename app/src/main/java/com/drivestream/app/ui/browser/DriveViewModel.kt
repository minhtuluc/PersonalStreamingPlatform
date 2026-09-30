package com.drivestream.app.ui.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivestream.app.data.AppSettings
import com.drivestream.app.data.BrowseViewMode
import com.drivestream.app.data.DownloadRepository
import com.drivestream.app.data.DriveRepository
import com.drivestream.app.data.FavoritesRepository
import com.drivestream.app.data.SettingsRepository
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.FileSortOption
import com.drivestream.app.player.PlayerPlaylistManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class DriveViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val downloadRepository: DownloadRepository? = null,
    private val playlistManager: PlayerPlaylistManager? = null
) : ViewModel() {

    private val folderId: String = savedStateHandle.get<String>("folderId") ?: DEFAULT_FOLDER_ID
    private val folderName: String = savedStateHandle.get<String>("folderName") ?: DEFAULT_FOLDER_NAME

    private var rawFiles: List<DriveFile> = emptyList()

    private val _uiState = MutableStateFlow(
        DriveUiState(
            folderId = folderId,
            folderName = folderName,
            isLoading = true,
            sortOption = settingsRepository.settings.value.sortOption,
            viewMode = settingsRepository.settings.value.viewMode
        )
    )
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    private var listingJob: Job? = null
    private var pageJob: Job? = null
    private var listingGeneration = 0L

    init {
        loadFolderFiles(forceRefresh = false)
        observeFavoriteIds()
    }

    private fun observeFavoriteIds() {
        viewModelScope.launch {
            favoritesRepository.favoriteIds().collect { ids ->
                _uiState.update { it.copy(favoriteIds = ids.toSet()) }
            }
        }
    }

    fun toggleFavorite(file: DriveFile) {
        viewModelScope.launch {
            favoritesRepository.toggle(file)
        }
    }

    fun loadFolderFiles(forceRefresh: Boolean = false) {
        startListing(query = "", forceRefresh = forceRefresh)
    }

    private fun startListing(query: String, forceRefresh: Boolean = false, debounce: Boolean = false) {
        listingJob?.cancel()
        pageJob?.cancel()
        val generation = ++listingGeneration
        val queryChanged = _uiState.value.searchQuery != query
        if (queryChanged) rawFiles = emptyList()
        _uiState.update {
            it.copy(
                files = if (queryChanged) emptyList() else it.files,
                searchQuery = query,
                isSearching = query.isNotBlank(),
                isLoading = !forceRefresh || it.files.isEmpty(),
                isRefreshing = forceRefresh,
                isLoadingMore = false,
                nextPageToken = null,
                errorMessage = null
            )
        }
        listingJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val result = if (query.isNotBlank()) {
                driveRepository.searchVideos(query = query)
            } else {
                driveRepository.getFolderFiles(folderId = folderId, forceRefresh = forceRefresh)
            }
            currentCoroutineContext().ensureActive()
            if (generation != listingGeneration) return@launch
            result.fold(
                onSuccess = { fileList ->
                    rawFiles = fileList.files.distinctBy { it.id }
                    _uiState.update {
                        it.copy(
                            files = sortFiles(rawFiles, it.sortOption),
                            nextPageToken = fileList.nextPageToken,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                    if (query.isBlank()) settingsRepository.setLastFolder(folderId, folderName)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.localizedMessage ?: "Failed to load files"
                        )
                    }
                }
            )
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        val token = currentState.nextPageToken
        if (token == null) return
        val isBusy = currentState.isLoadingMore || currentState.isLoading || currentState.isRefreshing
        if (isBusy) return

        val generation = listingGeneration
        _uiState.update { it.copy(isLoadingMore = true) }
        pageJob = viewModelScope.launch {
            val result = if (currentState.isSearching && currentState.searchQuery.isNotBlank()) {
                driveRepository.searchVideos(
                    query = currentState.searchQuery,
                    pageToken = token
                )
            } else {
                driveRepository.getFolderFiles(
                    folderId = folderId,
                    pageToken = token,
                    forceRefresh = false
                )
            }

            currentCoroutineContext().ensureActive()
            if (generation != listingGeneration) return@launch
            result.fold(
                onSuccess = { fileList ->
                    rawFiles = (rawFiles + fileList.files).distinctBy { it.id }
                    val sorted = sortFiles(rawFiles, _uiState.value.sortOption)
                    _uiState.update {
                        it.copy(
                            files = sorted,
                            nextPageToken = fileList.nextPageToken,
                            isLoadingMore = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            errorMessage = error.localizedMessage ?: "Failed to load more files"
                        )
                    }
                }
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        startListing(query = query, debounce = query.isNotBlank())
    }

    fun setSortOption(option: FileSortOption) {
        val sorted = sortFiles(rawFiles, option)
        _uiState.update {
            it.copy(
                files = sorted,
                sortOption = option
            )
        }
        settingsRepository.setSortOption(option)
    }

    fun toggleViewMode() {
        val nextMode = if (_uiState.value.viewMode == BrowseViewMode.GRID) {
            BrowseViewMode.LIST
        } else {
            BrowseViewMode.GRID
        }
        _uiState.update { it.copy(viewMode = nextMode) }
        settingsRepository.setViewMode(nextMode)
    }

    private fun sortFiles(files: List<DriveFile>, option: FileSortOption): List<DriveFile> {
        val (folders, videos) = files.partition { it.isFolder }
        val sortedFolders = when (option) {
            FileSortOption.NAME_ASC -> folders.sortedBy { it.name.lowercase(Locale.ROOT) }
            FileSortOption.NAME_DESC -> folders.sortedByDescending { it.name.lowercase(Locale.ROOT) }
            FileSortOption.DATE_DESC -> folders.sortedByDescending { it.modifiedAtEpochMs }
            FileSortOption.DATE_ASC -> folders.sortedBy { it.modifiedAtEpochMs }
            FileSortOption.SIZE_DESC -> folders.sortedBy { it.name.lowercase(Locale.ROOT) }
            FileSortOption.SIZE_ASC -> folders.sortedBy { it.name.lowercase(Locale.ROOT) }
        }
        val sortedVideos = when (option) {
            FileSortOption.NAME_ASC -> videos.sortedBy { it.name.lowercase(Locale.ROOT) }
            FileSortOption.NAME_DESC -> videos.sortedByDescending { it.name.lowercase(Locale.ROOT) }
            FileSortOption.DATE_DESC -> videos.sortedByDescending { it.modifiedAtEpochMs }
            FileSortOption.DATE_ASC -> videos.sortedBy { it.modifiedAtEpochMs }
            FileSortOption.SIZE_DESC -> videos.sortedWith(compareByDescending<DriveFile> { it.size }
                .thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
            FileSortOption.SIZE_ASC -> videos.sortedWith(compareBy<DriveFile> { it.size }
                .thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
        }
        return sortedFolders + sortedVideos
    }

    fun preparePlaylist() {
        val videoFiles = _uiState.value.files.filter { !it.isFolder }
        playlistManager?.setPlaylist(videoFiles)
    }

    fun refresh() {
        startListing(query = _uiState.value.searchQuery, forceRefresh = true)
    }

    fun clearSearch() {
        loadFolderFiles(forceRefresh = false)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun downloadVideo(file: DriveFile) {
        viewModelScope.launch {
            downloadRepository?.enqueueDownload(file.id, file.name, file.size)
        }
    }

    companion object {
        const val DEFAULT_FOLDER_ID = AppSettings.DEFAULT_FOLDER_ID
        const val DEFAULT_FOLDER_NAME = AppSettings.DEFAULT_FOLDER_NAME
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
