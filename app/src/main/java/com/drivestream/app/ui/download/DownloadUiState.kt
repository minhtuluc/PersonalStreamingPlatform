package com.drivestream.app.ui.download

import com.drivestream.app.data.DownloadStatus
import com.drivestream.app.download.DownloadFailure

data class DownloadUiItem(
    val fileId: String,
    val fileName: String,
    val localPath: String,
    val fileSize: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val downloadedAt: Long,
    val progressFraction: Float,
    val failure: DownloadFailure? = null
)

data class DownloadUiState(
    val items: List<DownloadUiItem> = emptyList(),
    val totalStorageUsedBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val isLoading: Boolean = true
)
