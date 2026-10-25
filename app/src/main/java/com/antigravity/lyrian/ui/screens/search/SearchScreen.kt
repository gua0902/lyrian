package com.antigravity.lyrian.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.remote.SongSearchResult
import com.antigravity.lyrian.ui.components.EmptyState
import com.antigravity.lyrian.ui.components.ErrorState
import com.antigravity.lyrian.ui.components.LoadingState
import com.antigravity.lyrian.ui.components.LyrianTopAppBar
import com.antigravity.lyrian.ui.theme.LyrianSpacing

/**
 * 首頁的搜尋（PRD v2 的 Home）。
 *
 * 版面：歌手／歌名輸入 → 搜尋鈕 → 「找到你的下一首」+ 歌曲數 → 歌曲卡片。
 *
 * ## 這一版拿掉了「選擇練習模式」
 *
 * 翻譯模式取消後只剩「單字填空」一種，再彈一個只有一張卡片的 Bottom Sheet
 * 只是多一次點擊。現在點歌**直接進練習頁**——這也是擁有者要的
 * 「不要留下無用的功能入口」。
 *
 * ## Top App Bar
 *
 * 用共用元件 [LyrianTopAppBar] 的「大標題會收合」版本：一進頁標題是主視覺，
 * 往下捲就縮成一般高度，把空間讓給搜尋結果。
 *
 * @param onSongSelected 點歌後的回呼（直接進練習）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSongSelected: (SongSearchResult) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LyrianTopAppBar(
                title = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = LyrianSpacing.screenHorizontal,
                end = LyrianSpacing.screenHorizontal,
                top = LyrianSpacing.sm,
                bottom = LyrianSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(LyrianSpacing.md),
        ) {
            item {
                SearchForm(
                    state = state,
                    onArtistChange = viewModel::onArtistChange,
                    onTrackChange = viewModel::onTrackChange,
                    onSearch = viewModel::search,
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
                state.isLoading -> item {
                    LoadingState(message = stringResource(R.string.search_loading))
                }

                state.errorMessage != null -> item {
                    ErrorState(
                        title = stringResource(R.string.search_error_generic),
                        description = state.errorMessage.orEmpty(),
                        onRetry = viewModel::search,
                    )
                }

                state.hasSearched && state.results.isEmpty() -> item {
                    EmptyState(
                        title = stringResource(R.string.search_empty_result),
                        description = stringResource(R.string.search_empty_result_hint),
                    )
                }

                !state.hasSearched -> item {
                    EmptyState(
                        title = stringResource(R.string.search_result_title),
                        description = stringResource(R.string.search_hint),
                    )
                }

                else -> items(
                    items = state.results,
                    key = { it.lrclibId },
                ) { song ->
                    SongCard(
                        song = song,
                        onClick = { onSongSelected(song) },
                        // 搜尋結果換一批時，卡片是淡入＋位移，不是瞬間跳出來
                        modifier = Modifier.animateItem(),
                    )
                }
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(LyrianSpacing.md)) {
        OutlinedTextField(
            value = state.artist,
            onValueChange = onArtistChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_artist_label)) },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraSmall,
        )

        OutlinedTextField(
            value = state.track,
            onValueChange = onTrackChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_track_label)) },
            leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraSmall,
        )

        Button(
            onClick = onSearch,
            enabled = state.canSearch,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(
                text = stringResource(R.string.action_search),
                modifier = Modifier.padding(start = LyrianSpacing.sm),
            )
        }
    }
}

@Composable
private fun ResultsHeader(count: Int, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LyrianSpacing.sm),
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
private fun SongCard(
    song: SongSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LyrianSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 封面：LRCLIB 沒有，來源是 iTunes
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium),
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
                    .padding(horizontal = LyrianSpacing.md),
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
                    contentDescription = stringResource(R.string.practice_start),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 組出「歌手 · 03:45」。 */
private fun buildMetaLine(song: SongSearchResult): String = buildString {
    append(song.artist)
    song.durationMs?.let { ms ->
        val totalSeconds = ms / 1000
        append(" · ")
        append("%02d:%02d".format(totalSeconds / 60, totalSeconds % 60))
    }
}
