package com.drivestream.app.observability

import android.os.SystemClock
import timber.log.Timber

data class PerfStats(val p50: Long, val p90: Long, val p99: Long, val count: Int)

object PerfTracker {
    private val timings = mutableMapOf<String, MutableList<Long>>()

    private const val THRESHOLD_APP_STARTUP = 2000L
    private const val THRESHOLD_VIDEO_START = 3000L
    private const val THRESHOLD_FILE_LIST_LOAD = 1500L
    private const val THRESHOLD_DB_QUERY = 50L
    private const val THRESHOLD_TOKEN_REFRESH = 5000L
    private const val THRESHOLD_DEFAULT = 1000L

    private const val PERCENTILE_90 = 0.9
    private const val PERCENTILE_99 = 0.99

    @Synchronized
    fun <T> track(operation: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        val result = block()
        val elapsed = SystemClock.elapsedRealtime() - start

        timings.getOrPut(operation) { mutableListOf() }.add(elapsed)

        val threshold = getThresholdForOperation(operation)
        if (elapsed > threshold) {
            Timber.w("[PERF] %s took %dms (threshold: %dms)", operation, elapsed, threshold)
        } else {
            Timber.d("[PERF] %s: %dms", operation, elapsed)
        }

        return result
    }

    private fun getThresholdForOperation(operation: String): Long {
        return when (operation) {
            "app_startup" -> THRESHOLD_APP_STARTUP
            "video_start" -> THRESHOLD_VIDEO_START
            "file_list_load" -> THRESHOLD_FILE_LIST_LOAD
            "db_query" -> THRESHOLD_DB_QUERY
            "token_refresh" -> THRESHOLD_TOKEN_REFRESH
            else -> THRESHOLD_DEFAULT
        }
    }

    @Synchronized
    fun getStats(operation: String): PerfStats? {
        val data = timings[operation]?.sorted()?.takeIf { it.isNotEmpty() } ?: return null

        return PerfStats(
            p50 = data[data.size / 2],
            p90 = data[((data.size - 1) * PERCENTILE_90).toInt()],
            p99 = data[((data.size - 1) * PERCENTILE_99).toInt()],
            count = data.size
        )
    }

    @Synchronized
    fun clear() {
        timings.clear()
    }
}
