package com.drivestream.app

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    @Suppress("TooGenericExceptionCaught")
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val timestamp = dateFormat.format(Date())
            val crashReport = buildString {
                appendLine("=== DRIVESTREAM CRASH REPORT ===")
                appendLine("Timestamp: $timestamp")
                appendLine("Thread: ${thread.name} (id=${thread.id})")
                appendLine("Exception: ${throwable.javaClass.name}")
                appendLine("Message: ${throwable.message}")
                appendLine("--- Stacktrace ---")
                appendLine(throwable.stackTraceToString())
            }

            Timber.e(throwable, "[CRASH] Uncaught exception on thread ${thread.name}")

            val crashDir = File(context.filesDir, "crashes")
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }
            val crashFile = File(crashDir, "crash_$timestamp.txt")
            FileOutputStream(crashFile).use { fos ->
                fos.write(crashReport.toByteArray(StandardCharsets.UTF_8))
            }
        } catch (t: Throwable) {
            Timber.e(t, "[CRASH] Failed to record crash report")
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
