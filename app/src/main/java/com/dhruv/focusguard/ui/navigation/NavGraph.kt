package com.dhruv.focusguard.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dhruv.focusguard.ui.screens.AddEditTaskScreen
import com.dhruv.focusguard.ui.screens.GoalsScreen
import com.dhruv.focusguard.ui.screens.SettingsScreen
import com.dhruv.focusguard.ui.screens.TaskListScreen
import com.dhruv.focusguard.ui.theme.mindful
import com.dhruv.focusguard.ui.viewmodel.TaskViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Tasks : Screen("tasks", "Tasks", Icons.Default.CheckCircle)
    data object Goals : Screen("goals", "Goals", Icons.Default.Star)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomTabs = listOf(Screen.Tasks, Screen.Goals, Screen.Settings)

/** Routes that are full-screen flows and hide the bottom bar. */
private val fullScreenRoutes = setOf("add_task", "edit_task/{taskId}")

@Composable
fun AppNavGraph(viewModel: TaskViewModel) {
    val c = MaterialTheme.mindful
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute !in fullScreenRoutes

    Scaffold(
        containerColor = c.canvas,
        bottomBar = {
            if (showBottomBar) {
                Box(modifier = Modifier.background(c.surface)) {
                    // Hairline above the bar instead of a heavy elevation shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(c.border)
                    )
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        bottomTabs.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = screen.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.accentDeep,
                                    selectedTextColor = c.accentDeep,
                                    indicatorColor = c.accentSoft,
                                    unselectedIconColor = c.textSecondary,
                                    unselectedTextColor = c.textSecondary
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Tasks.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Tasks.route) {
                TaskListScreen(
                    viewModel = viewModel,
                    onAddTask = { navController.navigate("add_task") },
                    onEditTask = { id -> navController.navigate("edit_task/$id") }
                )
            }

            composable(Screen.Goals.route) {
                GoalsScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }

            composable("add_task") {
                AddEditTaskScreen(
                    viewModel = viewModel,
                    taskId = null,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                "edit_task/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getLong("taskId")
                AddEditTaskScreen(
                    viewModel = viewModel,
                    taskId = taskId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
