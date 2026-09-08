package com.drivestream.app.network

import com.drivestream.app.AppError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DriveRemoteDataSourceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var json: Json
    private lateinit var rateLimiter: RateLimiter
    private lateinit var dataSource: DriveRemoteDataSource

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient = OkHttpClient()
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        rateLimiter = RateLimiter()

        dataSource = DriveRemoteDataSource(
            okHttpClient = okHttpClient,
            json = json,
            rateLimiter = rateLimiter,
            ioDispatcher = Dispatchers.Unconfined
        ).apply {
            baseUrl = mockWebServer.url("/files").toString()
        }
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    @DisplayName("listFiles parses folders and video metadata accurately")
    fun listFilesSuccess() = runTest {
        val mockJson = """
            {
                "nextPageToken": "token123",
                "files": [
                    {
                        "id": "folder_1",
                        "name": "Movies",
                        "mimeType": "application/vnd.google-apps.folder"
                    },
                    {
                        "id": "video_1",
                        "name": "Interstellar.mkv",
                        "mimeType": "video/x-matroska",
                        "size": "4294967296",
                        "thumbnailLink": "https://lh3.googleusercontent.com/thumb",
                        "modifiedTime": "2026-09-08T12:00:00Z",
                        "videoMediaMetadata": {
                            "width": 3840,
                            "height": 2160,
                            "durationMillis": "10140000"
                        }
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockJson)
        )

        val result = dataSource.listFiles(folderId = "root")
        assertThat(result.isSuccess).isTrue()

        val list = result.getOrThrow()
        assertThat(list.nextPageToken).isEqualTo("token123")
        assertThat(list.files).hasSize(2)

        val folder = list.files[0]
        assertThat(folder.id).isEqualTo("folder_1")
        assertThat(folder.isFolder).isTrue()

        val video = list.files[1]
        assertThat(video.id).isEqualTo("video_1")
        assertThat(video.isFolder).isFalse()
        assertThat(video.size).isEqualTo(4294967296L)
        assertThat(video.resolution?.label).isEqualTo("4K")
        assertThat(video.durationMs).isEqualTo(10140000L)
    }

    @Test
    @DisplayName("listFiles returns Authentication error on HTTP 401")
    fun listFilesUnauthorized() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("Unauthorized")
        )

        val result = dataSource.listFiles(folderId = "root")
        assertThat(result.isFailure).isTrue()

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(AppError.TokenExpired::class.java)
    }

    @Test
    @DisplayName("listFiles returns ServerError on HTTP 403 standard permission failure")
    fun listFilesForbidden() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error":{"message":"Forbidden"}}""")
        )

        val result = dataSource.listFiles(folderId = "root")
        assertThat(result.isFailure).isTrue()

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(AppError.ServerError::class.java)
        assertThat((error as AppError.ServerError).statusCode).isEqualTo(403)
    }

    @Test
    @DisplayName("searchVideos executes search query properly")
    fun searchVideosSuccess() = runTest {
        val mockJson = """
            {
                "files": [
                    {
                        "id": "video_2",
                        "name": "Oppenheimer.mp4",
                        "mimeType": "video/mp4",
                        "size": "2147483648"
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockJson)
        )

        val result = dataSource.searchVideos(query = "Oppenheimer")
        assertThat(result.isSuccess).isTrue()

        val list = result.getOrThrow()
        assertThat(list.files).hasSize(1)
        assertThat(list.files[0].name).isEqualTo("Oppenheimer.mp4")

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.requestUrl?.queryParameter("q")).contains("Oppenheimer")
    }

    @Test
    @DisplayName("listFiles correctly resolves folder and video shortcuts")
    fun listFilesShortcutSupport() = runTest {
        val mockJson = """
            {
                "files": [
                    {
                        "id": "shortcut_folder",
                        "name": "PC Movies Shortcut",
                        "mimeType": "application/vnd.google-apps.shortcut",
                        "shortcutDetails": {
                            "targetId": "real_target_folder_123",
                            "targetMimeType": "application/vnd.google-apps.folder"
                        }
                    },
                    {
                        "id": "shortcut_video",
                        "name": "Avatar.mp4",
                        "mimeType": "application/vnd.google-apps.shortcut",
                        "shortcutDetails": {
                            "targetId": "real_video_456",
                            "targetMimeType": "video/mp4"
                        }
                    },
                    {
                        "id": "shortcut_ignored_doc",
                        "name": "Notes.pdf",
                        "mimeType": "application/vnd.google-apps.shortcut",
                        "shortcutDetails": {
                            "targetId": "real_doc_789",
                            "targetMimeType": "application/pdf"
                        }
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockJson)
        )

        val result = dataSource.listFiles(folderId = "root")
        assertThat(result.isSuccess).isTrue()

        val list = result.getOrThrow()
        assertThat(list.files).hasSize(2)
        assertThat(list.files[0].id).isEqualTo("real_target_folder_123")
        assertThat(list.files[0].isFolder).isTrue()
        assertThat(list.files[1].id).isEqualTo("real_video_456")
        assertThat(list.files[1].isFolder).isFalse()
    }
}
