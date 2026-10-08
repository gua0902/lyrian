package com.antigravity.lyrian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.lyrian.R

/**
 * 三個 Tab 的暫用實作。
 *
 * 刻意顯示「即將推出」而非空白畫面——空白看起來像壞掉。
 * 這些會被依序替換為真正的畫面：
 *  1. Home（搜尋 + 模式選擇）
 *  2. Wordbank（字庫列表）
 *  3. My（統計 + 設定入口）
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScaffold(title: String, body: String) {
    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SearchScreenPlaceholder() {
    PlaceholderScaffold(
        title = stringResource(R.string.tab_home),
        body = stringResource(R.string.placeholder_search),
    )
}

@Composable
fun WordbankScreenPlaceholder() {
    PlaceholderScaffold(
        title = stringResource(R.string.tab_wordbank),
        body = stringResource(R.string.placeholder_wordbank),
    )
}

@Composable
fun MyScreenPlaceholder() {
    PlaceholderScaffold(
        title = stringResource(R.string.tab_my),
        body = stringResource(R.string.placeholder_my),
    )
}
