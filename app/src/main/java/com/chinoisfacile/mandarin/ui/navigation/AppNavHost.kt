package com.chinoisfacile.mandarin.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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

    val TOP_LEVEL_ROUTES = setOf(HOME, LEXICON, STATS, SETTINGS)

    fun stepLessonRoute(lessonId: String): String = "step_lesson/$lessonId"
    fun lessonRoute(lessonId: String): String = "lesson/$lessonId"
    fun quickReviewRoute(hskLevel: Int): String = "quick_review/$hskLevel"
}

private const val SECONDARY_ENTER_MS = 200
private const val SECONDARY_EXIT_MS = 180

@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.TOP_LEVEL_ROUTES

    val activityStoreOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val homeFactory = remember(container) { container.homeViewModelFactory() }
    val lexiconFactory = remember(container) { container.lexiconViewModelFactory() }
    val statsFactory = remember(container) { container.statsViewModelFactory() }
    val settingsFactory = remember(container) { container.settingsViewModelFactory() }

    val navigateToTab: (String) -> Unit = remember(navController) {
        { targetRoute ->
            if (navController.currentBackStackEntry?.destination?.route != targetRoute) {
                navController.navigate(targetRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
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
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                val viewModel: HomeViewModel = viewModel(
                    viewModelStoreOwner = activityStoreOwner,
                    factory = homeFactory,
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
                    viewModelStoreOwner = activityStoreOwner,
                    factory = lexiconFactory,
                )
                LexiconScreen(viewModel = viewModel)
            }

            composable(Routes.STATS) {
                val viewModel: StatsViewModel = viewModel(
                    viewModelStoreOwner = activityStoreOwner,
                    factory = statsFactory,
                )
                StatsScreen(
                    viewModel = viewModel,
                    onOpenLevelInLexicon = { navigateToTab(Routes.LEXICON) },
                    onStartQuickReview = { hskLevel ->
                        navController.navigate(Routes.quickReviewRoute(hskLevel))
                    },
                )
            }

            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(
                    viewModelStoreOwner = activityStoreOwner,
                    factory = settingsFactory,
                )
                SettingsScreen(viewModel = viewModel)
            }

            composable(
                route = Routes.STEP_LESSON,
                arguments = listOf(
                    navArgument(Routes.LESSON_ARG) { type = NavType.StringType },
                ),
                enterTransition = {
                    fadeIn(tween(SECONDARY_ENTER_MS)) +
                        slideInHorizontally(tween(SECONDARY_ENTER_MS)) { it / 12 }
                },
                exitTransition = { fadeOut(tween(SECONDARY_EXIT_MS)) },
                popEnterTransition = { fadeIn(tween(SECONDARY_EXIT_MS)) },
                popExitTransition = {
                    fadeOut(tween(SECONDARY_EXIT_MS)) +
                        slideOutHorizontally(tween(SECONDARY_EXIT_MS)) { it / 12 }
                },
            ) { entry ->
                val lessonId = entry.arguments?.getString(Routes.LESSON_ARG).orEmpty()
                val factory = remember(container, lessonId) {
                    container.stepLessonViewModelFactory(lessonId = lessonId)
                }
                val viewModel: StepLessonViewModel = viewModel(factory = factory)
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
                enterTransition = {
                    fadeIn(tween(SECONDARY_ENTER_MS)) +
                        slideInHorizontally(tween(SECONDARY_ENTER_MS)) { it / 12 }
                },
                exitTransition = { fadeOut(tween(SECONDARY_EXIT_MS)) },
                popEnterTransition = { fadeIn(tween(SECONDARY_EXIT_MS)) },
                popExitTransition = {
                    fadeOut(tween(SECONDARY_EXIT_MS)) +
                        slideOutHorizontally(tween(SECONDARY_EXIT_MS)) { it / 12 }
                },
            ) { entry ->
                val lessonId = entry.arguments?.getString(Routes.LESSON_ARG)
                val factory = remember(container, lessonId) {
                    container.lessonViewModelFactory(
                        lessonId = lessonId,
                        hskLevel = 1,
                    )
                }
                val viewModel: LessonViewModel = viewModel(factory = factory)
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
                enterTransition = {
                    fadeIn(tween(SECONDARY_ENTER_MS)) +
                        slideInHorizontally(tween(SECONDARY_ENTER_MS)) { it / 12 }
                },
                exitTransition = { fadeOut(tween(SECONDARY_EXIT_MS)) },
                popEnterTransition = { fadeIn(tween(SECONDARY_EXIT_MS)) },
                popExitTransition = {
                    fadeOut(tween(SECONDARY_EXIT_MS)) +
                        slideOutHorizontally(tween(SECONDARY_EXIT_MS)) { it / 12 }
                },
            ) { entry ->
                val hskLevel = entry.arguments?.getInt(Routes.HSK_LEVEL_ARG) ?: 1
                val factory = remember(container, hskLevel) {
                    container.lessonViewModelFactory(
                        lessonId = null,
                        hskLevel = hskLevel,
                    )
                }
                val viewModel: LessonViewModel = viewModel(factory = factory)
                LessonScreen(
                    viewModel = viewModel,
                    onExitSession = { navController.popBackStack() },
                )
            }
        }
    }
}
