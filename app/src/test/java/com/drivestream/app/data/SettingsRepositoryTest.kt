package com.drivestream.app.data

import com.drivestream.app.data.model.FileSortOption
import com.drivestream.app.testing.FakeSharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SettingsRepositoryTest {

    private lateinit var preferences: FakeSharedPreferences
    private lateinit var repository: SettingsRepository

    @BeforeEach
    fun setUp() {
        preferences = FakeSharedPreferences()
        repository = SettingsRepository(preferences)
    }

    @Test
    @DisplayName("defaults are exposed when nothing has been stored yet")
    fun defaultsAreExposed() {
        val settings = repository.settings.value

        assertThat(settings.sortOption).isEqualTo(FileSortOption.NAME_ASC)
        assertThat(settings.viewMode).isEqualTo(BrowseViewMode.LIST)
        assertThat(settings.autoplayNext).isTrue()
        assertThat(settings.defaultPlaybackSpeed).isEqualTo(AppSettings.DEFAULT_PLAYBACK_SPEED)
        assertThat(settings.resumeLastFolder).isTrue()
        assertThat(settings.lastFolderId).isEqualTo(AppSettings.DEFAULT_FOLDER_ID)
        assertThat(settings.lastFolderName).isEqualTo(AppSettings.DEFAULT_FOLDER_NAME)
    }

    @Test
    @DisplayName("setters update the exposed state and survive a repository rebuild")
    fun settersPersistAndEmit() {
        repository.setSortOption(FileSortOption.SIZE_DESC)
        repository.setViewMode(BrowseViewMode.GRID)
        repository.setAutoplayNext(false)
        repository.setDefaultPlaybackSpeed(1.5f)
        repository.setResumeLastFolder(false)
        repository.setLastFolder("folder-42", "Phim")

        val settings = repository.settings.value
        assertThat(settings.sortOption).isEqualTo(FileSortOption.SIZE_DESC)
        assertThat(settings.viewMode).isEqualTo(BrowseViewMode.GRID)
        assertThat(settings.autoplayNext).isFalse()
        assertThat(settings.defaultPlaybackSpeed).isEqualTo(1.5f)
        assertThat(settings.resumeLastFolder).isFalse()
        assertThat(settings.lastFolderId).isEqualTo("folder-42")
        assertThat(settings.lastFolderName).isEqualTo("Phim")

        val reloaded = SettingsRepository(preferences)
        assertThat(reloaded.settings.value.viewMode).isEqualTo(BrowseViewMode.GRID)
        assertThat(reloaded.settings.value.lastFolderName).isEqualTo("Phim")
    }

    @Test
    @DisplayName("playback speed outside the supported range is clamped")
    fun playbackSpeedIsClamped() {
        repository.setDefaultPlaybackSpeed(9.0f)
        assertThat(repository.settings.value.defaultPlaybackSpeed)
            .isEqualTo(AppSettings.MAX_PLAYBACK_SPEED)

        repository.setDefaultPlaybackSpeed(0.1f)
        assertThat(repository.settings.value.defaultPlaybackSpeed)
            .isEqualTo(AppSettings.MIN_PLAYBACK_SPEED)
    }

    @Test
    @DisplayName("unknown stored enum values fall back to defaults")
    fun unknownStoredEnumFallsBack() {
        preferences.edit().putString("sort_option", "NOT_A_SORT_OPTION").apply()
        preferences.edit().putString("view_mode", "NOT_A_VIEW_MODE").apply()

        val settings = SettingsRepository(preferences).settings.value
        assertThat(settings.sortOption).isEqualTo(FileSortOption.NAME_ASC)
        assertThat(settings.viewMode).isEqualTo(BrowseViewMode.LIST)
    }
}
