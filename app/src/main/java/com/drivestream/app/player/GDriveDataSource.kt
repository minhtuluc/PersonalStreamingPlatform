package com.drivestream.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.network.RateLimiter
import com.drivestream.app.network.RateLimitException
import com.drivestream.app.network.RetryPolicy
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

@Suppress("TooManyFunctions")
class GDriveDataSource(
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
    private val rateLimiter: RateLimiter,
    internal var endpointTemplate: String = DEFAULT_DRIVE_DOWNLOAD_ENDPOINT
) : BaseDataSource(true) {

    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseBody: ResponseBody? = null
    private var inputStream: InputStream? = null
    private var currentCall: Call? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.bytesRemaining = 0L
        transferInitializing(dataSpec)

        val fileId = extractFileId(dataSpec.uri)
        if (fileId.isBlank()) {
            throw HttpDataSource.HttpDataSourceException(
                "Invalid Google Drive file ID: ${dataSpec.uri}",
                dataSpec,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN
            )
        }

        val openResult = executeOpenWithRetry(dataSpec, fileId)
        this.response = openResult.response
        this.responseBody = openResult.response.body
        this.inputStream = openResult.response.body?.byteStream()
        this.bytesRemaining = openResult.bytesToRead
        this.opened = true

        transferStarted(dataSpec)
        return this.bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val inStream = inputStream ?: throw IOException("Input stream closed")
        return readFromStream(inStream, buffer, offset, length)
    }

    private fun readFromStream(inStream: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val bytesRead = try {
            inStream.read(buffer, offset, bytesToRead)
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException(
                e,
                dataSpec!!,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_READ
            )
        }

        return handleBytesRead(bytesRead)
    }

    private fun handleBytesRead(bytesRead: Int): Int {
        if (bytesRead == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0L) {
                throw HttpDataSource.HttpDataSourceException(
                    EOFException(),
                    dataSpec!!,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    HttpDataSource.HttpDataSourceException.TYPE_READ
                )
            }
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            currentCall?.cancel()
            currentCall = null

            try {
                inputStream?.close()
            } catch (_: IOException) {
                // Ignore stream close errors
            } finally {
                inputStream = null
                responseBody?.close()
                responseBody = null
                response?.close()
                response = null
                transferEnded()
            }
        }
    }

    private data class OpenResult(
        val response: Response,
        val bytesToRead: Long
    )

    private fun executeOpenWithRetry(dataSpec: DataSpec, fileId: String): OpenResult {
        return runBlocking {
            rateLimiter.executeWithRetry(
                policy = RetryPolicy(maxRetries = MAX_OPEN_RETRIES, initialDelayMs = RETRY_DELAY_MS)
            ) {
                executeSingleOpen(dataSpec, fileId)
            }
        }
    }

    private suspend fun executeSingleOpen(dataSpec: DataSpec, fileId: String): OpenResult {
        var token = tokenManager.getValidAccessToken().getOrNull()
            ?: throw HttpDataSource.HttpDataSourceException(
                "No valid access token available",
                dataSpec,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN
            )

        var request = buildRequest(fileId = fileId, token = token, dataSpec = dataSpec)
        val call = okHttpClient.newCall(request)
        currentCall = call

        var res = call.execute()

        // Handle 401 mid-stream token expiration: refresh and retry once
        if (res.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            res.close()
            val refreshed = tokenManager.refreshAccessToken()
            if (refreshed.isSuccess) {
                token = refreshed.getOrNull() ?: token
                request = buildRequest(fileId = fileId, token = token, dataSpec = dataSpec)
                val retryCall = okHttpClient.newCall(request)
                currentCall = retryCall
                res = retryCall.execute()
            }
        }

        return processResponse(res, dataSpec)
    }

    private fun processResponse(res: Response, dataSpec: DataSpec): OpenResult {
        val statusCode = res.code
        val isRateLimitError = statusCode == HTTP_TOO_MANY_REQUESTS ||
            (statusCode == HttpURLConnection.HTTP_FORBIDDEN && isRateLimit(res))

        if (isRateLimitError) {
            res.close()
            throw RateLimitException(
                "Google Drive API rate limit reached during media stream (HTTP $statusCode)"
            )
        }

        if (statusCode == HTTP_RANGE_NOT_SATISFIABLE && dataSpec.position > 0L) {
            return OpenResult(response = res, bytesToRead = 0L)
        }

        if (statusCode != HttpURLConnection.HTTP_OK && statusCode != HttpURLConnection.HTTP_PARTIAL) {
            throw createInvalidResponseException(res, dataSpec, statusCode)
        }

        val bytesToRead = calculateBytesToRead(res, dataSpec)
        return OpenResult(response = res, bytesToRead = bytesToRead)
    }

    private fun createInvalidResponseException(
        res: Response,
        dataSpec: DataSpec,
        statusCode: Int
    ): Exception {
        val errorBytes = res.body?.bytes() ?: byteArrayOf()
        res.close()
        return HttpDataSource.InvalidResponseCodeException(
            statusCode,
            res.message,
            null,
            res.headers.toMultimap(),
            dataSpec,
            errorBytes
        )
    }

    private fun isRateLimit(response: Response): Boolean {
        val bodySnippet = response.peekBody(BODY_PEEK_BYTES).string()
        return bodySnippet.contains("rateLimitExceeded") ||
            bodySnippet.contains("userRateLimitExceeded") ||
            bodySnippet.contains("quotaExceeded")
    }

    private fun calculateBytesToRead(res: Response, dataSpec: DataSpec): Long {
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            return dataSpec.length
        }
        val contentLength = res.body?.contentLength() ?: -1L
        return if (contentLength > 0L) contentLength else C.LENGTH_UNSET.toLong()
    }

    private fun buildRequest(fileId: String, token: String, dataSpec: DataSpec): Request {
        val url = endpointTemplate.replace("{fileId}", fileId)
        val builder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")

        if (dataSpec.position > 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            val rangeStart = dataSpec.position
            val rangeEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                (rangeStart + dataSpec.length - 1).toString()
            } else {
                ""
            }
            builder.addHeader("Range", "bytes=$rangeStart-$rangeEnd")
        }

        return builder.build()
    }

    companion object {
        const val DEFAULT_DRIVE_DOWNLOAD_ENDPOINT = "https://www.googleapis.com/drive/v3/files/{fileId}?alt=media"
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val HTTP_TOO_MANY_REQUESTS = 429
        private const val MAX_OPEN_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val BODY_PEEK_BYTES = 1024L

        fun extractFileId(uri: Uri): String {
            val scheme = uri.scheme
            return when {
                scheme == "gdrive" -> {
                    uri.host ?: uri.path?.removePrefix("/") ?: uri.schemeSpecificPart.removePrefix("//")
                }
                uri.path?.contains("/files/") == true -> {
                    val path = uri.path.orEmpty()
                    val afterFiles = path.substringAfter("/files/")
                    afterFiles.substringBefore("?").substringBefore("/")
                }
                else -> uri.lastPathSegment.orEmpty()
            }
        }
    }
}
