# DriveStream – Operational Excellence Guide

> Version: 1.0
> Status: Approved
> Last Updated: 2026-09-08

---

## 1. Build & Release Pipeline

### 1.1 Build Variants

```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            // LeakCanary, verbose logging, StrictMode enabled
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // R8 full mode, no logging, crash reporting enabled
        }
    }
}
```

### 1.2 ProGuard/R8 Rules

```proguard
# proguard-rules.pro

# Google Drive API models
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.** { *; }

# Room entities
-keep class com.drivestream.app.data.**Entity { *; }

# Kotlin serialization
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }

# ExoPlayer
-keep class androidx.media3.** { *; }

# Prevent stripping of error messages for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

### 1.3 Build Verification Script

```bash
#!/bin/bash
# scripts/verify-build.sh
# Run this before any release or after significant changes

set -e

echo "=== DriveStream Build Verification ==="

echo "[1/7] Clean build..."
./gradlew clean

echo "[2/7] Debug build..."
./gradlew assembleDebug

echo "[3/7] Release build..."
./gradlew assembleRelease

echo "[4/7] Unit tests..."
./gradlew testDebugUnitTest

echo "[5/7] Lint check..."
./gradlew lintDebug

echo "[6/7] Static analysis..."
./gradlew detekt

echo "[7/7] APK size check..."
APK_SIZE=$(stat -f%z app/build/outputs/apk/release/app-release.apk 2>/dev/null || stat -c%s app/build/outputs/apk/release/app-release.apk)
APK_SIZE_MB=$((APK_SIZE / 1048576))
echo "APK size: ${APK_SIZE_MB}MB"
if [ "$APK_SIZE_MB" -gt 25 ]; then
    echo "⚠️ WARNING: APK size exceeds 25MB target!"
fi

echo ""
echo "=== ✅ All checks passed ==="
```

---

## 2. Error Handling & Resilience

### 2.1 Error Classification

```kotlin
// Centralized error taxonomy
sealed class AppError(
    val userMessage: String,
    val technicalMessage: String,
    val isRetryable: Boolean,
    val severity: Severity
) {
    // Auth errors
    data class TokenExpired(val cause: Throwable) : AppError(
        userMessage = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
        technicalMessage = "Access token expired and refresh failed",
        isRetryable = false,
        severity = Severity.HIGH
    )

    // Network errors
    data class NetworkUnavailable(val cause: Throwable) : AppError(
        userMessage = "Không có kết nối mạng. Vui lòng kiểm tra WiFi/4G.",
        technicalMessage = "Network unreachable",
        isRetryable = true,
        severity = Severity.MEDIUM
    )

    data class RateLimited(val retryAfterMs: Long) : AppError(
        userMessage = "Đang tải quá nhanh. Vui lòng đợi vài giây...",
        technicalMessage = "Google API rate limit exceeded",
        isRetryable = true,
        severity = Severity.LOW
    )

    data class ServerError(val statusCode: Int, val cause: Throwable) : AppError(
        userMessage = "Máy chủ Google đang bận. Thử lại sau.",
        technicalMessage = "Server error: HTTP $statusCode",
        isRetryable = true,
        severity = Severity.MEDIUM
    )

    data class FileNotFound(val fileId: String) : AppError(
        userMessage = "File không tồn tại hoặc đã bị xóa.",
        technicalMessage = "File not found: $fileId",
        isRetryable = false,
        severity = Severity.LOW
    )

    data class InsufficientStorage(val requiredBytes: Long) : AppError(
        userMessage = "Không đủ bộ nhớ để tải video. Cần thêm ${requiredBytes / 1_073_741_824}GB.",
        technicalMessage = "Insufficient storage for download",
        isRetryable = false,
        severity = Severity.MEDIUM
    )

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
}
```

### 2.2 Retry Strategy

```kotlin
// Centralized retry with exponential backoff
object RetryPolicy {
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1_000,
        maxDelayMs: Long = 32_000,
        shouldRetry: (Throwable) -> Boolean = { true },
        block: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null

        repeat(maxAttempts) { attempt ->
            try {
                return block(attempt)
            } catch (e: CancellationException) {
                throw e // Never retry cancellation
            } catch (e: Throwable) {
                lastException = e
                if (attempt < maxAttempts - 1 && shouldRetry(e)) {
                    Timber.w("Retry attempt ${attempt + 1}/$maxAttempts after ${currentDelay}ms")
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
                }
            }
        }
        throw lastException!!
    }
}

// Usage example:
val files = RetryPolicy.withRetry(
    maxAttempts = 3,
    shouldRetry = { it is IOException || (it is GoogleJsonResponseException && it.statusCode in listOf(429, 500, 503)) }
) { attempt ->
    Timber.d("Fetching files, attempt $attempt")
    driveService.files().list().execute()
}
```

### 2.3 Circuit Breaker for API Calls

```kotlin
// Prevent cascading failures when Google API is down
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 60_000
) {
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var state: State = State.CLOSED

    enum class State { CLOSED, OPEN, HALF_OPEN }

    suspend fun <T> execute(block: suspend () -> T): T {
        when (state) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeoutMs) {
                    state = State.HALF_OPEN
                } else {
                    throw CircuitBreakerOpenException("API circuit breaker is OPEN. Retry after ${resetTimeoutMs / 1000}s")
                }
            }
            else -> { /* proceed */ }
        }

        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private fun onSuccess() {
        failureCount = 0
        state = State.CLOSED
    }

    private fun onFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        if (failureCount >= failureThreshold) {
            state = State.OPEN
            Timber.e("Circuit breaker OPENED after $failureCount failures")
        }
    }
}
```

---

## 3. Logging & Observability

### 3.1 Structured Logging

```kotlin
// Log categories with consistent format
object AppLog {
    // Auth events
    fun authSignIn(email: String) =
        Timber.i("[AUTH] Sign-in successful for ${email.take(3)}***")

    fun authTokenRefresh(remainingSeconds: Long) =
        Timber.d("[AUTH] Token refresh triggered, was expiring in ${remainingSeconds}s")

    fun authTokenRefreshFailed(error: Throwable) =
        Timber.e(error, "[AUTH] Token refresh FAILED")

    // Drive API events
    fun driveListFiles(folderId: String, count: Int, cached: Boolean) =
        Timber.d("[DRIVE] Listed $count files in folder $folderId (cached=$cached)")

    fun driveApiError(endpoint: String, statusCode: Int, message: String) =
        Timber.w("[DRIVE] API error: $endpoint → HTTP $statusCode: $message")

    // Player events
    fun playerStart(fileId: String, resolution: String, sizeBytes: Long) =
        Timber.i("[PLAYER] Started: $fileId ($resolution, ${sizeBytes / 1_048_576}MB)")

    fun playerBuffer(bufferPercent: Int, bufferMs: Long) =
        Timber.d("[PLAYER] Buffer: $bufferPercent% (${bufferMs}ms)")

    fun playerError(fileId: String, error: Throwable) =
        Timber.e(error, "[PLAYER] Playback error for $fileId")

    fun playerSeek(fromMs: Long, toMs: Long) =
        Timber.d("[PLAYER] Seek: ${fromMs/1000}s → ${toMs/1000}s")

    // Download events
    fun downloadStart(fileId: String, fileName: String, sizeBytes: Long) =
        Timber.i("[DOWNLOAD] Started: $fileName (${sizeBytes / 1_048_576}MB)")

    fun downloadProgress(fileId: String, percent: Int, speedKBps: Long) =
        Timber.d("[DOWNLOAD] $fileId: $percent% (${speedKBps}KB/s)")

    fun downloadComplete(fileId: String, elapsedMs: Long) =
        Timber.i("[DOWNLOAD] Completed: $fileId in ${elapsedMs / 1000}s")

    fun downloadError(fileId: String, error: Throwable) =
        Timber.e(error, "[DOWNLOAD] Failed: $fileId")
}
```

### 3.2 StrictMode (Debug Only)

```kotlin
// Detect main-thread violations and resource leaks early
class DriveStreamApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeath()  // Crash on violation in debug
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
```

### 3.3 Analytics Events (Privacy-First)

```kotlin
// Lightweight analytics stored LOCALLY only (no cloud upload)
// Purpose: Self-diagnosis of app performance

@Entity(tableName = "app_metrics")
data class AppMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val event: String,           // e.g., "video_play", "download_complete"
    val durationMs: Long?,       // how long the action took
    val success: Boolean,
    val errorCode: String?,
    val timestamp: Long = System.currentTimeMillis()
)

// Auto-cleanup: delete metrics older than 30 days
@Query("DELETE FROM app_metrics WHERE timestamp < :cutoffTimestamp")
suspend fun deleteOldMetrics(cutoffTimestamp: Long)
```

---

## 4. Crash Handling & Recovery

### 4.1 Global Exception Handler

```kotlin
class CrashHandler : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Log crash to local file (for self-diagnosis)
            val crashLog = buildString {
                appendLine("=== CRASH REPORT ===")
                appendLine("Time: ${Instant.now()}")
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: ${throwable.javaClass.simpleName}")
                appendLine("Message: ${throwable.message}")
                appendLine("Stack trace:")
                appendLine(throwable.stackTraceToString())
            }

            // Write to crash log file
            val crashFile = File(
                context.filesDir,
                "crashes/crash_${System.currentTimeMillis()}.txt"
            )
            crashFile.parentFile?.mkdirs()
            crashFile.writeText(crashLog)

            // Save current playback position (prevent data loss)
            // This runs synchronously since app is crashing
            savePlaybackPositionSync()

        } catch (e: Exception) {
            // Can't do anything if crash handler itself crashes
        }

        // Delegate to default handler (shows system crash dialog)
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
```

### 4.2 Graceful Degradation

```kotlin
// Feature degradation matrix when issues occur

// Network Issues:
// ├── WiFi lost during streaming → Pause player, show "Reconnecting..."
// ├── WiFi lost during download → Pause download, auto-resume when back
// ├── WiFi lost while browsing → Show cached file list if available
// └── No network at app start → Show only downloaded videos + last cached lists

// Storage Issues:
// ├── Low storage during download → Pause, show "Free up X GB to continue"
// └── Storage full → Disable download button, show storage management

// API Issues:
// ├── Rate limited → Back off, show countdown timer
// ├── API quota exhausted → Show "Try again tomorrow" + offline content
// └── Google outage → Show cached content + offline videos only
```

---

## 5. Performance Monitoring

### 5.1 Runtime Performance Tracking

```kotlin
// Track critical path timings
object PerfTracker {
    private val timings = mutableMapOf<String, MutableList<Long>>()

    fun <T> track(operation: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        val result = block()
        val elapsed = SystemClock.elapsedRealtime() - start

        timings.getOrPut(operation) { mutableListOf() }.add(elapsed)

        // Log warning if operation is slow
        val threshold = when (operation) {
            "app_startup" -> 2000L
            "video_start" -> 3000L
            "file_list_load" -> 1500L
            "db_query" -> 50L
            "token_refresh" -> 5000L
            else -> 1000L
        }

        if (elapsed > threshold) {
            Timber.w("[PERF] $operation took ${elapsed}ms (threshold: ${threshold}ms)")
        } else {
            Timber.d("[PERF] $operation: ${elapsed}ms")
        }

        return result
    }

    // Get p50, p90, p99 for an operation
    fun getStats(operation: String): PerfStats? {
        val data = timings[operation]?.sorted() ?: return null
        return PerfStats(
            p50 = data[data.size / 2],
            p90 = data[(data.size * 0.9).toInt()],
            p99 = data[(data.size * 0.99).toInt()],
            count = data.size
        )
    }
}

data class PerfStats(val p50: Long, val p90: Long, val p99: Long, val count: Int)
```

### 5.2 Network Usage Tracking

```kotlin
// Track bandwidth consumption to stay within Google quotas
object NetworkTracker {
    private var bytesDownloadedToday = 0L
    private var apiCallsToday = 0
    private var lastResetDate = LocalDate.now()

    fun recordApiCall() {
        resetIfNewDay()
        apiCallsToday++

        // Warn if approaching daily limits
        if (apiCallsToday > 8_000_000) { // 80% of 10M quota
            Timber.w("[QUOTA] API calls today: $apiCallsToday – approaching daily limit!")
        }
    }

    fun recordBytesDownloaded(bytes: Long) {
        resetIfNewDay()
        bytesDownloadedToday += bytes
    }

    private fun resetIfNewDay() {
        if (LocalDate.now() != lastResetDate) {
            Timber.i("[QUOTA] Daily reset. Yesterday: $apiCallsToday calls, ${bytesDownloadedToday / 1_073_741_824}GB downloaded")
            apiCallsToday = 0
            bytesDownloadedToday = 0
            lastResetDate = LocalDate.now()
        }
    }
}
```

---

## 6. Configuration Management

### 6.1 Feature Flags

```kotlin
// Runtime-toggleable features (no server needed, just local config)
object FeatureFlags {
    // Video player
    const val ENABLE_PIP = true
    const val ENABLE_BACKGROUND_AUDIO = false        // P2 – future
    const val ENABLE_PLAYBACK_SPEED = true

    // Download
    const val MAX_CONCURRENT_DOWNLOADS = 1
    const val ENABLE_DOWNLOAD_QUEUE = false           // P2 – future

    // Subtitle (placeholder)
    const val ENABLE_SUBTITLES = false                // Future feature

    // Debug
    const val ENABLE_PERF_OVERLAY = false             // Show FPS/buffer stats on screen
    const val ENABLE_NETWORK_LOGGING = BuildConfig.DEBUG
}
```

### 6.2 Build Configuration

```kotlin
// gradle.properties
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.enableBuildConfigField=true

# Compose compiler metrics (enable for profiling)
# composeCompilerReports=true
```

---

## 7. Maintenance & Housekeeping

### 7.1 Automated Cleanup

```kotlin
// Scheduled cleanup tasks (run on app start, max once per day)
class HousekeepingManager @Inject constructor(
    private val db: AppDatabase,
    private val context: Context
) {
    suspend fun performDailyCleanup() {
        val today = System.currentTimeMillis()
        val thirtyDaysAgo = today - (30L * 24 * 60 * 60 * 1000)

        // 1. Clear expired file cache
        db.fileCacheDao().deleteExpired(today - (5 * 60 * 1000))

        // 2. Clear old watch history (keep last 200 entries)
        db.watchHistoryDao().keepRecent(200)

        // 3. Clear old app metrics
        db.appMetricsDao().deleteOldMetrics(thirtyDaysAgo)

        // 4. Delete crash logs older than 7 days
        val crashDir = File(context.filesDir, "crashes")
        val sevenDaysAgo = today - (7L * 24 * 60 * 60 * 1000)
        crashDir.listFiles()?.filter { it.lastModified() < sevenDaysAgo }?.forEach { it.delete() }

        // 5. Verify downloaded files still exist on disk
        val downloads = db.downloadedVideoDao().getCompleted()
        downloads.forEach { download ->
            if (!File(download.localPath).exists()) {
                Timber.w("[HOUSEKEEPING] Downloaded file missing: ${download.fileName}")
                db.downloadedVideoDao().delete(download.fileId)
            }
        }

        Timber.i("[HOUSEKEEPING] Daily cleanup completed")
    }
}
```

### 7.2 Dependency Update Policy

| Category | Update Frequency | Action |
|---|---|---|
| **Security patches** | Immediately | Apply within 24 hours |
| **Media3 / ExoPlayer** | Every minor release | Test playback across all formats |
| **Compose BOM** | Every stable release | Run UI tests, check recomposition metrics |
| **Google APIs** | Every minor release | Verify API compatibility, check deprecations |
| **Kotlin** | Every minor release | Check compatibility with KSP and Compose compiler |
| **Gradle AGP** | Every minor release | Verify build pipeline |

### 7.3 Version Strategy

```
Version format: MAJOR.MINOR.PATCH
Example: 1.0.0 → 1.0.1 → 1.1.0 → 2.0.0

MAJOR: Breaking changes (architecture overhaul, API migration)
MINOR: New features (subtitles, multi-download, etc.)
PATCH: Bug fixes, performance improvements

versionCode: auto-increment on each build
versionName: semantic version string
```
