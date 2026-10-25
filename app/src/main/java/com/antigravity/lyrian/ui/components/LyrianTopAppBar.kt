package com.antigravity.lyrian.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * 頂層頁面（首頁／字庫／我的）共用的 Top App Bar。
 *
 * ## 為什麼是「大標題會收合」的那一種
 *
 * M3 Expressive 用**大標題 + 捲動收合**建立視覺層次：頁面一進來標題是
 * 主視覺，往下捲之後縮成一般高度，把空間讓給內容。三個頂層頁面都是
 * 「清單為主」，正好適用；設定子頁面則是「單一任務」，維持小高度即可。
 *
 * ## 按鈕與色彩的對齊
 *
 * - 容器色用 `surface`，捲動後換成 `surfaceContainer`（色調分層，不用陰影）
 * - 標題用 `titleLarge`，副標用 `bodySmall`／`onSurfaceVariant`
 * - 動作區（actions）由呼叫端提供，通常是 `IconButton`
 *
 * ## 版本註記
 *
 * 這裡刻意用 `TopAppBar` 家族的 `MediumTopAppBar`——`material3 1.4.0` 的
 * `MediumFlexibleTopAppBar`（M3E 的可調高度版本）與 `MaterialExpressiveTheme`
 * 一樣仍是 `internal`，`kotlinc` 直接拒絕。等 1.5 穩定版再換，不先自己模仿。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyrianTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )

    if (scrollBehavior != null) {
        androidx.compose.material3.MediumTopAppBar(
            title = { TitleBlock(title, subtitle) },
            modifier = modifier,
            colors = colors,
            scrollBehavior = scrollBehavior,
            actions = actions,
        )
    } else {
        androidx.compose.material3.TopAppBar(
            title = { TitleBlock(title, subtitle) },
            modifier = modifier,
            colors = colors,
            actions = actions,
        )
    }
}

@Composable
private fun TitleBlock(title: String, subtitle: String?) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
