package com.drivestream.app.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Login : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data class Browser(
        val folderId: String = "root",
        val folderName: String = "My Drive",
        val path: String = ""
    ) : Screen

    @Serializable
    data class Player(
        val fileId: String,
        val title: String,
        val folderId: String = ""
    ) : Screen

    @Serializable
    data object Downloads : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Favorites : Screen
}
