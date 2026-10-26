package com.antigravity.lyrian.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.antigravity.lyrian.data.active.PracticeSessionStore
import com.antigravity.lyrian.ui.navigation.LyrianNavigationBar
import com.antigravity.lyrian.ui.navigation.LyrianNavigationRail
import com.antigravity.lyrian.ui.navigation.Routes
import com.antigravity.lyrian.ui.navigation.TopLevelDestination
import com.antigravity.lyrian.ui.theme.LyrianMotion
import com.antigravity.lyrian.ui.theme.SystemBarIcons
import com.antigravity.lyrian.ui.screens.my.MyScreen
import com.antigravity.lyrian.ui.screens.onboarding.OnboardingScreen
import com.antigravity.lyrian.ui.screens.practice.PracticeScreen
import com.antigravity.lyrian.ui.screens.search.SearchScreen
import com.antigravity.lyrian.ui.screens.settings.AboutScreen
import com.antigravity.lyrian.ui.screens.settings.AppearanceSettingsScreen
import com.antigravity.lyrian.ui.screens.settings.DataSettingsScreen
import com.antigravity.lyrian.ui.screens.settings.PlaybackSettingsScreen
import com.antigravity.lyrian.ui.screens.settings.SettingsScreen
import com.antigravity.lyrian.ui.screens.wordbank.WordbankScreen

/**
 * App 的根 Composable。
 *
 * 導覽結構：
 *  - **Onboarding**：首次啟動時顯示（`startDestination` 決定），可全部略過
 *  - 三個頂層目的地（首頁／字庫／我的）共用同一個 Scaffold 與導覽列
 *  - 寬度 >= Medium 時自動改用 [LyrianNavigationRail]（MD3 適應性原則）
 *  - 練習頁與設定頁是**全螢幕**的（不顯示導覽列）
 *
 * ## 轉場
 *
 * 前進／返回都用「淡入淡出 + 畫面寬度 1/12 的水平位移」：
 *  - 位移很小，但在慢速滑動時看得出方向（誰蓋在誰上面）
 *  - 用 M3 的 emphasized 曲線，不是預設的線性
 *  - 出場比進場快（200ms vs 400ms），這是 M3 的標準比例
 */
@Composable
fun LyrianApp(
    windowSizeClass: WindowSizeClass?,
    sessionStore: PracticeSessionStore,
    onboardingCompleted: Boolean,
    onOnboardingCompleted: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    // 這些畫面是全螢幕的，不顯示底部導覽
    val isFullScreen = currentRoute == Routes.ONBOARDING ||
        currentRoute == Routes.ABOUT ||
        currentRoute?.startsWith("practice/") == true ||
        currentRoute?.startsWith("settings") == true ||
        currentRoute?.startsWith("vocab/") == true ||
        currentRoute?.startsWith("word/") == true

    /**
     * 練習頁的背景是「專輯封面 + 模糊」，永遠偏暗，因此狀態列圖示要固定用淺色，
     * 否則淺色主題下會變成深色圖示畫在深色背景上——完全看不見。
     * 其他頁面沿用主題（深色主題才用淺色圖示）。
     */
    val isPracticeRoute = currentRoute?.startsWith("practice/") == true
    SystemBarIcons(lightIcons = isPracticeRoute || isSystemInDarkTheme())

    val currentTopLevel = TopLevelDestination.entries.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    val useRail = !isFullScreen && when (windowSizeClass?.widthSizeClass) {
        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> true
        else -> false
    }

    val navigateToTopLevel: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            LyrianNavigationRail(current = currentTopLevel, onSelect = navigateToTopLevel)
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail && !isFullScreen) {
                    LyrianNavigationBar(current = currentTopLevel, onSelect = navigateToTopLevel)
                }
            },
            // 全螢幕頁面**不套用系統列內距**：
            //  - 練習頁要讓模糊背景一路畫到螢幕最頂端（狀態列圖示浮在圖上）
            //  - 設定／詳情頁自己有 TopAppBar，M3 的 TopAppBar 已經會處理 statusBars
            //
            // ⚠️ 之前這裡是預設值，於是全螢幕頁被往下推了一個狀態列的高度，
            // 頂端露出 Scaffold 的底色——那就是使用者回報的「頂部黑條」。
            // 而且設定頁還會被 TopAppBar 再推一次，形成兩倍空白。
            contentWindowInsets = if (isFullScreen) {
                WindowInsets(0)
            } else {
                ScaffoldDefaults.contentWindowInsets
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (onboardingCompleted) {
                    TopLevelDestination.START.route
                } else {
                    Routes.ONBOARDING
                },
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    fadeIn(animationSpec = LyrianMotion.enter()) +
                        slideInHorizontally(animationSpec = LyrianMotion.enter()) { it / 12 }
                },
                exitTransition = {
                    fadeOut(animationSpec = LyrianMotion.exit()) +
                        slideOutHorizontally(animationSpec = LyrianMotion.exit()) { -it / 12 }
                },
                popEnterTransition = {
                    fadeIn(animationSpec = LyrianMotion.enter()) +
                        slideInHorizontally(animationSpec = LyrianMotion.enter()) { -it / 12 }
                },
                popExitTransition = {
                    fadeOut(animationSpec = LyrianMotion.exit()) +
                        slideOutHorizontally(animationSpec = LyrianMotion.exit()) { it / 12 }
                },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onFinish = {
                            onOnboardingCompleted()
                            navController.navigate(TopLevelDestination.START.route) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                        onSetupAi = {
                            onOnboardingCompleted()
                            navController.navigate(Routes.SETTINGS_AI) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }

                composable(TopLevelDestination.HOME.route) {
                    SearchScreen(
                        onSongSelected = { song ->
                            sessionStore.put(
                                PracticeSessionStore.Session(
                                    songId = song.lrclibId,
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    durationMs = song.durationMs,
                                    coverUrl = song.coverUrl,
                                    genre = song.genre,
                                    lyricsFile = song.lyricsFile,
                                    syncedLyrics = song.syncedLyrics,
                                    plainLyrics = song.plainLyrics,
                                ),
                            )
                            navController.navigate(Routes.practice(song.lrclibId))
                        },
                    )
                }

                composable(TopLevelDestination.WORDBANK.route) {
                    WordbankScreen(
                        onOpenDetail = { vocabId ->
                            navController.navigate(Routes.vocabDetail(vocabId))
                        },
                    )
                }

                composable(
                    route = Routes.VOCAB_DETAIL,
                    arguments = listOf(
                        navArgument(Routes.ARG_VOCAB_ID) { type = NavType.StringType },
                    ),
                ) {
                    com.antigravity.lyrian.ui.screens.wordbank.VocabDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSong = { songId -> navController.navigate(Routes.practice(songId)) },
                    )
                }

                composable(TopLevelDestination.MY.route) {
                    MyScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }

                composable(
                    route = Routes.PRACTICE,
                    arguments = listOf(
                        navArgument(Routes.ARG_SONG_ID) { type = NavType.StringType },
                    ),
                ) {
                    PracticeScreen(
                        onBack = { navController.popBackStack() },
                        onOpenWordbank = {
                            // 結果頁的「查看字庫」要真的跳到字庫，而不是留在練習頁
                            navController.navigate(TopLevelDestination.WORDBANK.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenWord = { word -> navController.navigate(Routes.wordDetail(word)) },
                    )
                }

                composable(
                    route = Routes.WORD_DETAIL,
                    arguments = listOf(
                        navArgument(Routes.ARG_WORD) { type = NavType.StringType },
                    ),
                ) {
                    com.antigravity.lyrian.ui.screens.wordbank.WordDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSong = { songId -> navController.navigate(Routes.practice(songId)) },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                        onOpenPlayback = { navController.navigate(Routes.SETTINGS_PLAYBACK) },
                        onOpenAiSettings = { navController.navigate(Routes.SETTINGS_AI) },
                        onOpenData = { navController.navigate(Routes.SETTINGS_DATA) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    )
                }

                composable(Routes.SETTINGS_APPEARANCE) {
                    AppearanceSettingsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SETTINGS_PLAYBACK) {
                    PlaybackSettingsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SETTINGS_AI) {
                    com.antigravity.lyrian.ui.screens.settings.AiSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.SETTINGS_DATA) {
                    DataSettingsScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
