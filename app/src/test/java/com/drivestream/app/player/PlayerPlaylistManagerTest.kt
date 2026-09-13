package com.drivestream.app.player

import app.cash.turbine.test
import com.drivestream.app.data.model.DriveFile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerPlaylistManagerTest {

    private val playlistManager = PlayerPlaylistManager()

    private val sampleVideos = listOf(
        DriveFile(id = "v1", name = "Episode 1.mp4", mimeType = "video/mp4", size = 1000L),
        DriveFile(id = "v2", name = "Episode 2.mp4", mimeType = "video/mp4", size = 2000L),
        DriveFile(id = "v3", name = "Episode 3.mp4", mimeType = "video/mp4", size = 3000L)
    )

    @Test
    @DisplayName("initial playlist is empty")
    fun initialPlaylistEmpty() {
        assertThat(playlistManager.getPlaylist()).isEmpty()
    }

    @Test
    @DisplayName("setPlaylist updates playlist and emits to state flow")
    fun setPlaylistUpdatesAndEmits() = runTest {
        playlistManager.playlist.test {
            assertThat(awaitItem()).isEmpty()

            playlistManager.setPlaylist(sampleVideos)
            val updated = awaitItem()
            assertThat(updated).hasSize(3)
            assertThat(updated.map { it.name }).containsExactly(
                "Episode 1.mp4", "Episode 2.mp4", "Episode 3.mp4"
            ).inOrder()
            assertThat(playlistManager.getPlaylist()).isEqualTo(sampleVideos)
        }
    }

    @Test
    @DisplayName("clear empties playlist and emits empty list")
    fun clearEmptiesPlaylist() = runTest {
        playlistManager.setPlaylist(sampleVideos)
        assertThat(playlistManager.getPlaylist()).hasSize(3)

        playlistManager.playlist.test {
            assertThat(awaitItem()).hasSize(3)

            playlistManager.clear()
            assertThat(awaitItem()).isEmpty()
            assertThat(playlistManager.getPlaylist()).isEmpty()
        }
    }
}
