package com.drivestream.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.hilt.navigation.compose.hiltViewModel
import com.drivestream.app.auth.AuthViewModel
import com.drivestream.app.ui.screens.BrowserScreen
import com.drivestream.app.ui.screens.DownloadsScreen
import com.drivestream.app.ui.screens.FavoritesScreen
import com.drivestream.app.ui.screens.HomeScreen
import com.drivestream.app.ui.screens.LoginScreen
import com.drivestream.app.ui.screens.PlayerScreen
import com.drivestream.app.ui.screens.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: Screen = Screen.Login,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Screen.Login> {
            val authViewModel: AuthViewModel = hiltViewModel()
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home) {
                        popUpTo<Screen.Login> { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        composable<Screen.Home> {
            val authViewModel: AuthViewModel = hiltViewModel()
            HomeScreen(
                onNavigateToBrowser = { folderId, folderName ->
                    navController.navigate(
                        Screen.Browser(folderId = folderId, folderName = Uri.encode(folderName))
                    )
                },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings)
                },
                onNavigateToFavorites = {
                    navController.navigate(Screen.Favorites)
                },
                onPlayVideo = { fileId, title ->
                    navController.navigate(Screen.Player(fileId = fileId, title = Uri.encode(title)))
                },
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate(Screen.Login) {
                        popUpTo<Screen.Home> { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.Browser> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Browser>()
            val currentName = Uri.decode(route.folderName)
            val breadcrumbs = decodeBreadcrumbPath(route.path) + (route.folderId to currentName)
            BrowserScreen(
                folderId = route.folderId,
                folderName = currentName,
                breadcrumbs = breadcrumbs.map { it.second },
                onCrumbClick = { index ->
                    repeat(breadcrumbs.size - index - 1) { navController.popBackStack() }
                },
                onNavigateBack = { navController.popBackStack() },
                onFolderClick = { subFolderId, subFolderName ->
                    navController.navigate(
                        Screen.Browser(
                            folderId = subFolderId,
                            folderName = Uri.encode(subFolderName),
                            path = appendBreadcrumbPath(route.path, route.folderId, currentName)
                        )
                    )
                },
                onVideoClick = { fileId, title ->
                    navController.navigate(
                        Screen.Player(
                            fileId = fileId,
                            title = Uri.encode(title),
                            folderId = route.folderId
                        )
                    )
                }
            )
        }

        composable<Screen.Player> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Player>()
            PlayerScreen(
                fileId = route.fileId,
                title = Uri.decode(route.title),
                folderId = route.folderId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.Downloads> {
            DownloadsScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlayVideo = { fileId, title ->
                    navController.navigate(Screen.Player(fileId = fileId, title = Uri.encode(title)))
                }
            )
        }

        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Screen.Login) {
                        popUpTo<Screen.Home> { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.Favorites> {
            FavoritesScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlayVideo = { fileId, title ->
                    navController.navigate(Screen.Player(fileId = fileId, title = Uri.encode(title)))
                },
                onOpenFolder = { folderId, folderName ->
                    navController.navigate(
                        Screen.Browser(folderId = folderId, folderName = Uri.encode(folderName))
                    )
                }
            )
        }
    }
}

private const val CRUMB_SEGMENT_SEPARATOR = ";"
private const val CRUMB_FIELD_SEPARATOR = ","
private const val CRUMB_FIELD_COUNT = 2

private fun appendBreadcrumbPath(existingPath: String, folderId: String, folderName: String): String {
    val segment = "$folderId$CRUMB_FIELD_SEPARATOR${Uri.encode(folderName)}"
    return if (existingPath.isEmpty()) {
        segment
    } else {
        "$existingPath$CRUMB_SEGMENT_SEPARATOR$segment"
    }
}

private fun decodeBreadcrumbPath(path: String): List<Pair<String, String>> {
    if (path.isEmpty()) return emptyList()
    return path.split(CRUMB_SEGMENT_SEPARATOR).mapNotNull { segment ->
        val fields = segment.split(CRUMB_FIELD_SEPARATOR, limit = CRUMB_FIELD_COUNT)
        if (fields.size == CRUMB_FIELD_COUNT) fields[0] to Uri.decode(fields[1]) else null
    }
}
