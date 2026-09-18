package com.drivestream.app.ui.home

import com.drivestream.app.data.AppSettings
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.data.FavoriteEntity
import com.drivestream.app.data.WatchHistoryEntity

data class HomeUiState(
    val continueWatching: List<WatchHistoryEntity> = emptyList(),
    val recentlyViewed: List<WatchHistoryEntity> = emptyList(),
    val downloadedVideos: List<DownloadedVideoEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val browseStartFolderId: String = AppSettings.DEFAULT_FOLDER_ID,
    val browseStartFolderName: String = AppSettings.DEFAULT_FOLDER_NAME,
    val isLoading: Boolean = true
)
