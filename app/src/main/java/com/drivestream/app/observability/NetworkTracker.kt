package com.drivestream.app.observability

import timber.log.Timber
import java.time.LocalDate

object NetworkTracker {
    private var bytesDownloadedToday = 0L
    private var apiCallsToday = 0
    private var lastResetDate: LocalDate = LocalDate.now()

    private const val API_QUOTA_WARNING_THRESHOLD = 8_000_000

    @Synchronized
    fun recordApiCall() {
        resetIfNewDay()
        apiCallsToday++

        if (apiCallsToday > API_QUOTA_WARNING_THRESHOLD) {
            Timber.w("[QUOTA] API calls today: %d – approaching daily limit!", apiCallsToday)
        }
    }

    @Synchronized
    fun recordBytesDownloaded(bytes: Long) {
        resetIfNewDay()
        bytesDownloadedToday += bytes
    }

    @Synchronized
    fun getApiCallsToday(): Int = apiCallsToday

    @Synchronized
    fun getBytesDownloadedToday(): Long = bytesDownloadedToday

    private fun resetIfNewDay() {
        val today = LocalDate.now()
        if (today != lastResetDate) {
            Timber.i(
                "[QUOTA] Daily reset. Yesterday: %d calls, %d bytes downloaded",
                apiCallsToday,
                bytesDownloadedToday
            )
            apiCallsToday = 0
            bytesDownloadedToday = 0
            lastResetDate = today
        }
    }

    @Synchronized
    internal fun resetForTesting() {
        apiCallsToday = 0
        bytesDownloadedToday = 0
        lastResetDate = LocalDate.now()
    }
}
