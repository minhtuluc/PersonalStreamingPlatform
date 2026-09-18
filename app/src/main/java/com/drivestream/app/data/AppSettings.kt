package com.drivestream.app.data

import com.drivestream.app.data.model.FileSortOption

enum class BrowseViewMode {
    LIST,
    GRID
}

data class AppSettings(
    val sortOption: FileSortOption = FileSortOption.NAME_ASC,
    val viewMode: BrowseViewMode = BrowseViewMode.LIST,
    val autoplayNext: Boolean = true,
    val defaultPlaybackSpeed: Float = DEFAULT_PLAYBACK_SPEED,
    val resumeLastFolder: Boolean = true,
    val lastFolderId: String = DEFAULT_FOLDER_ID,
    val lastFolderName: String = DEFAULT_FOLDER_NAME
) {
    companion object {
        const val DEFAULT_FOLDER_ID = "root"
        const val DEFAULT_FOLDER_NAME = "My Drive"
        const val DEFAULT_PLAYBACK_SPEED = 1.0f
        const val MIN_PLAYBACK_SPEED = 0.5f
        const val MAX_PLAYBACK_SPEED = 2.0f
    }
}
