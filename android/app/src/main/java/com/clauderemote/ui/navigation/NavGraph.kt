package com.clauderemote.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clauderemote.ui.screens.projects.ProjectsScreen
import com.clauderemote.ui.screens.projects.ProjectsViewModel
import com.clauderemote.ui.screens.sessions.SessionsScreen
import com.clauderemote.ui.screens.sessions.SessionsViewModel
import com.clauderemote.ui.screens.settings.SettingsScreen
import com.clauderemote.ui.screens.settings.SettingsViewModel
import com.clauderemote.ui.screens.terminal.TerminalScreen
import com.clauderemote.ui.screens.terminal.TerminalViewModel

object Routes {
    const val SETTINGS = "settings"
    const val PROJECTS = "projects"
    const val SESSIONS = "sessions/{projectId}"
    const val TERMINAL = "terminal/{projectId}/{sessionId}"

    fun sessions(projectId: String) = "sessions/$projectId"
    fun terminal(projectId: String, sessionId: String) = "terminal/$projectId/$sessionId"
}

@Composable
fun ClaudeRemoteNavGraph() {
    val navController = rememberNavController()

    // Determine start destination based on settings
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()
    val isLoading by settingsViewModel.isLoading.collectAsState()

    // Wait for settings to load
    if (isLoading) {
        return
    }

    val startDestination = if (settings == null) Routes.SETTINGS else Routes.PROJECTS

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onSettingsSaved = {
                    navController.navigate(Routes.PROJECTS) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.PROJECTS) {
            val viewModel: ProjectsViewModel = hiltViewModel()
            ProjectsScreen(
                viewModel = viewModel,
                onProjectClick = { projectId ->
                    navController.navigate(Routes.sessions(projectId))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.SESSIONS,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val viewModel: SessionsViewModel = hiltViewModel()

            LaunchedEffect(projectId) {
                viewModel.loadSessions(projectId)
            }

            SessionsScreen(
                viewModel = viewModel,
                projectId = projectId,
                onSessionClick = { sessionId ->
                    navController.navigate(Routes.terminal(projectId, sessionId))
                },
                onNewSession = {
                    navController.navigate(Routes.terminal(projectId, "new"))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.TERMINAL,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val sessionIdArg = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            val sessionId = if (sessionIdArg == "new") null else sessionIdArg

            val viewModel: TerminalViewModel = hiltViewModel()

            LaunchedEffect(projectId, sessionId) {
                viewModel.initialize(projectId, sessionId)
            }

            TerminalScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
