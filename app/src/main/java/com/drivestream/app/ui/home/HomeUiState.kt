package com.drivestream.app.ui.home

import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.data.WatchHistoryEntity

data class HomeUiState(
    val continueWatching: List<WatchHistoryEntity> = emptyList(),
    val recentlyViewed: List<WatchHistoryEntity> = emptyList(),
    val downloadedVideos: List<DownloadedVideoEntity> = emptyList(),
    val isLoading: Boolean = true
)
