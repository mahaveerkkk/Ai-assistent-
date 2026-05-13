// File: app/src/main/java/com/myai/assistant/ui/navigation/AppNavigation.kt
// App Navigation — All screen routing

package com.myai.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myai.assistant.ui.screens.ChatScreen
import com.myai.assistant.ui.screens.PermissionScreen
import com.myai.assistant.ui.screens.SettingsScreen

object AppRoutes {
    const val PERMISSION = "permission"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val CAMERA = "camera"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.PERMISSION
    ) {
        // Permission Screen — pehle permissions maango
        composable(AppRoutes.PERMISSION) {
            PermissionScreen(
                onAllPermissionsGranted = {
                    navController.navigate(AppRoutes.CHAT) {
                        popUpTo(AppRoutes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }

        // Chat Screen — main AI chat interface
        composable(AppRoutes.CHAT) {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate(AppRoutes.SETTINGS)
                }
            )
        }

        // Settings Screen
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
