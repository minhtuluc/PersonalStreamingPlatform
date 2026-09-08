package com.drivestream.app

import com.drivestream.app.data.DownloadStatus
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.data.FileCacheEntity
import com.drivestream.app.data.WatchHistoryEntity
import com.drivestream.app.player.BufferConfig
import com.drivestream.app.ui.navigation.Screen
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SprintZeroSetupTest {

    @Nested
    @DisplayName("Buffer Configuration Tests")
    inner class BufferConfigTests {
        @Test
        fun `verify buffer configuration values match technical requirements`() {
            assertThat(BufferConfig.MIN_BUFFER_MS).isEqualTo(30_000)
            assertThat(BufferConfig.MAX_BUFFER_MS).isEqualTo(90_000)
            assertThat(BufferConfig.BUFFER_FOR_PLAYBACK_MS).isEqualTo(2_500)
            assertThat(BufferConfig.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS).isEqualTo(5_000)
            assertThat(BufferConfig.BACK_BUFFER_DURATION_MS).isEqualTo(30_000)
            assertThat(BufferConfig.RETAIN_BACK_BUFFER).isTrue()
            assertThat(BufferConfig.CONNECT_TIMEOUT_MS).isEqualTo(10_000)
            assertThat(BufferConfig.READ_TIMEOUT_MS).isEqualTo(30_000)
            assertThat(BufferConfig.CHUNK_SIZE_BYTES).isEqualTo(2 * 1024 * 1024)
        }
    }

    @Nested
    @DisplayName("Room Entity Tests")
    inner class RoomEntityTests {
        @Test
        fun `verify WatchHistoryEntity construction`() {
            val entity = WatchHistoryEntity(
                fileId = "video-101",
                fileName = "4k_nature.mkv",
                lastPosition = 120_000L,
                duration = 3_600_000L,
                lastWatched = 1725800000000L,
                thumbnailUrl = "https://lh3.googleusercontent.com/thumbnail",
                resolution = "3840x2160",
                fileSize = 4_500_000_000L
            )

            assertThat(entity.fileId).isEqualTo("video-101")
            assertThat(entity.resolution).isEqualTo("3840x2160")
            assertThat(entity.lastPosition).isEqualTo(120_000L)
        }

        @Test
        fun `verify FileCacheEntity construction`() {
            val entity = FileCacheEntity(
                folderId = "root",
                filesJson = "[]",
                cachedAt = 1725800000000L
            )

            assertThat(entity.folderId).isEqualTo("root")
            assertThat(entity.filesJson).isEqualTo("[]")
        }

        @Test
        fun `verify DownloadedVideoEntity and DownloadStatus construction`() {
            val entity = DownloadedVideoEntity(
                fileId = "dl-01",
                fileName = "offline_movie.mp4",
                localPath = "/data/user/0/com.drivestream.app/files/dl-01.mp4",
                fileSize = 1_500_000_000L,
                downloadedBytes = 1_500_000_000L,
                downloadedAt = 1725800000000L,
                status = DownloadStatus.COMPLETED
            )

            assertThat(entity.status).isEqualTo(DownloadStatus.COMPLETED)
            assertThat(entity.downloadedBytes).isEqualTo(entity.fileSize)
        }

        @Test
        fun `verify Room Converters for DownloadStatus enum`() {
            val converters = com.drivestream.app.data.Converters()
            
            // Round trip
            assertThat(converters.fromDownloadStatus(DownloadStatus.COMPLETED)).isEqualTo("COMPLETED")
            assertThat(converters.toDownloadStatus("COMPLETED")).isEqualTo(DownloadStatus.COMPLETED)
            assertThat(converters.toDownloadStatus("DOWNLOADING")).isEqualTo(DownloadStatus.DOWNLOADING)
            assertThat(converters.toDownloadStatus("QUEUED")).isEqualTo(DownloadStatus.QUEUED)
            assertThat(converters.toDownloadStatus("PAUSED")).isEqualTo(DownloadStatus.PAUSED)
            
            // Null safety and invalid fallback
            assertThat(converters.fromDownloadStatus(null)).isNull()
            assertThat(converters.toDownloadStatus(null)).isEqualTo(DownloadStatus.FAILED)
            assertThat(converters.toDownloadStatus("UNKNOWN_STATUS")).isEqualTo(DownloadStatus.FAILED)
        }
    }

    @Nested
    @DisplayName("Navigation Route Tests")
    inner class NavigationRouteTests {
        @Test
        fun `verify default route parameters`() {
            val browserRoute = Screen.Browser()
            assertThat(browserRoute.folderId).isEqualTo("root")
            assertThat(browserRoute.folderName).isEqualTo("My Drive")

            val playerRoute = Screen.Player(fileId = "f1", title = "Video 1")
            assertThat(playerRoute.fileId).isEqualTo("f1")
            assertThat(playerRoute.title).isEqualTo("Video 1")
        }
    }

    @Nested
    @DisplayName("Error Taxonomy Tests")
    inner class ErrorTaxonomyTests {
        @Test
        fun `verify rate limited error properties`() {
            val error = AppError.RateLimited(retryAfterMs = 4000L)
            assertThat(error.isRetryable).isTrue()
            assertThat(error.severity).isEqualTo(ErrorSeverity.LOW)
            assertThat(error.technicalMessage).contains("4000ms")
        }

        @Test
        fun `verify token expired error is not retryable without refresh`() {
            val error = AppError.TokenExpired(cause = RuntimeException("OAuth revoked"))
            assertThat(error.isRetryable).isFalse()
            assertThat(error.severity).isEqualTo(ErrorSeverity.HIGH)
        }
    }
}
