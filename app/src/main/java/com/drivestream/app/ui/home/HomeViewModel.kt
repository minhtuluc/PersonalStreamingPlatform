package com.drivestream.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivestream.app.auth.GoogleAuthManager
import com.drivestream.app.data.AppSettings
import com.drivestream.app.data.DownloadedVideoDao
import com.drivestream.app.data.FavoritesRepository
import com.drivestream.app.data.SettingsRepository
import com.drivestream.app.data.WatchHistoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao,
    private val downloadedVideoDao: DownloadedVideoDao,
    private val googleAuthManager: GoogleAuthManager,
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        watchHistoryDao.getContinueWatching(),
        watchHistoryDao.getRecentHistory(RECENT_HISTORY_LIMIT),
        downloadedVideoDao.getCompletedDownloads(),
        settingsRepository.settings,
        favoritesRepository.favorites
    ) { continueWatching, recentlyViewed, downloadedVideos, settings, favorites ->
        HomeUiState(
            continueWatching = continueWatching,
            recentlyViewed = recentlyViewed,
            downloadedVideos = downloadedVideos,
            favorites = favorites.take(MAX_FAVORITES_DISPLAY),
            browseStartFolderId = if (settings.resumeLastFolder) {
                settings.lastFolderId
            } else {
                AppSettings.DEFAULT_FOLDER_ID
            },
            browseStartFolderName = if (settings.resumeLastFolder) {
                settings.lastFolderName
            } else {
                AppSettings.DEFAULT_FOLDER_NAME
            },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = HomeUiState(isLoading = true)
    )

    fun signOut() {
        viewModelScope.launch {
            googleAuthManager.signOut()
        }
    }

    companion object {
        private const val RECENT_HISTORY_LIMIT = 20
        private const val MAX_FAVORITES_DISPLAY = 10
        private const val SUBSCRIBE_TIMEOUT_MS = 5000L
    }
}
