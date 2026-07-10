// File: app/src/main/java/com/myai/assistant/ui/navigation/AppNavigation.kt
// App Navigation — All screen routing

package com.myai.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myai.assistant.data.repository.SettingsRepository
import com.myai.assistant.permissions.PermissionManager
import com.myai.assistant.ui.screens.ChatScreen
import com.myai.assistant.ui.screens.PermissionScreen
import com.myai.assistant.ui.screens.SettingsScreen
import com.myai.assistant.ui.screens.CameraScreen
import com.myai.assistant.ui.screens.OnboardingScreen

object AppRoutes {
    const val ONBOARDING = "onboarding"
    const val PERMISSION = "permission"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val CAMERA = "camera"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val startRoute = remember {
        if (!settingsRepository.onboardingCompleted) {
            AppRoutes.ONBOARDING
        } else if (!PermissionManager.areCriticalPermissionsGranted(context)) {
            AppRoutes.PERMISSION
        } else {
            AppRoutes.CHAT
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute
    ) {
        // Onboarding Screen
        composable(AppRoutes.ONBOARDING) {
            OnboardingScreen(
                settingsRepository = settingsRepository,
                onFinished = {
                    navController.navigate(AppRoutes.PERMISSION) {
                        popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

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
                },
                onNavigateToCamera = {
                    navController.navigate(AppRoutes.CAMERA)
                }
            )
        }

        // Settings Screen
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Camera Screen
        composable(AppRoutes.CAMERA) {
            CameraScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
