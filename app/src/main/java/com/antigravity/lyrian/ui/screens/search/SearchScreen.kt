package com.antigravity.lyrian.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.remote.SongSearchResult
import com.antigravity.lyrian.domain.practice.PracticeMode

/**
 * 首頁的搜尋（PRD v2 的 Home）。
 *
 * 版面依 `UI/references/Lyrian · 搜尋.png`：
 * 歌手／歌名輸入 → 搜尋鈕 → 「找到你的下一首」+ 歌曲數 → 歌曲卡片。
 *
 * 另外有**手動輸入區**（模組 A 的兜底）：外呼被擋（503）或斷網時，
 * 貼上 YouTube 連結就能播、貼上歌詞就能練，不會整個 App 卡死。
 *
 * @param onSongSelected 點歌後的回呼（目前尚未接上模式選擇）
 * @param onManualPractice 手動輸入時的回呼（影片 id、歌詞、歌名）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSongSelected: (SongSearchResult, PracticeMode) -> Unit = { _, _ -> },
    onManualPractice: (videoId: String?, lyrics: String?, title: String) -> Unit = { _, _, _ -> },
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 點歌後先彈出模式選擇，而不是直接進練習
    var pendingSong by remember { mutableStateOf<SongSearchResult?>(null) }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SearchForm(
                    state = state,
                    onArtistChange = viewModel::onArtistChange,
                    onTrackChange = viewModel::onTrackChange,
                    onSearch = viewModel::search,
                )
            }

            // 手動兜底：預設收合，搜尋失敗時自動展開（見下方 expanded 的初值）
            item {
                ManualEntryCard(
                    expandedByDefault = state.hasSearched && state.results.isEmpty(),
                    onPlay = { video, title -> onManualPractice(video, null, title) },
                    onPractice = { lyrics, title -> onManualPractice(null, lyrics, title) },
                    onBoth = { video, lyrics, title -> onManualPractice(video, lyrics, title) },
                )
            }

            if (state.hasSearched) {
                item {
                    ResultsHeader(
                        count = state.results.size,
                        isLoading = state.isLoading,
                    )
                }
            }

            when {
                state.isLoading -> item { LoadingIndicator() }

                state.errorMessage != null -> item {
                    CenteredMessage(
                        text = state.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                state.hasSearched && state.results.isEmpty() -> item {
                    CenteredMessage(
                        text = stringResource(R.string.search_empty_result),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                !state.hasSearched -> item {
                    CenteredMessage(
                        text = stringResource(R.string.search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> items(
                    items = state.results,
                    key = { it.lrclibId },
                ) { song ->
                    SongCard(song = song, onClick = { pendingSong = song })
                }
            }
        }

        pendingSong?.let { song ->
            PracticeModeSheet(
                questionCount = song.blankCount,
                // BYOK 尚未實作，一律視為未設定——讓 UI 的停用狀態可以被驗證
                aiConfigured = false,
                onDismiss = { pendingSong = null },
                onSelect = { mode ->
                    pendingSong = null
                    onSongSelected(song, mode)
                },
            )
        }
    }
}

@Composable
private fun SearchForm(
    state: SearchUiState,
    onArtistChange: (String) -> Unit,
    onTrackChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.artist,
            onValueChange = onArtistChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_artist_label)) },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
        )

        OutlinedTextField(
            value = state.track,
            onValueChange = onTrackChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_track_label)) },
            leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
            singleLine = true,
        )

        androidx.compose.material3.Button(
            onClick = onSearch,
            enabled = state.canSearch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(
                text = stringResource(R.string.action_search),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ResultsHeader(count: Int, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.search_result_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!isLoading) {
            Text(
                text = stringResource(R.string.search_result_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SongCard(song: SongSearchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面：LRCLIB 沒有，來源是 iTunes
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (song.coverUrl != null) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildMetaLine(song),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.action_search),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 組出「歌手 · 03:45」與「10 個填空 · 流行」兩行。 */
private fun buildMetaLine(song: SongSearchResult): String = buildString {
    append(song.artist)
    song.durationMs?.let { ms ->
        val totalSeconds = ms / 1000
        append(" · ")
        append("%02d:%02d".format(totalSeconds / 60, totalSeconds % 60))
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    )
}

/**
 * 手動輸入區（模組 A 的兜底）。
 *
 * 兩個獨立入口，互不干擾：
 *  - 只有 YouTube 連結 → 進「只有播放器」模式
 *  - 有歌詞（LRC 或純文字）→ 進一般的填空練習
 *
 * 預設收合；搜尋失敗時自動展開，免得使用者還要去翻。
 */
@Composable
private fun ManualEntryCard(
    expandedByDefault: Boolean,
    onPlay: (video: String, title: String) -> Unit,
    onPractice: (lyrics: String, title: String) -> Unit,
    onBoth: (video: String, lyrics: String, title: String) -> Unit,
) {
    var expanded by remember(expandedByDefault) { mutableStateOf(expandedByDefault) }
    var video by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val noVideoError = stringResource(R.string.manual_error_no_video)
    val noLyricsError = stringResource(R.string.manual_error_no_lyrics)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.manual_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }

            if (!expanded) return@Column

            Text(
                text = stringResource(R.string.manual_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            OutlinedTextField(
                value = video,
                onValueChange = { video = it; error = null },
                label = { Text(stringResource(R.string.manual_youtube_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.manual_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            OutlinedTextField(
                value = lyrics,
                onValueChange = { lyrics = it; error = null },
                label = { Text(stringResource(R.string.manual_lyrics_label)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val id = ManualSongInput.extractVideoId(video)
                        if (id == null) error = noVideoError else onPlay(id, title)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.manual_play), style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        val id = ManualSongInput.extractVideoId(video)
                        when {
                            lyrics.isBlank() -> error = noLyricsError
                            id != null -> onBoth(id, lyrics, title)
                            else -> onPractice(lyrics, title)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.manual_practice), style = MaterialTheme.typography.labelMedium)
                }
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
