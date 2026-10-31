package com.ks.attendance.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.screens.attendance.AttendanceListScreen
import com.ks.attendance.ui.screens.calendar.CalendarScreen
import com.ks.attendance.ui.screens.home.HomeScreen
import com.ks.attendance.ui.screens.record.AddEditRecordScreen
import com.ks.attendance.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val labelAr: String, val icon: ImageVector) {
    object Home      : Screen("home",     "الرئيسية",   Icons.Filled.Home)
    object List      : Screen("list",     "السجلات",    Icons.Filled.List)
    object Calendar  : Screen("calendar", "التقويم",    Icons.Filled.CalendarMonth)
    object Settings  : Screen("settings", "الإعدادات",  Icons.Filled.Settings)
}

val bottomNavScreens = listOf(
    Screen.Home, Screen.List, Screen.Settings
)

@Composable
fun KSNavGraph(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Show bottom bar only on main screens
    val showBottomBar = bottomNavScreens.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    bottomNavScreens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.labelAr) },
                            label = { Text(screen.labelAr) },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Screen.List.route) {
                AttendanceListScreen(
                    viewModel = viewModel,
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    viewModel = viewModel,
                    onDayClick = { date ->
                        viewModel.selectDate(date)
                        navController.navigate("record/-1?date=$date")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = null
                )
            }
            // Add/Edit record — recordId = -1 means new
            composable(
                route = "record/{recordId}?date={date}",
                arguments = listOf(
                    navArgument("recordId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
                val initialDate = backStackEntry.arguments?.getString("date")
                AddEditRecordScreen(
                    viewModel = viewModel,
                    recordId = recordId,
                    initialDate = initialDate,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
