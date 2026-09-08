package com.drivestream.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.drivestream.app.MainActivity
import com.drivestream.app.R
import com.drivestream.app.data.DownloadRepository
import com.drivestream.app.data.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var downloadManager: DownloadManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.drivestream.app.download.PAUSE"
        const val ACTION_RESUME = "com.drivestream.app.download.RESUME"
        const val ACTION_CANCEL = "com.drivestream.app.download.CANCEL"
        const val EXTRA_FILE_ID = "extra_file_id"
        private const val MAX_PROGRESS = 100
        private const val BYTES_PER_MB = 1048576.0
        private const val MB_PER_GB = 1024.0
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startInForeground(createInitialNotification())
        observeProgress()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val fileId = intent?.getStringExtra(EXTRA_FILE_ID)

        if (action != null && fileId != null) {
            handleNotificationAction(action, fileId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleNotificationAction(action: String, fileId: String) {
        serviceScope.launch(Dispatchers.IO) {
            when (action) {
                ACTION_PAUSE -> downloadRepository.pauseDownload(fileId)
                ACTION_RESUME -> downloadRepository.resumeDownload(fileId)
                ACTION_CANCEL -> downloadRepository.cancelDownload(fileId)
            }
        }
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeProgress() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            downloadManager.progressFlow.collectLatest { progressMap ->
                val active = progressMap.values.firstOrNull {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                }

                if (active != null) {
                    val notification = buildProgressNotification(active)
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_started))
            .setContentText(getString(R.string.download_queued))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildProgressNotification(progress: DownloadProgress): Notification {
        val percent = (progress.progressFraction * MAX_PROGRESS).toInt()
        val downloadedText = formatFileSize(progress.downloadedBytes)
        val totalText = formatFileSize(progress.totalBytes)
        val contentText = "$downloadedText / $totalText ($percent%)"

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_FILE_ID, progress.fileId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_FILE_ID, progress.fileId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.download_in_progress, progress.fileId))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(MAX_PROGRESS, percent, false)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_pause), pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.action_cancel), cancelIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes.toDouble() / BYTES_PER_MB
        return if (mb >= MB_PER_GB) {
            String.format(java.util.Locale.US, "%.1f GB", mb / MB_PER_GB)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        }
    }
}
