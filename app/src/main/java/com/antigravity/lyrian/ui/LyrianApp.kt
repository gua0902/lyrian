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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.antigravity.lyrian.ui.navigation.LyrianNavigationBar
import com.antigravity.lyrian.ui.navigation.LyrianNavigationRail
import com.antigravity.lyrian.ui.navigation.TopLevelDestination
import com.antigravity.lyrian.ui.screens.MyScreenPlaceholder
import com.antigravity.lyrian.ui.screens.WordbankScreenPlaceholder
import com.antigravity.lyrian.ui.screens.search.SearchScreen

/**
 * App 的根 Composable。
 *
 * 導覽結構：
 *  - 三個頂層目的地（首頁／字庫／我的）共用同一個 Scaffold 與導覽列
 *  - 寬度 >= Medium 時自動改用 [LyrianNavigationRail]（MD3 適應性原則）
 *  - 非頂層畫面（練習頁、設定頁）之後會用獨立的 NavHost 分支處理
 */
@Composable
fun LyrianApp(windowSizeClass: WindowSizeClass?) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val currentTopLevel = TopLevelDestination.entries.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    val useRail = when (windowSizeClass?.widthSizeClass) {
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
                if (!useRail) {
                    LyrianNavigationBar(current = currentTopLevel, onSelect = navigateToTopLevel)
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.START.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(TopLevelDestination.HOME.route) { SearchScreen() }
                composable(TopLevelDestination.WORDBANK.route) { WordbankScreenPlaceholder() }
                composable(TopLevelDestination.MY.route) { MyScreenPlaceholder() }
            }
        }
    }
}
