package com.drivestream.app.data

import android.content.SharedPreferences
import com.drivestream.app.data.model.FileSortOption
import com.drivestream.app.di.SettingsPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @SettingsPrefs private val preferences: SharedPreferences
) {

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setSortOption(option: FileSortOption) {
        preferences.edit().putString(KEY_SORT_OPTION, option.name).apply()
        _settings.update { it.copy(sortOption = option) }
    }

    fun setViewMode(mode: BrowseViewMode) {
        preferences.edit().putString(KEY_VIEW_MODE, mode.name).apply()
        _settings.update { it.copy(viewMode = mode) }
    }

    fun setAutoplayNext(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTOPLAY_NEXT, enabled).apply()
        _settings.update { it.copy(autoplayNext = enabled) }
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        val sanitized = speed.coerceIn(AppSettings.MIN_PLAYBACK_SPEED, AppSettings.MAX_PLAYBACK_SPEED)
        preferences.edit().putFloat(KEY_DEFAULT_SPEED, sanitized).apply()
        _settings.update { it.copy(defaultPlaybackSpeed = sanitized) }
    }

    fun setResumeLastFolder(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RESUME_LAST_FOLDER, enabled).apply()
        _settings.update { it.copy(resumeLastFolder = enabled) }
    }

    fun setLastFolder(folderId: String, folderName: String) {
        preferences.edit()
            .putString(KEY_LAST_FOLDER_ID, folderId)
            .putString(KEY_LAST_FOLDER_NAME, folderName)
            .apply()
        _settings.update { it.copy(lastFolderId = folderId, lastFolderName = folderName) }
    }

    private fun readSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            sortOption = readEnum(KEY_SORT_OPTION, FileSortOption.entries, defaults.sortOption),
            viewMode = readEnum(KEY_VIEW_MODE, BrowseViewMode.entries, defaults.viewMode),
            autoplayNext = preferences.getBoolean(KEY_AUTOPLAY_NEXT, defaults.autoplayNext),
            defaultPlaybackSpeed = preferences
                .getFloat(KEY_DEFAULT_SPEED, defaults.defaultPlaybackSpeed)
                .coerceIn(AppSettings.MIN_PLAYBACK_SPEED, AppSettings.MAX_PLAYBACK_SPEED),
            resumeLastFolder = preferences.getBoolean(KEY_RESUME_LAST_FOLDER, defaults.resumeLastFolder),
            lastFolderId = preferences.getString(KEY_LAST_FOLDER_ID, null) ?: defaults.lastFolderId,
            lastFolderName = preferences.getString(KEY_LAST_FOLDER_NAME, null) ?: defaults.lastFolderName
        )
    }

    private fun <T : Enum<T>> readEnum(key: String, entries: List<T>, fallback: T): T {
        val stored = preferences.getString(key, null) ?: return fallback
        return entries.firstOrNull { it.name == stored } ?: fallback
    }

    companion object {
        const val PREFS_NAME = "drivestream_settings"

        private const val KEY_SORT_OPTION = "sort_option"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_AUTOPLAY_NEXT = "autoplay_next"
        private const val KEY_DEFAULT_SPEED = "default_speed"
        private const val KEY_RESUME_LAST_FOLDER = "resume_last_folder"
        private const val KEY_LAST_FOLDER_ID = "last_folder_id"
        private const val KEY_LAST_FOLDER_NAME = "last_folder_name"
    }
}
