package com.drivestream.app.network

import com.drivestream.app.AppError
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.DriveFileList
import com.drivestream.app.data.model.GoogleDriveFileDto
import com.drivestream.app.data.model.GoogleDriveFileListDto
import com.drivestream.app.data.model.Resolution
import com.drivestream.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRemoteDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val rateLimiter: RateLimiter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    internal var baseUrl: String = DRIVE_FILES_ENDPOINT

    suspend fun listFiles(
        folderId: String,
        pageToken: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<DriveFileList> = withContext(ioDispatcher) {
        runCatching {
            rateLimiter.executeWithRetry {
                executeListRequest(folderId = folderId, pageToken = pageToken, pageSize = pageSize)
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(AppError.fromThrowable(it)) }
        )
    }

    suspend fun searchVideos(
        query: String,
        pageToken: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Result<DriveFileList> = withContext(ioDispatcher) {
        runCatching {
            rateLimiter.executeWithRetry {
                executeSearchRequest(query = query, pageToken = pageToken, pageSize = pageSize)
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(AppError.fromThrowable(it)) }
        )
    }

    private fun executeListRequest(
        folderId: String,
        pageToken: String?,
        pageSize: Int
    ): DriveFileList {
        val escapedFolderId = folderId.replace("'", "\\'")
        val driveQuery = "'$escapedFolderId' in parents and " +
            "(mimeType contains 'video/' or mimeType = '${DriveFile.FOLDER_MIME_TYPE}') and " +
            "trashed = false"

        return fetchDriveFiles(query = driveQuery, pageToken = pageToken, pageSize = pageSize)
    }

    private fun executeSearchRequest(
        query: String,
        pageToken: String?,
        pageSize: Int
    ): DriveFileList {
        val escapedQuery = query.replace("'", "\\'")
        val driveQuery = "name contains '$escapedQuery' and " +
            "(mimeType contains 'video/' or mimeType = '${DriveFile.FOLDER_MIME_TYPE}') and " +
            "trashed = false"

        return fetchDriveFiles(query = driveQuery, pageToken = pageToken, pageSize = pageSize)
    }

    private fun fetchDriveFiles(
        query: String,
        pageToken: String?,
        pageSize: Int
    ): DriveFileList {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("fields", DRIVE_FIELDS)
            .addQueryParameter("pageSize", pageSize.toString())
            .addQueryParameter("orderBy", "folder,name")
            .addQueryParameter("supportsAllDrives", "true")
            .addQueryParameter("includeItemsFromAllDrives", "true")

        if (!pageToken.isNullOrBlank()) {
            urlBuilder.addQueryParameter("pageToken", pageToken)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                handleHttpError(response.code, responseBody)
            }

            val dtoList = json.decodeFromString<GoogleDriveFileListDto>(responseBody)
            return DriveFileList(
                files = dtoList.files.map { it.toDomain() },
                nextPageToken = dtoList.nextPageToken
            )
        }
    }

    private fun handleHttpError(code: Int, body: String): Nothing {
        throw createHttpException(code, body)
    }

    private fun createHttpException(code: Int, body: String): Exception {
        return when {
            code == HTTP_TOO_MANY_REQUESTS -> {
                RateLimitException("Google Drive API rate limit reached (HTTP 429)")
            }
            code == HTTP_FORBIDDEN && isRateLimitBody(body) -> {
                RateLimitException("Google Drive quota or user rate limit exceeded (HTTP 403)")
            }
            code == HTTP_UNAUTHORIZED -> {
                AppError.TokenExpired(
                    Exception("Authentication session expired. Please sign in again.")
                )
            }
            else -> {
                AppError.ServerError(
                    statusCode = code,
                    cause = Exception("Drive request failed with status $code")
                )
            }
        }
    }

    private fun isRateLimitBody(body: String): Boolean {
        return body.contains("rateLimitExceeded") ||
            body.contains("userRateLimitExceeded") ||
            body.contains("quotaExceeded")
    }

    private fun GoogleDriveFileDto.toDomain(): DriveFile {
        val isFolder = mimeType == DriveFile.FOLDER_MIME_TYPE
        val resolution = videoMediaMetadata?.let { meta ->
            if (meta.width != null && meta.height != null) {
                Resolution(width = meta.width, height = meta.height)
            } else {
                null
            }
        }
        val duration = videoMediaMetadata?.durationMillis?.toLongOrNull()
        val sizeBytes = size?.toLongOrNull() ?: 0L
        val modifiedEpoch = modifiedTime?.let { parseIsoTime(it) } ?: 0L

        return DriveFile(
            id = id,
            name = name,
            mimeType = mimeType,
            size = sizeBytes,
            thumbnailUrl = thumbnailLink,
            resolution = resolution,
            durationMs = duration,
            modifiedAtEpochMs = modifiedEpoch,
            isFolder = isFolder
        )
    }

    private fun parseIsoTime(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        }
    }

    companion object {
        private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_FIELDS =
            "nextPageToken, files(id, name, mimeType, size, thumbnailLink, videoMediaMetadata, modifiedTime)"
        const val DEFAULT_PAGE_SIZE = 50
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
