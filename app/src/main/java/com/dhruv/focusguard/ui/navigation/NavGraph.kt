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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import com.dhruv.focusguard.ui.components.ProtectionBanner
import com.dhruv.focusguard.ui.components.openAccessibilitySettings
import com.dhruv.focusguard.ui.components.rememberProtectionState
import com.dhruv.focusguard.ui.screens.GoalsScreen
import com.dhruv.focusguard.ui.screens.OnboardingScreen
import com.dhruv.focusguard.ui.screens.LimitScreen
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
private const val ONBOARDING = "onboarding"

/** The daily budget / cool-down page, opened from the Tasks screen. */
const val LIMIT_ROUTE = "daily_limit"
private val fullScreenRoutes = setOf("add_task", "edit_task/{taskId}", ONBOARDING)

@Composable
fun AppNavGraph(
    viewModel: TaskViewModel,
    openAddTask: Boolean = false,
    openSettings: Boolean = false,
    editTaskId: Long = -1L
) {
    val c = MaterialTheme.mindful
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showBottomBar = currentRoute != null && currentRoute !in fullScreenRoutes
    val startRoute = remember { if (viewModel.onboardingDone) Screen.Tasks.route else ONBOARDING }
    val context = LocalContext.current
    val protection = rememberProtectionState()

    // Arriving from the pause screen's "Add a task": go straight to the form.
    // ...or from a task tapped on the home-screen widget.
    LaunchedEffect(openAddTask, openSettings, editTaskId) {
        if (!viewModel.onboardingDone) return@LaunchedEffect
        if (openAddTask) navController.navigate("add_task")
        else if (openSettings) navController.navigate(Screen.Settings.route) { launchSingleTop = true }
        else if (editTaskId > 0L) navController.navigate("edit_task/$editTaskId")
    }

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            // Loud, persistent warning on the main tabs whenever the guard is down
            if (showBottomBar && !protection.running) {
                ProtectionBanner(onFix = { context.openAccessibilitySettings() })
            }
        },
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
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ONBOARDING) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onFinished = {
                        navController.navigate(Screen.Tasks.route) {
                            popUpTo(ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Tasks.route) {
                TaskListScreen(
                    viewModel = viewModel,
                    onAddTask = { navController.navigate("add_task") },
                    onEditTask = { id -> navController.navigate("edit_task/$id") },
                    onOpenLimit = { navController.navigate(LIMIT_ROUTE) }
                )
            }

            composable(LIMIT_ROUTE) {
                LimitScreen(viewModel = viewModel)
            }

            composable(Screen.Goals.route) {
                GoalsScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenLimit = { navController.navigate(LIMIT_ROUTE) }
                )
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
