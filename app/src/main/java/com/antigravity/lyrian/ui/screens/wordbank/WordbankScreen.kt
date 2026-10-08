package com.antigravity.lyrian.ui.screens.wordbank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.local.VocabEntity

/**
 * 字庫頁。
 *
 * 依 PRD v2 的「極簡」原則——**不要塞資訊**。
 * 每張卡片只有三行：
 *  1. 單字 +（未來的）CEFR 標籤 + 詞性
 *  2. 音標 + 簡短釋義
 *  3. 來源歌詞那一句（目標字以 primary 標色）
 *
 * 頂部顯示待複習數量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordbankScreen(viewModel: WordbankViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tab_wordbank)) })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryBanner(state) }

            item {
                FilterRow(
                    current = state.filter,
                    onChange = viewModel::onFilterChange,
                )
            }

            if (state.items.isEmpty()) {
                item { EmptyState(filter = state.filter) }
            } else {
                items(items = state.items, key = { it.id }) { vocab ->
                    VocabCard(vocab)
                }
            }
        }
    }
}

@Composable
private fun SummaryBanner(state: WordbankUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bookmarks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.wordbank_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(
                        R.string.wordbank_banner_subtitle,
                        state.total,
                        state.dueForReview,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterRow(current: WordbankFilter, onChange: (WordbankFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WordbankFilter.entries.forEach { filter ->
            FilterChip(
                selected = current == filter,
                onClick = { onChange(filter) },
                label = {
                    Text(
                        when (filter) {
                            WordbankFilter.ALL -> stringResource(R.string.wordbank_filter_all)
                            WordbankFilter.MASTERED -> stringResource(R.string.wordbank_filter_mastered)
                            WordbankFilter.REVIEWING -> stringResource(R.string.wordbank_filter_reviewing)
                        },
                    )
                },
            )
        }
    }
}

/**
 * 單字卡。
 *
 * 顯示 `surfaceContainerLow`——依 PRD 指定的色調層級。
 */
@Composable
private fun VocabCard(vocab: VocabEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 第一行：單字 + 詞性／CEFR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = vocab.surfaceForm ?: vocab.lemma,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // CEFR 標籤（接上 ECDICT 後才會有值）
                    vocab.cefr?.let { cefr ->
                        TagChip(text = cefr, emphasized = true)
                    }
                    vocab.partOfSpeech?.let { pos ->
                        TagChip(text = pos, emphasized = false)
                    }
                }
            }

            // 第二行：音標 + 簡短釋義
            val detail = listOfNotNull(vocab.phonetic, vocab.translation)
                .filter { it.isNotBlank() }
                .joinToString("  ")

            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // 第三行：來源歌詞
            vocab.contextLine?.let { context ->
                Text(
                    text = context,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            // 錯誤次數（答錯越多次越需要複習）
            if (vocab.errorCount > 1) {
                Text(
                    text = stringResource(R.string.wordbank_error_count, vocab.errorCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TagChip(text: String, emphasized: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyState(filter: WordbankFilter) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (filter == WordbankFilter.ALL) {
                stringResource(R.string.wordbank_empty)
            } else {
                stringResource(R.string.wordbank_empty_filtered)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
