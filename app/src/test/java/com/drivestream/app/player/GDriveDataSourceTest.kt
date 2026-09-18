package com.drivestream.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.network.RateLimiter
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

private const val GDRIVE_SCHEME = "gdrive"
private const val VIDEO_FILE_ID = "video_abc"
private const val DRIVE_PATH_PREFIX = "/drive/v3/files/"
private const val RETRYABLE_ATTEMPTS = 4

/**
 * android.net.Uri is not available in plain JVM unit tests, so the media Uri is
 * stubbed with the exact accessors [GDriveDataSource.extractFileId] reads.
 */
private fun gdriveUri(fileId: String): Uri {
    val uri: Uri = mockk(relaxed = true)
    every { uri.scheme } returns GDRIVE_SCHEME
    every { uri.host } returns fileId
    every { uri.toString() } returns "$GDRIVE_SCHEME://$fileId"
    return uri
}

private fun driveApiUri(fileId: String): Uri {
    val uri: Uri = mockk(relaxed = true)
    every { uri.scheme } returns "https"
    every { uri.path } returns "$DRIVE_PATH_PREFIX$fileId"
    every { uri.toString() } returns "https://www.googleapis.com$DRIVE_PATH_PREFIX$fileId?alt=media"
    return uri
}

class GDriveDataSourceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var tokenManager: TokenManager
    private lateinit var rateLimiter: RateLimiter
    private lateinit var dataSource: GDriveDataSource

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient = OkHttpClient()
        tokenManager = mockk()
        rateLimiter = RateLimiter()

        coEvery { tokenManager.getValidAccessToken() } returns Result.success("valid_token_123")

        dataSource = GDriveDataSource(
            okHttpClient = okHttpClient,
            tokenManager = tokenManager,
            rateLimiter = rateLimiter,
            endpointTemplate = "${mockWebServer.url("/")}files/{fileId}?alt=media"
        )
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
        mockWebServer.shutdown()
    }

    @Test
    @DisplayName("open sends request with Bearer token and alt=media")
    fun openSuccess() {
        val payload = "video-data-bytes"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(payload)
        )

        val dataSpec = DataSpec(gdriveUri(VIDEO_FILE_ID))
        val bytesToRead = dataSource.open(dataSpec)

        assertThat(bytesToRead).isEqualTo(payload.length.toLong())

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.path).contains("/files/video_abc?alt=media")
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer valid_token_123")
    }

    @Test
    @DisplayName("open sends Range header for non-zero seek position")
    fun openWithRangeHeader() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 2048-4095/10000")
                .setHeader("Content-Length", "2048")
                .setBody("range-video-data")
        )

        val dataSpec = DataSpec(
            gdriveUri(VIDEO_FILE_ID),
            2048L, // position
            2048L  // length
        )
        val bytesToRead = dataSource.open(dataSpec)

        assertThat(bytesToRead).isEqualTo(2048L)

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.getHeader("Range")).isEqualTo("bytes=2048-4095")
    }

    @Test
    @DisplayName("read retrieves payload from response body and updates position")
    fun readSuccess() {
        val testPayload = "0123456789"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", testPayload.length.toString())
                .setBody(testPayload)
        )

        val dataSpec = DataSpec(gdriveUri(VIDEO_FILE_ID))
        dataSource.open(dataSpec)

        val buffer = ByteArray(10)
        val bytesRead = dataSource.read(buffer, 0, 10)

        assertThat(bytesRead).isEqualTo(10)
        assertThat(String(buffer)).isEqualTo(testPayload)

        // Next read should return END_OF_INPUT
        val end = dataSource.read(buffer, 0, 10)
        assertThat(end).isEqualTo(C.RESULT_END_OF_INPUT)
    }

    @Test
    @DisplayName("open retries and refreshes token when encountering 401 mid-stream")
    fun midStreamTokenRefresh() {
        val payload = "refreshed-data"

        // First call: 401 Unauthorized
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )
        // Second call after refresh: 200 OK
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(payload)
        )

        coEvery { tokenManager.refreshAccessToken(any()) } returns Result.success("new_token_456")

        val dataSpec = DataSpec(gdriveUri(VIDEO_FILE_ID))
        val bytes = dataSource.open(dataSpec)

        assertThat(bytes).isEqualTo(payload.length.toLong())
        assertThat(mockWebServer.requestCount).isEqualTo(2)

        val firstReq = mockWebServer.takeRequest()
        assertThat(firstReq.getHeader("Authorization")).isEqualTo("Bearer valid_token_123")

        val secondReq = mockWebServer.takeRequest()
        assertThat(secondReq.getHeader("Authorization")).isEqualTo("Bearer new_token_456")
    }

    @Test
    @DisplayName("open handles 416 Range Not Satisfiable gracefully at end of file")
    fun handlesRangeNotSatisfiable() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */5000")
                .setBody("Requested Range Not Satisfiable")
        )

        val dataSpec = DataSpec(gdriveUri(VIDEO_FILE_ID), 5000L, 1000L)
        val bytes = dataSource.open(dataSpec)

        assertThat(bytes).isEqualTo(0L)
    }

    @Test
    @DisplayName("open throws InvalidResponseCodeException for unhandled HTTP error")
    fun openThrowsOnServerError() {
        // Server errors are retryable, so queue one response per attempt (initial + retries)
        repeat(RETRYABLE_ATTEMPTS) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")
            )
        }

        val dataSpec = DataSpec(gdriveUri(VIDEO_FILE_ID))
        val error = runCatching { dataSource.open(dataSpec) }.exceptionOrNull()

        assertThat(error).isInstanceOf(HttpDataSource.InvalidResponseCodeException::class.java)
    }

    @Test
    @DisplayName("extractFileId parses gdrive and standard URLs correctly")
    fun extractFileIdTests() {
        assertThat(GDriveDataSource.extractFileId(gdriveUri("video_123"))).isEqualTo("video_123")
        assertThat(GDriveDataSource.extractFileId(driveApiUri("file_xyz"))).isEqualTo("file_xyz")
    }
}
