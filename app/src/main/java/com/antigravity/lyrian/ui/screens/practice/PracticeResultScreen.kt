package com.antigravity.lyrian.ui.screens.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.antigravity.lyrian.R
import com.antigravity.lyrian.domain.model.ResultWord

/** 延伸詞彙預設顯示幾條，其餘收在「展開」後面。 */
private const val EXTENSION_PREVIEW_COUNT = 8

/**
 * 練習結果頁（模組 2）。
 *
 * 區塊依擁有者指定：完成標題、歌曲資訊、統計、錯題、值得學的表達、延伸詞彙、底部操作。
 *
 * 兩份字表**每條只顯示 eng、詞性、中文釋義**三項（擁有者指定），
 * 不顯示例句、不顯示等級細節——那些在單字詳情頁。
 *
 * 統計全部來自 `PracticeUiState`（本次 session），**沒有新增任何資料表**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeResultScreen(
    state: PracticeUiState,
    onReplay: () -> Unit,
    onChangeSong: () -> Unit,
    onOpenWordbank: () -> Unit,
    onOpenWord: (String) -> Unit,
    onAddToWordbank: (ResultWord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var extensionExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = {
                    IconButton(onClick = onChangeSong) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            ResultActions(
                onReplay = onReplay,
                onChangeSong = onChangeSong,
                onOpenWordbank = onOpenWordbank,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SongSummary(
                    title = state.songTitle,
                    artist = state.artist,
                    coverUrl = state.coverUrl,
                )
            }

            item {
                StatsCard(
                    blanks = state.total,
                    correct = state.correctCount,
                    wrong = state.total - state.correctCount,
                    percent = state.percent,
                    elapsedMs = state.elapsedMs,
                )
            }

            // 片語／諺語區塊：本輪**不產生內容**，等 AiService 接上才顯示。
            // AI 未啟用時整塊隱藏（擁有者指定），所以現在不會出現在畫面上。
            if (state.aiConfigured) {
                item { SectionTitle(stringResource(R.string.result_accent_title)) }
                item {
                    PlaceholderCard(stringResource(R.string.result_accent_empty))
                }
            }

            item { SectionTitle(stringResource(R.string.result_wrong_title)) }

            if (state.wrongWords.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.result_wrong_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(items = state.wrongWords, key = { "wrong-${it.word}" }) { word ->
                    WordRow(
                        word = word,
                        onClick = { onOpenWord(word.word) },
                        onAdd = { onAddToWordbank(word) },
                        // 換頁回來或加入字庫時，列的高度/位置是滑順變化的
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item {
                SectionTitle(
                    title = stringResource(R.string.result_extend_title),
                    hint = stringResource(R.string.result_extend_hint, state.difficulty.label),
                )
            }

            if (state.extensionWords.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.result_extend_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val visible = if (extensionExpanded) {
                    state.extensionWords
                } else {
                    state.extensionWords.take(EXTENSION_PREVIEW_COUNT)
                }
                items(items = visible, key = { "ext-${it.word}" }) { word ->
                    WordRow(
                        word = word,
                        onClick = { onOpenWord(word.word) },
                        onAdd = { onAddToWordbank(word) },
                        modifier = Modifier.animateItem(),
                    )
                }

                if (state.extensionWords.size > EXTENSION_PREVIEW_COUNT) {
                    item {
                        val hidden = state.extensionWords.size - EXTENSION_PREVIEW_COUNT
                        TextButton(onClick = { extensionExpanded = !extensionExpanded }) {
                            Icon(
                                imageVector = if (extensionExpanded) {
                                    Icons.Filled.ExpandLess
                                } else {
                                    Icons.Filled.ExpandMore
                                },
                                contentDescription = null,
                            )
                            Text(
                                text = if (extensionExpanded) {
                                    stringResource(R.string.result_extend_less)
                                } else {
                                    stringResource(R.string.result_extend_more, hidden)
                                },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongSummary(title: String, artist: String, coverUrl: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsCard(
    blanks: Int,
    correct: Int,
    wrong: Int,
    percent: Int,
    elapsedMs: Long?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(stringResource(R.string.result_stat_blanks), "$blanks", Modifier.weight(1f))
                StatCell(stringResource(R.string.result_stat_correct), "$correct", Modifier.weight(1f))
                StatCell(stringResource(R.string.result_stat_wrong), "$wrong", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(stringResource(R.string.result_stat_accuracy), "$percent%", Modifier.weight(1f))
                StatCell(
                    label = stringResource(R.string.result_stat_duration),
                    value = formatDuration(elapsedMs),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 用時：不到一分鐘只顯示秒數，之後才顯示「N 分 M 秒」。 */
@Composable
private fun formatDuration(elapsedMs: Long?): String {
    if (elapsedMs == null) return "—"
    val seconds = elapsedMs / 1000
    return if (seconds < 60) {
        stringResource(R.string.result_duration_seconds, seconds.toInt())
    } else {
        stringResource(R.string.result_duration_format, (seconds / 60).toInt(), (seconds % 60).toInt())
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceholderCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * 一份字表的單列：**只有 eng、詞性、中文釋義**（擁有者指定的三項）。
 *
 * 點擊進入單字詳情（[onClick]）。右側的「＋」是模組 D.3 的手動加入——
 * A1/A2 的字不會自動入庫，使用者要就得自己按。整列可點，最小高度 48dp。
 */
@Composable
private fun WordRow(
    word: ResultWord,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = word.word,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (!word.partOfSpeech.isNullOrBlank()) {
            Text(
                text = word.partOfSpeech,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = word.meaning ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )

        if (word.addedToWordbank) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.result_added_to_wordbank),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(20.dp),
            )
        } else {
            IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.result_add_to_wordbank),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultActions(
    onReplay: () -> Unit,
    onChangeSong: () -> Unit,
    onOpenWordbank: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onReplay, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.result_action_again))
            }
            OutlinedButton(onClick = onChangeSong, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.result_action_change_song))
            }
        }
        TextButton(onClick = onOpenWordbank, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.result_action_wordbank))
        }
    }
}
