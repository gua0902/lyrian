package com.antigravity.lyrian.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.antigravity.lyrian.R
import com.antigravity.lyrian.ui.theme.LyrianMotion

/**
 * 設定子頁面共用的骨架。
 *
 * ## 為什麼每一頁都長一樣
 *
 * M3 的設定頁不是「表單」，而是**分組的清單**：
 * ```
 * 區段標題（labelLarge / primary）
 * ┌───────────────────────────┐  ← Surface，圓角 12
 * │ 項目 │ 說明 │ 控制項      │
 * ├───────────────────────────┤  ← HorizontalDivider（outlineVariant）
 * │ 項目 │ 說明 │ 控制項      │
 * └───────────────────────────┘
 * ```
 * 一致的分組讓使用者不必重新學一次版面，也讓「哪一頁有什麼」變得可預期。
 *
 * 每一頁都自己帶 `SnackbarHostState`：清除資料、重設偏移的結果要**留在那一頁**
 * 顯示，不能跑到別的畫面去。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content,
    )
}

/** 設定頁的清單本體：一致的間距與左右留白。 */
@Composable
fun SettingsList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * 一個區段：標題 + 圓角容器。
 *
 * 容器顏色用 `surfaceContainerLow`：比 background 亮一階，但不像卡片那麼有存在感。
 * 這是 M3 對「設定群組」的建議——它是背景的一部分，不是可點擊的卡片。
 */
@Composable
fun SettingsSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
            )
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                // 內容變多（例如展開說明）時高度是滑順長出來的，不是跳一格
                .animateContentSize(animationSpec = LyrianMotion.emphasized()),
        ) {
            Column(content = content)
        }
    }
}

/** 群組內的分隔線。用 `outlineVariant`（裝飾性），不是 `outline`。 */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 開關列：整列可點（點文字等於切換），並保留 48dp 的觸控高度。 */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
    )
}

/** 動作列（清除、重設、進入子頁）。`destructive` 用 error 色，只用在真的不可逆的操作。 */
@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailingChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    ListItem(
        headlineContent = { Text(title, color = contentColor) },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    color = if (destructive && enabled) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        leadingContent = icon?.let {
            { Icon(imageVector = it, contentDescription = null, tint = contentColor) }
        },
        trailingContent = if (trailingChevron) {
            {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = contentColor,
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** 唯讀的資訊列（版本、檔名、統計）。 */
@Composable
fun SettingsValueRow(
    title: String,
    value: String,
    subtitle: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** 說明文字（區段底下的小字）。 */
@Composable
fun SettingsHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
    )
}

/**
 * 確認對話框。
 *
 * 破壞性操作**一定要**走這裡：對話框的形狀是 `extraLarge`（28dp），
 * 由 `MaterialTheme.shapes` 提供，不必自己指定。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
