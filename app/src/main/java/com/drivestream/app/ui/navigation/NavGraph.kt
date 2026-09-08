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
import com.drivestream.app.ui.screens.HomeScreen
import com.drivestream.app.ui.screens.LoginScreen
import com.drivestream.app.ui.screens.PlayerScreen

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
                onNavigateToBrowser = {
                    navController.navigate(Screen.Browser())
                },
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads)
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
            BrowserScreen(
                folderId = route.folderId,
                folderName = Uri.decode(route.folderName),
                onNavigateBack = { navController.popBackStack() },
                onFolderClick = { subFolderId, subFolderName ->
                    navController.navigate(
                        Screen.Browser(folderId = subFolderId, folderName = Uri.encode(subFolderName))
                    )
                },
                onVideoClick = { fileId, title ->
                    navController.navigate(Screen.Player(fileId = fileId, title = Uri.encode(title)))
                }
            )
        }

        composable<Screen.Player> { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Player>()
            PlayerScreen(
                fileId = route.fileId,
                title = Uri.decode(route.title),
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
    }
}
