package com.drivestream.app.ui.download

import com.drivestream.app.data.DownloadStatus

data class DownloadUiItem(
    val fileId: String,
    val fileName: String,
    val localPath: String,
    val fileSize: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val downloadedAt: Long,
    val progressFraction: Float
)

data class DownloadUiState(
    val items: List<DownloadUiItem> = emptyList(),
    val totalStorageUsedBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val isLoading: Boolean = true
)
