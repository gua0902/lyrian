package com.antigravity.lyrian.ui.screens.wordbank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.dictionary.DictionaryEntry
import com.antigravity.lyrian.data.dictionary.DictionaryRepository
import com.antigravity.lyrian.data.dictionary.DictionaryResult
import com.antigravity.lyrian.data.dictionary.DictionarySense
import com.antigravity.lyrian.data.local.SongDao
import com.antigravity.lyrian.data.local.VocabDao
import com.antigravity.lyrian.data.tts.Speaker
import com.antigravity.lyrian.domain.model.CefrLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 歌詞例句。 */
data class WordExample(
    val line: String,
    val songTitle: String,
    /** 要跳回那首歌需要的路由鍵（LRCLIB id）；null 表示這首歌不能跳 */
    val songLrclibId: Long?,
)

/** 單字詳情的 UI 狀態。 */
data class WordDetailUiState(
    val word: String = "",
    val isLoading: Boolean = true,
    val entry: DictionaryEntry? = null,
    val example: WordExample? = null,
    val notFound: Boolean = false,
    val error: String? = null,
)

/**
 * 單字詳情頁（模組 F）。
 *
 * ## 版面（由上而下，擁有者指定）
 *
 * 1. 頂部：單詞（大字）、CEFR 徽章（右上）、音標、TTS 朗讀
 * 2. 釋義：**按詞性分組**，詞性做成左側小徽章；`[醫]` 這類領域標註抽成灰色小標
 * 3. 詞形變化：`過去式 passed / 現在分詞 passing`，沒有資料整區隱藏
 * 4. 歌詞例句：原句 + 出自哪首歌，可點回那首歌
 * 5. 沒有「加入字庫」按鈕（字庫入庫在別處決定）
 *
 * 不顯示熟練度、複習次數、柯林斯星級、考試標籤——擁有者明確要求。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    onBack: () -> Unit,
    onOpenSong: (Long) -> Unit,
    viewModel: WordDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wordbank_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        WordDetailBody(
            state = state,
            onSpeak = viewModel::speak,
            onOpenSong = onOpenSong,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * 詳情頁的本體。`word/{word}` 與字庫的 `vocab/{id}` 共用同一份——
 * 擁有者要求「結果頁與 Word Detail 共用同一套查詢與快取邏輯」。
 */
@Composable
fun WordDetailBody(
    state: WordDetailUiState,
    onSpeak: () -> Unit,
    onOpenSong: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Headline(word = state.word, level = state.entry?.level, onSpeak = onSpeak, entry = state.entry)

        when {
            state.error != null -> Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            state.notFound -> Text(
                text = stringResource(R.string.word_detail_not_found_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                val senses = state.entry?.senses.orEmpty()
                if (senses.isNotEmpty()) {
                    SenseSection(senses)
                }

                val forms = state.entry?.forms.orEmpty()
                if (forms.isNotEmpty()) {
                    FormSection(forms.map { it.label to it.word })
                }
            }
        }

        state.example?.let { example ->
            ExampleSection(example = example, onOpenSong = onOpenSong)
        }
    }
}

/** 區塊標題。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Headline(
    word: String,
    level: CefrLevel?,
    entry: DictionaryEntry?,
    onSpeak: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry?.matchedForm?.takeIf { it != word && entry.level != null } ?: word,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (entry?.matchedForm != null && entry.matchedForm != word) {
                        // `silhouettes` 查到的其實是 `silhouette`，要講清楚
                        Text(
                            text = prettyFormsHint(word, entry.matchedForm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    entry?.phonetic?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                LevelBadge(level = level)
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.wordbank_detail_pronounce),
                    )
                }
                Text(
                    text = stringResource(R.string.wordbank_detail_pronounce),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

/** 例如「silhouettes 的字典條目」。 */
private fun prettyFormsHint(surface: String, matched: String): String =
    if (surface.equals(matched, ignoreCase = true)) "" else "$surface → $matched"

/** CEFR 徽章（右上角）。 */
@Composable
private fun LevelBadge(level: CefrLevel?) {
    if (level == null) return
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        contentColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = level.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * 釋義區：**按詞性分組**。
 *
 * 原始資料長這樣（一行一個詞性）：
 * ```
 * n. 希望, 信心, 期待
 * v. 希望, 期望, 信賴
 * ```
 * 詞性不寫進句子裡，而是當左側徽章；`[醫]` 這種領域標註抽出來變灰色小字。
 */
@Composable
private fun SenseSection(senses: List<DictionarySense>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.wordbank_detail_meaning))
        senses.forEach { sense ->
            Row(verticalAlignment = Alignment.Top) {
                PosBadge(sense.partOfSpeech)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = sense.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (sense.domains.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            sense.domains.forEach { domain ->
                                Text(
                                    text = "［$domain］",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosBadge(pos: String?) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = pos ?: "—",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 詞形變化：`過去式 passed`、`現在分詞 passing`…。 */
@Composable
private fun FormSection(forms: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(stringResource(R.string.word_detail_forms))
        Text(
            text = forms.joinToString(" / ") { (label, word) -> "$label $word" },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 歌詞例句：原句 + 出自哪首歌，可點回那首歌。 */
@Composable
private fun ExampleSection(example: WordExample, onOpenSong: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(stringResource(R.string.word_detail_examples))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = example.line, style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.word_detail_from_song, example.songTitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    example.songLrclibId?.let { id ->
                        TextButton(onClick = { onOpenSong(id) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.word_detail_open_song),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * `word/{word}` 的 ViewModel：字典查詢 + 找例句。
 *
 * 例句來源是字庫裡的 `contextLine`（B1 以上的字會自動入庫，所以多數情況有）。
 * 沒有語境就整區隱藏——不編造例句。
 */
@HiltViewModel
class WordDetailViewModel @Inject constructor(
    private val dictionary: DictionaryRepository,
    private val vocabDao: VocabDao,
    private val songDao: SongDao,
    private val speaker: Speaker,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rawWord: String = savedStateHandle.get<String>(ARG_WORD).orEmpty()

    private val _uiState = MutableStateFlow(WordDetailUiState(word = rawWord))
    val uiState: StateFlow<WordDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val example = runCatching { findExample(rawWord) }.getOrNull()

            when (val result = dictionary.lookup(rawWord)) {
                is DictionaryResult.Found -> _uiState.value = WordDetailUiState(
                    word = rawWord,
                    isLoading = false,
                    entry = result.entry,
                    example = example,
                )

                is DictionaryResult.NotFound -> _uiState.value = WordDetailUiState(
                    word = rawWord,
                    isLoading = false,
                    notFound = true,
                    example = example,
                )

                is DictionaryResult.Failed -> _uiState.value = WordDetailUiState(
                    word = rawWord,
                    isLoading = false,
                    error = result.reason,
                )
            }
        }
    }

    fun speak() = speaker.speak(rawWord)

    private suspend fun findExample(word: String): WordExample? {
        val vocab = vocabDao.findByLemma(word.lowercase()) ?: return null
        val line = vocab.contextLine ?: return null
        val song = vocab.firstSongId?.let { songDao.findById(it) }
        return WordExample(
            line = line,
            songTitle = song?.title ?: "",
            songLrclibId = song?.lrclibId,
        )
    }

    companion object {
        const val ARG_WORD = "word"
    }
}
