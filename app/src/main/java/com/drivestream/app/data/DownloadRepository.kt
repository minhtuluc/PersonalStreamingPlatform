package com.drivestream.app.data

import android.content.Context
import android.content.Intent
import android.os.Build
import com.drivestream.app.download.DownloadManager
import com.drivestream.app.download.DownloadProgress
import com.drivestream.app.download.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadedVideoDao: DownloadedVideoDao,
    private val downloadManager: DownloadManager
) {
    val allDownloads: Flow<List<DownloadedVideoEntity>> = downloadedVideoDao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadedVideoEntity>> = downloadedVideoDao.getCompletedDownloads()
    val totalStorageUsed: Flow<Long> = downloadedVideoDao.getTotalStorageUsed()
    val activeProgressFlow: StateFlow<Map<String, DownloadProgress>> = downloadManager.progressFlow

    fun getAvailableStorageBytes(): Long {
        return downloadManager.getDownloadDir().usableSpace
    }

    suspend fun enqueueDownload(fileId: String, fileName: String, fileSize: Long) {
        downloadManager.enqueue(fileId, fileName, fileSize)
        startService()
    }

    suspend fun pauseDownload(fileId: String) {
        downloadManager.pause(fileId)
    }

    suspend fun resumeDownload(fileId: String) {
        downloadManager.resume(fileId)
        startService()
    }

    suspend fun cancelDownload(fileId: String) {
        downloadManager.cancel(fileId)
    }

    suspend fun deleteDownload(fileId: String) {
        downloadManager.delete(fileId)
    }

    suspend fun getCompletedDownload(fileId: String): DownloadedVideoEntity? {
        val entity = downloadedVideoDao.getById(fileId)
        if (entity != null && entity.status == DownloadStatus.COMPLETED) {
            val file = File(entity.localPath)
            if (file.exists() && file.length() > 0L) {
                return entity
            }
        }
        return null
    }

    private fun startService() {
        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
