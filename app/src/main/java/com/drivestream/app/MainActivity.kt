package com.drivestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.ui.navigation.NavGraph
import com.drivestream.app.ui.navigation.Screen
import com.drivestream.app.ui.theme.DriveStreamTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = if (tokenManager.hasValidSession()) {
            Screen.Home
        } else {
            Screen.Login
        }

        setContent {
            DriveStreamTheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
