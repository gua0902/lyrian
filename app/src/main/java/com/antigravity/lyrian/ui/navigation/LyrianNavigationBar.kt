package com.antigravity.lyrian.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * 底部導覽列（3 個目的地）。
 *
 * 依 MD3 規範：**選中的項目用 secondaryContainer 的圖示容器**呈現，
 * 未選中的保持低調。這是 NavigationBar 的預設行為，不需自訂顏色。
 *
 * 只在 compact 寬度使用；較寬的視窗應改用 NavigationRail（見 `LyrianApp`）。
 */
@Composable
fun LyrianNavigationBar(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                alwaysShowLabel = true,
            )
        }
    }
}

/**
 * 側邊導覽列（medium / expanded 寬度）。
 *
 * MD3 的適應性原則：手機用底部導覽，平板／摺疊機用 rail。
 */
@Composable
fun LyrianNavigationRail(
    current: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.NavigationRail(modifier = modifier) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            androidx.compose.material3.NavigationRailItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
