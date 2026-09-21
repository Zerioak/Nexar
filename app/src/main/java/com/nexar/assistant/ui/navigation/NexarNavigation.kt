package com.nexar.assistant.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nexar.assistant.ui.screens.ConversationScreen
import com.nexar.assistant.ui.screens.MemoryScreen
import com.nexar.assistant.ui.screens.PermissionsScreen
import com.nexar.assistant.ui.screens.SettingsScreen
import com.nexar.assistant.ui.viewmodel.NexarViewModel

object NexarRoutes {
    const val CONVERSATION = "conversation"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val MEMORY = "memory"
}

@Composable
fun NexarNavHost(
    navController: NavHostController,
    viewModel: NexarViewModel
) {
    NavHost(
        navController = navController,
        startDestination = NexarRoutes.CONVERSATION
    ) {
        composable(NexarRoutes.CONVERSATION) {
            ConversationScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate(NexarRoutes.SETTINGS) },
                onNavigateToPermissions = { navController.navigate(NexarRoutes.PERMISSIONS) },
                onNavigateToMemory = { navController.navigate(NexarRoutes.MEMORY) }
            )
        }
        composable(NexarRoutes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPermissions = { navController.navigate(NexarRoutes.PERMISSIONS) }
            )
        }
        composable(NexarRoutes.PERMISSIONS) {
            PermissionsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(NexarRoutes.MEMORY) {
            MemoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
