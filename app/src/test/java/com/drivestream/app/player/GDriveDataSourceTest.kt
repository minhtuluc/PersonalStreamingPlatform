package com.drivestream.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.network.RateLimiter
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
            endpointTemplate = mockWebServer.url("/files/{fileId}?alt=media").toString()
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
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "1024")
                .setBody("video-data-bytes")
        )

        val dataSpec = DataSpec(Uri.parse("gdrive://video_abc"))
        val bytesToRead = dataSource.open(dataSpec)

        assertThat(bytesToRead).isEqualTo(1024L)

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
            Uri.parse("gdrive://video_abc"),
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

        val dataSpec = DataSpec(Uri.parse("gdrive://video_abc"))
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
                .setHeader("Content-Length", "500")
                .setBody("refreshed-data")
        )

        coEvery { tokenManager.refreshAccessToken() } returns Result.success("new_token_456")

        val dataSpec = DataSpec(Uri.parse("gdrive://video_abc"))
        val bytes = dataSource.open(dataSpec)

        assertThat(bytes).isEqualTo(500L)
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

        val dataSpec = DataSpec(Uri.parse("gdrive://video_abc"), 5000L, 1000L)
        val bytes = dataSource.open(dataSpec)

        assertThat(bytes).isEqualTo(0L)
    }

    @Test
    @DisplayName("open throws InvalidResponseCodeException for unhandled HTTP error")
    fun openThrowsOnServerError() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val dataSpec = DataSpec(Uri.parse("gdrive://video_abc"))
        assertThrows(HttpDataSource.InvalidResponseCodeException::class.java) {
            dataSource.open(dataSpec)
        }
    }

    @Test
    @DisplayName("extractFileId parses gdrive and standard URLs correctly")
    fun extractFileIdTests() {
        assertThat(GDriveDataSource.extractFileId(Uri.parse("gdrive://video_123"))).isEqualTo("video_123")
        assertThat(
            GDriveDataSource.extractFileId(
                Uri.parse("https://www.googleapis.com/drive/v3/files/file_xyz?alt=media")
            )
        ).isEqualTo("file_xyz")
    }
}
