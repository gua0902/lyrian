package com.antigravity.lyrian.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import com.antigravity.lyrian.ui.screens.MyScreenPlaceholder
import com.antigravity.lyrian.ui.screens.WordbankScreenPlaceholder
import com.antigravity.lyrian.ui.screens.practice.PracticeScreen
import com.antigravity.lyrian.ui.screens.search.SearchScreen

/**
 * App 的根 Composable。
 *
 * 導覽結構：
 *  - 三個頂層目的地（首頁／字庫／我的）共用同一個 Scaffold 與導覽列
 *  - 寬度 >= Medium 時自動改用 [LyrianNavigationRail]（MD3 適應性原則）
 *  - 練習頁是**全螢幕**的（不顯示導覽列），因為它需要完整的垂直空間
 */
@Composable
fun LyrianApp(
    windowSizeClass: WindowSizeClass?,
    sessionStore: PracticeSessionStore,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    // 練習頁是全螢幕的，不顯示導覽列
    val isFullScreen = currentRoute?.startsWith("practice/") == true

    val currentTopLevel = TopLevelDestination.entries.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    val useRail = !isFullScreen && when (windowSizeClass?.widthSizeClass) {
        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> true
        else -> false
    }

    val navigateToTopLevel: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            // 切換 Tab 時不堆疊：回到起始目的地並保留各 Tab 的狀態
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
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.START.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(TopLevelDestination.HOME.route) {
                    SearchScreen(
                        onSongSelected = { song, mode ->
                            // 把手上的歌詞交給練習頁，避免二次請求
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

                composable(TopLevelDestination.WORDBANK.route) { WordbankScreenPlaceholder() }
                composable(TopLevelDestination.MY.route) { MyScreenPlaceholder() }

                composable(
                    route = Routes.PRACTICE,
                    arguments = listOf(
                        navArgument(Routes.ARG_SONG_ID) { type = NavType.StringType },
                    ),
                ) {
                    PracticeScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
