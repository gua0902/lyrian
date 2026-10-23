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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.antigravity.lyrian.data.dictionary.DictionaryRepository
import com.antigravity.lyrian.data.dictionary.DictionaryResult
import com.antigravity.lyrian.data.tts.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 單字詳情的 UI 狀態。 */
data class WordDetailUiState(
    val word: String = "",
    val isLoading: Boolean = true,
    val partOfSpeech: String? = null,
    val phonetic: String? = null,
    val meaning: String? = null,
    val level: String? = null,
    /** 查無此詞 */
    val notFound: Boolean = false,
    /** 查詢過程中出錯（資產讀取失敗），與「查無此詞」不同 */
    val error: String? = null,
)

/**
 * 單字詳情（結果頁點字進來的那一條路徑）。
 *
 * 與字庫的 `vocab/{id}` 不同：這裡**沒有** Room 紀錄、沒有語境、沒有錯誤次數，
 * 純粹是「查一個字」。[DictionaryRepository] 是離線資產，所以不需要網路狀態。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    onBack: () -> Unit,
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
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WordHeadline(
                    word = state.word,
                    partOfSpeech = state.partOfSpeech,
                    phonetic = state.phonetic,
                    level = state.level,
                    onSpeak = viewModel::speak,
                )

                when {
                    state.error != null -> Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    state.notFound -> Text(
                        text = stringResource(R.string.wordbank_detail_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    else -> Section(
                        title = stringResource(R.string.wordbank_detail_meaning),
                        body = state.meaning ?: "—",
                    )
                }
            }
        }
    }
}

@Composable
private fun WordHeadline(
    word: String,
    partOfSpeech: String?,
    phonetic: String?,
    level: String?,
    onSpeak: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row {
                        partOfSpeech?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        level?.let {
                            Text(
                                text = "  $it",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                FilledIconButton(
                    onClick = onSpeak,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.wordbank_detail_pronounce),
                    )
                }
            }

            phonetic?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    private val dictionary: DictionaryRepository,
    private val speaker: Speaker,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rawWord: String =
        savedStateHandle.get<String>(ARG_WORD).orEmpty()

    private val _uiState = MutableStateFlow(WordDetailUiState(word = rawWord))
    val uiState: StateFlow<WordDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = dictionary.lookup(rawWord)) {
                is DictionaryResult.Found -> _uiState.value = WordDetailUiState(
                    word = rawWord,
                    isLoading = false,
                    partOfSpeech = result.entry.partOfSpeech,
                    phonetic = result.entry.phonetic,
                    meaning = result.entry.translation,
                    level = result.entry.level?.label,
                )

                is DictionaryResult.NotFound -> _uiState.value = WordDetailUiState(
                    word = rawWord,
                    isLoading = false,
                    notFound = true,
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

    companion object {
        const val ARG_WORD = "word"
    }
}
