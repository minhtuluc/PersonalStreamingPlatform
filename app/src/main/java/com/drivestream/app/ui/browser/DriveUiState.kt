package com.drivestream.app.ui.browser

import com.drivestream.app.data.BrowseViewMode
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.FileSortOption

data class DriveUiState(
    val folderId: String = "root",
    val folderName: String = "My Drive",
    val files: List<DriveFile> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextPageToken: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val sortOption: FileSortOption = FileSortOption.NAME_ASC,
    val viewMode: BrowseViewMode = BrowseViewMode.LIST,
    val favoriteIds: Set<String> = emptySet()
) {
    val isEmpty: Boolean
        get() = files.isEmpty() && !isLoading && !isRefreshing && errorMessage == null
}
