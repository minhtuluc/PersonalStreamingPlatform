package com.drivestream.app.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivestream.app.data.DownloadRepository
import com.drivestream.app.data.DownloadStatus
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.download.DownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val availableStorageFlow = MutableStateFlow(downloadRepository.getAvailableStorageBytes())

    val uiState: StateFlow<DownloadUiState> = combine(
        downloadRepository.allDownloads,
        downloadRepository.totalStorageUsed,
        downloadRepository.activeProgressFlow,
        availableStorageFlow
    ) { downloads, totalUsed, activeProgressMap, availableStorage ->
        val uiItems = downloads.map { entity ->
            val activeProgress = activeProgressMap[entity.fileId]
            mapToUiItem(entity, activeProgress)
        }
        DownloadUiState(
            items = uiItems,
            totalStorageUsedBytes = totalUsed,
            availableStorageBytes = availableStorage,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DownloadUiState(
            availableStorageBytes = downloadRepository.getAvailableStorageBytes(),
            isLoading = true
        )
    )

    private fun mapToUiItem(entity: DownloadedVideoEntity, activeProgress: DownloadProgress?): DownloadUiItem {
        val currentBytes = activeProgress?.downloadedBytes ?: entity.downloadedBytes
        val currentStatus = activeProgress?.status ?: entity.status
        val fraction = if (entity.fileSize > 0L) {
            (currentBytes.toFloat() / entity.fileSize).coerceIn(0f, 1f)
        } else {
            0f
        }

        return DownloadUiItem(
            fileId = entity.fileId,
            fileName = entity.fileName,
            localPath = entity.localPath,
            fileSize = entity.fileSize,
            downloadedBytes = currentBytes,
            status = currentStatus,
            downloadedAt = entity.downloadedAt,
            progressFraction = fraction
        )
    }

    fun pauseDownload(fileId: String) {
        viewModelScope.launch {
            downloadRepository.pauseDownload(fileId)
            refreshStorage()
        }
    }

    fun resumeDownload(fileId: String) {
        viewModelScope.launch {
            downloadRepository.resumeDownload(fileId)
            refreshStorage()
        }
    }

    fun deleteDownload(fileId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(fileId)
            refreshStorage()
        }
    }

    fun refreshStorage() {
        availableStorageFlow.value = downloadRepository.getAvailableStorageBytes()
    }
}
