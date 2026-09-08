package com.drivestream.app.download

import android.content.Context
import android.os.Environment
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.data.DownloadStatus
import com.drivestream.app.data.DownloadedVideoDao
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.network.RateLimiter
import com.drivestream.app.network.RetryPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import com.drivestream.app.di.IoDispatcher
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val fileId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

@Suppress("TooManyFunctions")
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
    private val downloadedVideoDao: DownloadedVideoDao,
    private val rateLimiter: RateLimiter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val queueMutex = Mutex()

    private var activeJob: Job? = null
    private var activeFileId: String? = null

    private val _progressFlow = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progressFlow: StateFlow<Map<String, DownloadProgress>> = _progressFlow.asStateFlow()

    internal var endpointTemplate: String = DEFAULT_ENDPOINT_TEMPLATE

    companion object {
        const val DEFAULT_ENDPOINT_TEMPLATE = "https://www.googleapis.com/drive/v3/files/{fileId}?alt=media"
        const val STORAGE_BUFFER_BYTES = 100L * 1024L * 1024L // 100MB buffer
        private const val BUFFER_SIZE = 64 * 1024 // 64KB
        private const val PROGRESS_EMIT_INTERVAL_MS = 500L
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
    }

    fun getDownloadDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "videos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun enqueue(fileId: String, fileName: String, fileSize: Long) {
        queueMutex.withLock {
            val downloadDir = getDownloadDir()
            val targetFile = File(downloadDir, fileName)

            val existing = downloadedVideoDao.getById(fileId)
            if (existing == null) {
                val entity = DownloadedVideoEntity(
                    fileId = fileId,
                    fileName = fileName,
                    localPath = targetFile.absolutePath,
                    fileSize = fileSize,
                    downloadedBytes = 0L,
                    downloadedAt = System.currentTimeMillis(),
                    status = DownloadStatus.QUEUED
                )
                downloadedVideoDao.upsert(entity)
            } else if (existing.status != DownloadStatus.COMPLETED) {
                downloadedVideoDao.updateProgress(fileId, existing.downloadedBytes, DownloadStatus.QUEUED)
            }

            updateProgressMap(fileId, existing?.downloadedBytes ?: 0L, fileSize, DownloadStatus.QUEUED)
        }
        processNextInQueue()
    }

    suspend fun pause(fileId: String) {
        queueMutex.withLock {
            if (activeFileId == fileId) {
                activeJob?.cancel()
                activeJob = null
                activeFileId = null
            }
            val existing = downloadedVideoDao.getById(fileId)
            val currentBytes = existing?.downloadedBytes ?: 0L
            downloadedVideoDao.updateProgress(fileId, currentBytes, DownloadStatus.PAUSED)
            updateProgressMap(fileId, currentBytes, existing?.fileSize ?: 0L, DownloadStatus.PAUSED)
        }
        processNextInQueue()
    }

    suspend fun resume(fileId: String) {
        val existing = downloadedVideoDao.getById(fileId) ?: return
        enqueue(fileId, existing.fileName, existing.fileSize)
    }

    suspend fun cancel(fileId: String) {
        queueMutex.withLock {
            if (activeFileId == fileId) {
                activeJob?.cancel()
                activeJob = null
                activeFileId = null
            }
            val existing = downloadedVideoDao.getById(fileId)
            existing?.let {
                val partFile = File("${it.localPath}.part")
                if (partFile.exists()) partFile.delete()
                val targetFile = File(it.localPath)
                if (targetFile.exists()) targetFile.delete()
            }
            downloadedVideoDao.delete(fileId)
            _progressFlow.update { it - fileId }
        }
        processNextInQueue()
    }

    suspend fun delete(fileId: String) {
        cancel(fileId)
    }

    private fun processNextInQueue() {
        scope.launch {
            queueMutex.withLock {
                if (activeJob != null && activeJob?.isActive == true) {
                    return@withLock
                }

                val queuedList = downloadedVideoDao.getByStatus(DownloadStatus.QUEUED)
                val next = queuedList.firstOrNull() ?: return@withLock

                activeFileId = next.fileId
                activeJob = launch(ioDispatcher) {
                    executeDownload(next)
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeDownload(item: DownloadedVideoEntity) {
        val fileId = item.fileId
        val partFile = File("${item.localPath}.part")
        val targetFile = File(item.localPath)

        try {
            validateStorage(item, partFile)
            downloadedVideoDao.updateProgress(fileId, partFile.length(), DownloadStatus.DOWNLOADING)
            updateProgressMap(fileId, partFile.length(), item.fileSize, DownloadStatus.DOWNLOADING)

            downloadLoop(fileId, item.fileSize, partFile)

            if (!partFile.renameTo(targetFile)) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }

            downloadedVideoDao.updateProgress(fileId, item.fileSize, DownloadStatus.COMPLETED)
            updateProgressMap(fileId, item.fileSize, item.fileSize, DownloadStatus.COMPLETED)
        } catch (e: CancellationException) {
            handleCancellation(fileId, partFile, item.fileSize)
            throw e
        } catch (e: Exception) {
            handleDownloadError(fileId, partFile, item.fileSize, e)
        } finally {
            finishActiveTask(fileId)
        }
    }

    private fun validateStorage(item: DownloadedVideoEntity, partFile: File) {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val neededBytes = (item.fileSize - existingBytes).coerceAtLeast(0L)
        val freeSpace = getDownloadDir().usableSpace
        if (freeSpace < neededBytes + STORAGE_BUFFER_BYTES) {
            throw InsufficientStorageException(
                message = "Bộ nhớ không đủ để tải tệp",
                requiredBytes = neededBytes,
                availableBytes = freeSpace
            )
        }
    }

    private suspend fun downloadLoop(fileId: String, fileSize: Long, partFile: File) {
        var downloaded = if (partFile.exists()) partFile.length() else 0L
        var token = tokenManager.getValidAccessToken().getOrNull() ?: ""

        val response = fetchDownloadStream(fileId, downloaded, token)
        val validatedResponse = handleAuthRetry(response, fileId, downloaded)

        writeStreamToFile(validatedResponse, partFile, downloaded, fileSize, fileId)
    }

    private suspend fun handleAuthRetry(response: Response, fileId: String, downloaded: Long): Response {
        if (response.code != HTTP_UNAUTHORIZED) return response
        response.close()
        val refreshed = tokenManager.refreshAccessToken().getOrNull() ?: ""
        return fetchDownloadStream(fileId, downloaded, refreshed)
    }

    private suspend fun fetchDownloadStream(fileId: String, fromByte: Long, token: String): Response {
        rateLimiter.acquire()
        val url = endpointTemplate.replace("{fileId}", fileId)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")

        if (fromByte > 0L) {
            requestBuilder.header("Range", "bytes=$fromByte-")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (response.code != HTTP_OK && response.code != HTTP_PARTIAL_CONTENT && response.code != HTTP_UNAUTHORIZED) {
            val code = response.code
            response.close()
            throw DownloadFailedException("Server trả về mã HTTP không hợp lệ: $code")
        }
        return response
    }

    private suspend fun writeStreamToFile(
        response: Response,
        partFile: File,
        initialDownloaded: Long,
        totalSize: Long,
        fileId: String
    ) {
        var currentBytes = initialDownloaded
        var lastEmitTime = System.currentTimeMillis()
        val buffer = ByteArray(BUFFER_SIZE)

        response.use { res ->
            val body = res.body ?: throw DownloadFailedException("Empty response body")
            body.byteStream().use { input ->
                FileOutputStream(partFile, true).use { output ->
                    currentBytes = copyStream(input, output, buffer, currentBytes) { updatedBytes ->
                        val now = System.currentTimeMillis()
                        if (now - lastEmitTime >= PROGRESS_EMIT_INTERVAL_MS) {
                            lastEmitTime = now
                            downloadedVideoDao.updateProgress(fileId, updatedBytes, DownloadStatus.DOWNLOADING)
                            updateProgressMap(fileId, updatedBytes, totalSize, DownloadStatus.DOWNLOADING)
                        }
                    }
                }
            }
        }
    }

    private inline fun copyStream(
        input: InputStream,
        output: FileOutputStream,
        buffer: ByteArray,
        startBytes: Long,
        onProgress: (Long) -> Unit
    ): Long {
        var bytes = startBytes
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            bytes += read
            onProgress(bytes)
        }
        output.flush()
        return bytes
    }

    private suspend fun handleCancellation(fileId: String, partFile: File, totalSize: Long) {
        val bytes = if (partFile.exists()) partFile.length() else 0L
        downloadedVideoDao.updateProgress(fileId, bytes, DownloadStatus.PAUSED)
        updateProgressMap(fileId, bytes, totalSize, DownloadStatus.PAUSED)
    }

    private suspend fun handleDownloadError(fileId: String, partFile: File, totalSize: Long, e: Exception) {
        Timber.e(e, "Download failed for fileId: %s", fileId)
        val bytes = if (partFile.exists()) partFile.length() else 0L
        downloadedVideoDao.updateProgress(fileId, bytes, DownloadStatus.FAILED)
        updateProgressMap(fileId, bytes, totalSize, DownloadStatus.FAILED, e.message)
    }

    private fun finishActiveTask(fileId: String) {
        scope.launch {
            queueMutex.withLock {
                if (activeFileId == fileId) {
                    activeJob = null
                    activeFileId = null
                }
            }
            processNextInQueue()
        }
    }

    private fun updateProgressMap(
        fileId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        status: DownloadStatus,
        errorMessage: String? = null
    ) {
        _progressFlow.update { current ->
            current + (fileId to DownloadProgress(fileId, downloadedBytes, totalBytes, status, errorMessage))
        }
    }
}
