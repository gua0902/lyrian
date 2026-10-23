package com.antigravity.lyrian.ui.screens.wordbank

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.R
import com.antigravity.lyrian.data.dictionary.DictionaryRepository
import com.antigravity.lyrian.data.dictionary.DictionaryResult
import com.antigravity.lyrian.data.local.SongDao
import com.antigravity.lyrian.data.local.VocabDao
import com.antigravity.lyrian.data.tts.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 字庫的單字詳情（`vocab/{id}`）。
 *
 * 版面與 `word/{word}` **完全共用**（[WordDetailBody]），只差在資料來源：
 * 這裡的字一定有語境與來源歌曲，而且會補查字典後寫回 Room。
 *
 * 擁有者要求「字庫詳情頁接上手機的 TTS，與其他頁面共用同一實作」——
 * 所以朗讀走同一個 [Speaker] 單例。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabDetailScreen(
    onBack: () -> Unit,
    onOpenSong: (Long) -> Unit,
    viewModel: VocabDetailViewModel = hiltViewModel(),
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

@HiltViewModel
class VocabDetailViewModel @Inject constructor(
    private val vocabDao: VocabDao,
    private val songDao: SongDao,
    private val dictionary: DictionaryRepository,
    private val speaker: Speaker,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vocabId: Long =
        savedStateHandle.get<String>(VocabDetailRoute.ARG)?.toLongOrNull() ?: -1L

    private val _uiState = MutableStateFlow(WordDetailUiState())
    val uiState: StateFlow<WordDetailUiState> = _uiState.asStateFlow()

    private var lastSpoken: String = ""

    init {
        viewModelScope.launch {
            val vocab = vocabDao.findById(vocabId)
            if (vocab == null) {
                _uiState.value = WordDetailUiState(isLoading = false, notFound = true)
                return@launch
            }

            lastSpoken = vocab.lemma

            val example = runCatching {
                val line = vocab.contextLine
                val song = vocab.firstSongId?.let { songDao.findById(it) }
                if (line.isNullOrBlank()) {
                    null
                } else {
                    WordExample(
                        line = line,
                        songTitle = song?.title.orEmpty(),
                        songLrclibId = song?.lrclibId,
                    )
                }
            }.getOrNull()

            // 字典資料優先從離線分片取；拿到就順手寫回 Room，之後不必再解析分片
            val hit = dictionary.lookup(vocab.lemma)
            val entry = (hit as? DictionaryResult.Found)?.entry
            if (entry != null && !vocab.dictionaryFetched) {
                vocabDao.updateDictionary(
                    id = vocabId,
                    translation = entry.translation,
                    phonetic = entry.phonetic,
                    accent = null,
                    partOfSpeech = entry.partOfSpeech,
                    cefr = entry.level?.label,
                    now = System.currentTimeMillis(),
                    markFetched = true,
                )
            }

            _uiState.value = WordDetailUiState(
                word = vocab.surfaceForm ?: vocab.lemma,
                isLoading = false,
                entry = entry,
                example = example,
                notFound = entry == null,
            )
        }
    }

    fun speak() = speaker.speak(lastSpoken)
}

/** 詳情頁的路由參數名稱。 */
object VocabDetailRoute {
    const val ARG = "vocabId"
}
