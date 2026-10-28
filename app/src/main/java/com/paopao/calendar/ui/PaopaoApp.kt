package com.paopao.calendar.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paopao.calendar.AppContainer
import com.paopao.calendar.ui.calendar.CalendarScreen
import com.paopao.calendar.ui.focus.FocusScreen
import com.paopao.calendar.ui.habit.HabitEditScreen
import com.paopao.calendar.ui.habit.HabitScreen
import com.paopao.calendar.ui.inventory.InventoryEditScreen
import com.paopao.calendar.ui.inventory.InventoryScreen
import com.paopao.calendar.ui.memorial.MemorialEditScreen
import com.paopao.calendar.ui.memorial.MemorialScreen
import com.paopao.calendar.ui.more.MoreScreen
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.ui.navigation.TopLevelDestination
import com.paopao.calendar.ui.settings.MonthSettingsScreen
import com.paopao.calendar.ui.settings.SettingsScreen
import com.paopao.calendar.ui.training.ExerciseLibraryScreen
import com.paopao.calendar.ui.training.TrainingScreen
import com.paopao.calendar.ui.training.WorkoutPlanEditScreen
import com.paopao.calendar.ui.training.WorkoutTimerScreen
import kotlinx.coroutines.flow.MutableStateFlow


/**
 * 应用外壳：底部导航 + NavHost。
 *
 * 二级页面（编辑页、子模块页）不显示底栏 —— 编辑是「做完就回去」的短任务，
 * 底栏在那儿只会占掉输入法的空间。
 *
 * [pendingDeepLink] 是桌面插件的落点：Activity 是 singleTask，热启动时走 onNewIntent
 * 而不会重新 onCreate，所以路由要作为一个待消费的值流送进来，由这里跳给 NavController。
 */
@Composable
fun PaopaoApp(
    container: AppContainer,
    pendingDeepLink: MutableStateFlow<String?>,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute == null || currentRoute in TOP_LEVEL_ROUTES
    val deepLink by pendingDeepLink.collectAsStateWithLifecycle()

    LaunchedEffect(deepLink) {
        deepLink?.let { route ->
            navController.navigate(route)
            pendingDeepLink.value = null
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                PaopaoBottomBar(
                    currentRoute = currentRoute ?: Routes.CALENDAR,
                    onSelect = navController::navigateTopLevel,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.calendarStart(),
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(
                route = Routes.CALENDAR,
                arguments = listOf(
                    navArgument(Routes.ARG_EPOCH_DAY) {
                        type = NavType.LongType
                        defaultValue = Routes.NEW
                    },
                ),
            ) { entry ->
                CalendarScreen(
                    container = container,
                    initialEpochDay = Routes.dayOrNull(entry.arguments?.getLong(Routes.ARG_EPOCH_DAY)),
                    onNavigate = navController::navigate,
                )
            }
            composable(Routes.INVENTORY) {
                InventoryScreen(container = container, onNavigate = navController::navigate)
            }
            composable(
                route = Routes.INVENTORY_EDIT,
                arguments = listOf(
                    navArgument(Routes.ARG_INVENTORY_ID) { type = NavType.LongType; defaultValue = Routes.NEW },
                    navArgument(Routes.ARG_EPOCH_DAY) { type = NavType.LongType; defaultValue = Routes.NEW },
                ),
            ) { entry ->
                InventoryEditScreen(
                    container = container,
                    inventoryId = Routes.idOrNull(entry.arguments?.getLong(Routes.ARG_INVENTORY_ID)),
                    presetDueDay = Routes.idOrNull(entry.arguments?.getLong(Routes.ARG_EPOCH_DAY)),
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.FOCUS) { FocusScreen(container = container) }
            composable(Routes.MORE) { MoreScreen(navController = navController) }
            composable(Routes.SETTINGS) {
                SettingsScreen(container = container, onBack = navController::popBackStack)
            }
            composable(Routes.MONTH_SETTINGS) {
                MonthSettingsScreen(container = container, onBack = navController::popBackStack)
            }
            composable(Routes.HABIT) { HabitScreen(container = container, onNavigate = navController::navigate) }
            composable(
                route = Routes.HABIT_EDIT,
                arguments = listOf(
                    navArgument(Routes.ARG_HABIT_ID) { type = NavType.LongType; defaultValue = Routes.NEW },
                ),
            ) { entry ->
                HabitEditScreen(
                    container = container,
                    habitId = Routes.idOrNull(entry.arguments?.getLong(Routes.ARG_HABIT_ID)),
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.MEMORIAL) {
                MemorialScreen(container = container, onNavigate = navController::navigate)
            }
            composable(
                route = Routes.MEMORIAL_EDIT,
                arguments = listOf(
                    navArgument(Routes.ARG_MEMORIAL_ID) { type = NavType.LongType; defaultValue = Routes.NEW },
                ),
            ) { entry ->
                MemorialEditScreen(
                    container = container,
                    memorialId = Routes.idOrNull(entry.arguments?.getLong(Routes.ARG_MEMORIAL_ID)),
                    onBack = navController::popBackStack,
                )
            }
            composable(Routes.TRAINING) {
                TrainingScreen(container = container, onNavigate = navController::navigate)
            }
            composable(Routes.EXERCISE_LIBRARY) {
                ExerciseLibraryScreen(onBack = navController::popBackStack)
            }
            composable(
                route = Routes.TRAINING_EDIT,
                arguments = listOf(
                    navArgument(Routes.ARG_PLAN_ID) { type = NavType.LongType; defaultValue = Routes.NEW },
                ),
            ) { entry ->
                WorkoutPlanEditScreen(
                    container = container,
                    planId = Routes.idOrNull(entry.arguments?.getLong(Routes.ARG_PLAN_ID)),
                    onBack = navController::popBackStack,
                    onNavigate = navController::navigate,
                )
            }
            composable(
                route = Routes.WORKOUT_RUN,
                arguments = listOf(
                    navArgument(Routes.ARG_PLAN_ID) { type = NavType.LongType; defaultValue = Routes.NEW },
                ),
            ) { entry ->
                WorkoutTimerScreen(
                    container = container,
                    planId = Routes.idOrNull(entry.arguments?.getLong(Routes.ARG_PLAN_ID)),
                    onBack = navController::popBackStack,
                )
            }
        }
    }
}

private val TOP_LEVEL_ROUTES = setOf(
    Routes.CALENDAR,
    Routes.INVENTORY,
    Routes.FOCUS,
    Routes.MORE,
)

@Composable
private fun PaopaoBottomBar(
    currentRoute: String,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon(), contentDescription = stringResource(destination.labelRes)) },
                label = { Text(stringResource(destination.labelRes)) },
                alwaysShowLabel = false,
            )
        }
    }
}

/** 图标放在这里而不是枚举字段里：material-icons 是 Compose 类型，混进导航常量层不干净。 */
private fun TopLevelDestination.icon(): ImageVector = when (this) {
    TopLevelDestination.CALENDAR -> Icons.Filled.CalendarMonth
    TopLevelDestination.INVENTORY -> Icons.Filled.Checklist
    TopLevelDestination.FOCUS -> Icons.Filled.Timer
    TopLevelDestination.MORE -> Icons.Filled.MoreHoriz
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.launchRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
