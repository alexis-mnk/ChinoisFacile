package com.chinoisfacile.mandarin.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chinoisfacile.mandarin.AppContainer
import com.chinoisfacile.mandarin.R
import com.chinoisfacile.mandarin.ui.home.HomeScreen
import com.chinoisfacile.mandarin.ui.home.HomeViewModel
import com.chinoisfacile.mandarin.ui.lesson.LessonScreen
import com.chinoisfacile.mandarin.ui.lesson.LessonViewModel
import com.chinoisfacile.mandarin.ui.lexicon.LexiconScreen
import com.chinoisfacile.mandarin.ui.lexicon.LexiconViewModel
import com.chinoisfacile.mandarin.ui.settings.SettingsScreen
import com.chinoisfacile.mandarin.ui.settings.SettingsViewModel
import com.chinoisfacile.mandarin.ui.stats.StatsScreen
import com.chinoisfacile.mandarin.ui.stats.StatsViewModel
import com.chinoisfacile.mandarin.ui.steplesson.StepLessonScreen
import com.chinoisfacile.mandarin.ui.steplesson.StepLessonViewModel

private object Routes {
    const val HOME = "home"
    const val LEXICON = "lexicon"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val LESSON_ARG = "lessonId"
    const val HSK_LEVEL_ARG = "hskLevel"
    const val STEP_LESSON = "step_lesson/{lessonId}"
    const val LESSON = "lesson/{lessonId}"
    const val QUICK_REVIEW = "quick_review/{hskLevel}"

    fun stepLessonRoute(lessonId: String): String = "step_lesson/$lessonId"
    fun lessonRoute(lessonId: String): String = "lesson/$lessonId"
    fun quickReviewRoute(hskLevel: Int): String = "quick_review/$hskLevel"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = setOf(Routes.HOME, Routes.LEXICON, Routes.STATS, Routes.SETTINGS)
    val showBottomBar = currentRoute in topLevelRoutes

    val navigateToTab: (String) -> Unit = { targetRoute ->
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = { navigateToTab(Routes.HOME) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                            )
                        },
                        label = { Text(text = stringResource(R.string.nav_home)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.LEXICON,
                        onClick = { navigateToTab(Routes.LEXICON) },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                            )
                        },
                        label = { Text(text = stringResource(R.string.nav_lexicon)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.STATS,
                        onClick = { navigateToTab(Routes.STATS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                            )
                        },
                        label = { Text(text = stringResource(R.string.nav_stats)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navigateToTab(Routes.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                            )
                        },
                        label = { Text(text = stringResource(R.string.nav_settings)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                val viewModel: HomeViewModel = viewModel(
                    factory = container.homeViewModelFactory(),
                )
                HomeScreen(
                    viewModel = viewModel,
                    onOpenStepLesson = { lessonId ->
                        navController.navigate(Routes.stepLessonRoute(lessonId))
                    },
                    onStartLesson = { lessonId ->
                        navController.navigate(Routes.lessonRoute(lessonId))
                    },
                    onStartQuickReview = { hskLevel ->
                        navController.navigate(Routes.quickReviewRoute(hskLevel))
                    },
                )
            }

            composable(Routes.LEXICON) {
                val viewModel: LexiconViewModel = viewModel(
                    factory = container.lexiconViewModelFactory(),
                )
                LexiconScreen(viewModel = viewModel)
            }

            composable(Routes.STATS) {
                val viewModel: StatsViewModel = viewModel(
                    factory = container.statsViewModelFactory(),
                )
                StatsScreen(
                    viewModel = viewModel,
                    onOpenLevelInLexicon = { navigateToTab(Routes.LEXICON) },
                )
            }

            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(
                    factory = container.settingsViewModelFactory(),
                )
                SettingsScreen(viewModel = viewModel)
            }

            composable(
                route = Routes.STEP_LESSON,
                arguments = listOf(
                    navArgument(Routes.LESSON_ARG) { type = NavType.StringType },
                ),
            ) { entry ->
                val lessonId = entry.arguments?.getString(Routes.LESSON_ARG).orEmpty()
                val viewModel: StepLessonViewModel = viewModel(
                    factory = container.stepLessonViewModelFactory(lessonId = lessonId),
                )
                StepLessonScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onStartQuiz = { targetLessonId ->
                        navController.navigate(Routes.lessonRoute(targetLessonId)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                )
            }

            composable(
                route = Routes.LESSON,
                arguments = listOf(
                    navArgument(Routes.LESSON_ARG) { type = NavType.StringType },
                ),
            ) { entry ->
                val lessonId = entry.arguments?.getString(Routes.LESSON_ARG)
                val viewModel: LessonViewModel = viewModel(
                    factory = container.lessonViewModelFactory(
                        lessonId = lessonId,
                        hskLevel = 1,
                    ),
                )
                LessonScreen(
                    viewModel = viewModel,
                    onExitSession = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.QUICK_REVIEW,
                arguments = listOf(
                    navArgument(Routes.HSK_LEVEL_ARG) { type = NavType.IntType },
                ),
            ) { entry ->
                val hskLevel = entry.arguments?.getInt(Routes.HSK_LEVEL_ARG) ?: 1
                val viewModel: LessonViewModel = viewModel(
                    factory = container.lessonViewModelFactory(
                        lessonId = null,
                        hskLevel = hskLevel,
                    ),
                )
                LessonScreen(
                    viewModel = viewModel,
                    onExitSession = { navController.popBackStack() },
                )
            }
        }
    }
}
