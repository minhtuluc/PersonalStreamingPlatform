package com.drivestream.app.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PlayerRepositoryTest {

    private lateinit var fakeDao: FakeWatchHistoryDao
    private lateinit var repository: PlayerRepository

    class FakeWatchHistoryDao : WatchHistoryDao {
        val historyMap = mutableMapOf<String, WatchHistoryEntity>()

        override fun getAllHistory(): Flow<List<WatchHistoryEntity>> =
            flowOf(historyMap.values.toList())

        override fun getContinueWatching(): Flow<List<WatchHistoryEntity>> =
            flowOf(historyMap.values.filter { it.lastPosition > 0 && it.lastPosition < it.duration })

        override fun getRecentHistory(limit: Int): Flow<List<WatchHistoryEntity>> =
            flowOf(historyMap.values.take(limit))

        override suspend fun getByFileId(fileId: String): WatchHistoryEntity? = historyMap[fileId]

        override suspend fun upsert(entity: WatchHistoryEntity) {
            historyMap[entity.fileId] = entity
        }

        override suspend fun delete(fileId: String) {
            historyMap.remove(fileId)
        }

        override suspend fun keepRecent(limit: Int) {
            // No-op for test
        }

        override suspend fun clearAll() {
            historyMap.clear()
        }
    }

    @BeforeEach
    fun setUp() {
        fakeDao = FakeWatchHistoryDao()
        repository = PlayerRepository(fakeDao, Dispatchers.Unconfined)
    }

    @Test
    @DisplayName("savePlaybackPosition persists record with timestamp")
    fun savePositionSuccess() = runTest {
        repository.savePlaybackPosition(
            fileId = "v1",
            fileName = "Interstellar.mkv",
            positionMs = 120_000L,
            durationMs = 600_000L
        )

        val record = fakeDao.getByFileId("v1")
        assertThat(record).isNotNull()
        assertThat(record!!.lastPosition).isEqualTo(120_000L)
        assertThat(record.duration).isEqualTo(600_000L)
    }

    @Test
    @DisplayName("getSavedPosition returns position when valid resume condition met")
    fun getSavedPositionValid() = runTest {
        fakeDao.upsert(
            WatchHistoryEntity(
                fileId = "v2",
                fileName = "Movie.mp4",
                lastPosition = 50_000L, // > 5s
                duration = 300_000L,     // remaining 250s > 10s
                lastWatched = 1000L,
                thumbnailUrl = null,
                resolution = "1080p",
                fileSize = 1000L
            )
        )

        val position = repository.getSavedPosition("v2")
        assertThat(position).isEqualTo(50_000L)
    }

    @Test
    @DisplayName("getSavedPosition returns 0 when position is under 5 seconds")
    fun getSavedPositionTooEarly() = runTest {
        fakeDao.upsert(
            WatchHistoryEntity(
                fileId = "v3",
                fileName = "Short.mp4",
                lastPosition = 3_000L, // < 5s
                duration = 100_000L,
                lastWatched = 1000L,
                thumbnailUrl = null,
                resolution = null,
                fileSize = 1000L
            )
        )

        val position = repository.getSavedPosition("v3")
        assertThat(position).isEqualTo(0L)
    }

    @Test
    @DisplayName("getSavedPosition returns 0 when video was nearly finished")
    fun getSavedPositionNearlyFinished() = runTest {
        fakeDao.upsert(
            WatchHistoryEntity(
                fileId = "v4",
                fileName = "End.mp4",
                lastPosition = 95_000L,
                duration = 100_000L, // remaining 5s < 10s threshold
                lastWatched = 1000L,
                thumbnailUrl = null,
                resolution = null,
                fileSize = 1000L
            )
        )

        val position = repository.getSavedPosition("v4")
        assertThat(position).isEqualTo(0L)
    }
}
