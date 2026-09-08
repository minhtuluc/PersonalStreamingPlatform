package com.drivestream.app.maintenance

import android.content.Context
import com.drivestream.app.data.AppDatabase
import com.drivestream.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HousekeepingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val PREFS_NAME = "drivestream_housekeeping"
        private const val KEY_LAST_CLEANUP = "last_cleanup_timestamp"

        const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        const val CACHE_EXPIRY_MS = 5L * 60L * 1000L
        const val MAX_HISTORY_ENTRIES = 200
    }

    suspend fun performDailyCleanup(force: Boolean = false) = withContext(ioDispatcher) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastRun = prefs.getLong(KEY_LAST_CLEANUP, 0L)

        if (!force && (now - lastRun < ONE_DAY_MS)) {
            Timber.d("[HOUSEKEEPING] Skipped. Already ran within the last 24 hours")
            return@withContext
        }

        cleanupExpiredCache(now)
        cleanupWatchHistory()
        cleanupCrashReports(now)
        cleanupOrphanDownloads()

        prefs.edit().putLong(KEY_LAST_CLEANUP, now).apply()
        Timber.i("[HOUSEKEEPING] Daily cleanup completed successfully")
    }

    private suspend fun cleanupExpiredCache(now: Long) {
        db.fileCacheDao().deleteExpired(now - CACHE_EXPIRY_MS)
    }

    private suspend fun cleanupWatchHistory() {
        db.watchHistoryDao().keepRecent(MAX_HISTORY_ENTRIES)
    }

    private fun cleanupCrashReports(now: Long) {
        val crashDir = File(context.filesDir, "crashes")
        if (crashDir.exists()) {
            val threshold = now - SEVEN_DAYS_MS
            crashDir.listFiles()?.filter { it.lastModified() < threshold }?.forEach { it.delete() }
        }
    }

    private suspend fun cleanupOrphanDownloads() {
        val completedList = db.downloadedVideoDao().getCompletedList()
        completedList.forEach { download ->
            val localFile = File(download.localPath)
            if (!localFile.exists()) {
                Timber.w("[HOUSEKEEPING] Orphan record found without file: %s", download.fileName)
                db.downloadedVideoDao().delete(download.fileId)
            }
        }
    }
}
