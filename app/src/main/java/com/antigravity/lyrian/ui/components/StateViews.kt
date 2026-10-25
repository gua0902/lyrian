package com.antigravity.lyrian.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.lyrian.R
import androidx.compose.ui.res.stringResource

/**
 * 共用的狀態畫面：**Loading / Empty / Error**。
 *
 * ## 為什麼要抽出來
 *
 * 之前每個畫面自己寫一份「置中文字」或「轉圈」：搜尋頁、字庫頁、單字頁
 * 的字級、間距、顏色都不一樣。M3 的要求不是「有轉圈就好」，
 * 而是**同一個狀態在任何頁面看起來一樣**：
 *
 * ```
 * Loading  轉圈 36dp + 一句話（bodyMedium / onSurfaceVariant）
 * Empty    標題（titleMedium）+ 說明（bodySmall）+ 選用動作（TextButton）
 * Error    標題用 error 色 + 說明 + 重試按鈕
 * ```
 *
 * 全部置中、垂直間距 8 / 16dp、左右留 24dp——對齊 M3 的 8dp 間距系統。
 * 轉圈用 `CircularProgressIndicator`：`material3 1.4.0` 還沒有公開的
 * `LoadingIndicator`（那是 1.5 的 alpha API），所以**不假裝**用了新元件。
 */
@Composable
fun LoadingState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 空狀態：一句標題 + 一句說明（+ 可選動作）。 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    StateBody(
        title = title,
        description = description,
        titleColor = MaterialTheme.colorScheme.onSurface,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier,
    )
}

/** 錯誤狀態：標題用 `error` 色，並提供重試。 */
@Composable
fun ErrorState(
    title: String,
    description: String,
    retryLabel: String = stringResource(R.string.action_retry),
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    StateBody(
        title = title,
        description = description,
        titleColor = MaterialTheme.colorScheme.error,
        actionLabel = if (onRetry != null) retryLabel else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
private fun StateBody(
    title: String,
    description: String,
    titleColor: androidx.compose.ui.graphics.Color,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
                Text(actionLabel)
            }
        }
    }
}
