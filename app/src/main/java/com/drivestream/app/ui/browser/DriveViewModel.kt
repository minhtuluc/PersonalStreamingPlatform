package com.drivestream.app.ui.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivestream.app.data.DownloadRepository
import com.drivestream.app.data.DriveRepository
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.FileSortOption
import com.drivestream.app.player.PlayerPlaylistManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriveViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    savedStateHandle: SavedStateHandle,
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
            isLoading = true
        )
    )
    val uiState: StateFlow<DriveUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadFolderFiles(forceRefresh = false)
    }

    fun loadFolderFiles(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !forceRefresh && it.files.isEmpty(),
                    isRefreshing = forceRefresh,
                    errorMessage = null
                )
            }

            val result = driveRepository.getFolderFiles(
                folderId = folderId,
                pageToken = null,
                forceRefresh = forceRefresh
            )

            result.fold(
                onSuccess = { fileList ->
                    rawFiles = fileList.files
                    val sorted = sortFiles(rawFiles, _uiState.value.sortOption)
                    _uiState.update {
                        it.copy(
                            files = sorted,
                            nextPageToken = fileList.nextPageToken,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
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
        if (token == null || currentState.isLoadingMore || currentState.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

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

            result.fold(
                onSuccess = { fileList ->
                    rawFiles = rawFiles + fileList.files
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
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false) }
            loadFolderFiles(forceRefresh = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            executeSearch(query)
        }
    }

    private suspend fun executeSearch(query: String) {
        _uiState.update { it.copy(isSearching = true, isLoading = true, errorMessage = null) }
        val result = driveRepository.searchVideos(query = query)
        result.fold(
            onSuccess = { fileList ->
                rawFiles = fileList.files
                val sorted = sortFiles(rawFiles, _uiState.value.sortOption)
                _uiState.update {
                    it.copy(
                        files = sorted,
                        nextPageToken = fileList.nextPageToken,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage ?: "Search failed"
                    )
                }
            }
        )
    }

    fun setSortOption(option: FileSortOption) {
        val sorted = sortFiles(rawFiles, option)
        _uiState.update {
            it.copy(
                files = sorted,
                sortOption = option
            )
        }
    }

    private fun sortFiles(files: List<DriveFile>, option: FileSortOption): List<DriveFile> {
        val (folders, videos) = files.partition { it.isFolder }
        val sortedFolders = when (option) {
            FileSortOption.NAME_ASC -> folders.sortedBy { it.name.lowercase() }
            FileSortOption.NAME_DESC -> folders.sortedByDescending { it.name.lowercase() }
            FileSortOption.DATE_DESC -> folders.sortedByDescending { it.modifiedAtEpochMs }
            FileSortOption.DATE_ASC -> folders.sortedBy { it.modifiedAtEpochMs }
            FileSortOption.SIZE_DESC -> folders.sortedByDescending { it.name.lowercase() }
            FileSortOption.SIZE_ASC -> folders.sortedBy { it.name.lowercase() }
        }
        val sortedVideos = when (option) {
            FileSortOption.NAME_ASC -> videos.sortedBy { it.name.lowercase() }
            FileSortOption.NAME_DESC -> videos.sortedByDescending { it.name.lowercase() }
            FileSortOption.DATE_DESC -> videos.sortedByDescending { it.modifiedAtEpochMs }
            FileSortOption.DATE_ASC -> videos.sortedBy { it.modifiedAtEpochMs }
            FileSortOption.SIZE_DESC -> videos.sortedByDescending { it.size }
            FileSortOption.SIZE_ASC -> videos.sortedBy { it.size }
        }
        return sortedFolders + sortedVideos
    }

    fun preparePlaylist() {
        val videoFiles = _uiState.value.files.filter { !it.isFolder }
        playlistManager?.setPlaylist(videoFiles)
    }

    fun refresh() {
        if (_uiState.value.isSearching && _uiState.value.searchQuery.isNotBlank()) {
            viewModelScope.launch {
                executeSearch(_uiState.value.searchQuery)
            }
        } else {
            loadFolderFiles(forceRefresh = true)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearching = false
            )
        }
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
        const val DEFAULT_FOLDER_ID = "root"
        const val DEFAULT_FOLDER_NAME = "My Drive"
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
