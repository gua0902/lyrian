package com.antigravity.lyrian.ui.screens.wordbank

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.data.dictionary.DictionaryService
import com.antigravity.lyrian.data.local.VocabDao
import com.antigravity.lyrian.data.local.VocabEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 字庫的篩選條件。 */
enum class WordbankFilter { ALL, MASTERED, REVIEWING }

data class WordbankUiState(
    val filter: WordbankFilter = WordbankFilter.ALL,
    val items: List<VocabEntity> = emptyList(),
    val total: Int = 0,
    val mastered: Int = 0,
) {
    /** 「待複習」= 尚未掌握的全部 */
    val dueForReview: Int get() = total - mastered
}

@HiltViewModel
class WordbankViewModel @Inject constructor(
    private val vocabDao: VocabDao,
    private val dictionaryService: DictionaryService,
) : ViewModel() {

    private val _filter = MutableStateFlow(WordbankFilter.ALL)
    val filter: StateFlow<WordbankFilter> = _filter.asStateFlow()

    private val listFlow = combine(_filter, vocabDao.observeAll()) { filter, all ->
        when (filter) {
            WordbankFilter.ALL -> all
            WordbankFilter.MASTERED -> all.filter { it.status == VocabEntity.STATUS_MASTERED }
            WordbankFilter.REVIEWING -> all.filter { it.status != VocabEntity.STATUS_MASTERED }
        }
    }

    val uiState: StateFlow<WordbankUiState> = combine(
        _filter,
        listFlow,
        vocabDao.observeTotal(),
        vocabDao.observeCountByStatus(VocabEntity.STATUS_MASTERED),
    ) { filter, items, total, mastered ->
        WordbankUiState(
            filter = filter,
            items = items,
            total = total,
            mastered = mastered,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordbankUiState(),
    )

    fun onFilterChange(filter: WordbankFilter) {
        _filter.value = filter
    }

    /**
     * 為還沒有字典資料的單字補查。
     *
     * **只查缺的**——`dictionaryFetched` 為 true 的會跳過。
     *
     * 一次最多查 [MAX_LOOKUPS]，避免進入字庫頁時一次打太多 API。
     */
    fun fetchMissingDictionaryData() {
        viewModelScope.launch {
            val pending = uiState.value.items
                .filterNot { it.dictionaryFetched }
                .take(MAX_LOOKUPS)

            for (vocab in pending) {
                val entry = dictionaryService.lookup(vocab.lemma)

                val hasData = entry != null && entry.hasContent
                if (!hasData) {
                    // 查不到就**不要**標記為已查過——否則這個字永遠不會再試，
                    // 使用者只看得到一句「查不到」。
                    // 目前仍然計入這一輪的配額，避免一次掃完整個字庫。
                    continue
                }

                vocabDao.updateDictionary(
                    id = vocab.id,
                    translation = entry.definition,
                    phonetic = entry.phonetic,
                    accent = entry.phoneticAccent,
                    partOfSpeech = entry.partOfSpeech,
                    cefr = null,
                    now = System.currentTimeMillis(),
                    markFetched = true,
                )
            }
        }
    }

    private companion object {
        /**
         * 一次最多補查幾個字。
         *
         * 字典 API 沒有明確的速率限制，但**不該在進入頁面時一次打幾十個請求**——
         * 那既慢又無礼。剩下的會在下次進入時陸續補齊。
         */
        const val MAX_LOOKUPS = 8
    }
}

/**
 * 單字詳情頁的 ViewModel。
 *
 * 進入時若還沒有字典資料就補查一次；之後不再連外。
 */
@HiltViewModel
class VocabDetailViewModel @Inject constructor(
    private val vocabDao: VocabDao,
    private val dictionaryService: DictionaryService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vocabId: Long =
        savedStateHandle.get<String>(VocabDetailRoute.ARG)?.toLongOrNull() ?: -1L

    val entry: StateFlow<VocabEntity?> = vocabDao.observeById(vocabId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    init {
        viewModelScope.launch {
            val vocab = vocabDao.findById(vocabId) ?: return@launch
            if (vocab.dictionaryFetched) return@launch

            val result = dictionaryService.lookup(vocab.lemma) ?: return@launch
            if (!result.hasContent) return@launch

            vocabDao.updateDictionary(
                id = vocabId,
                translation = result.definition,
                phonetic = result.phonetic,
                accent = result.phoneticAccent,
                partOfSpeech = result.partOfSpeech,
                cefr = null,
                now = System.currentTimeMillis(),
                markFetched = true,
            )
        }
    }
}

/** 詳情頁的路由參數名稱。 */
object VocabDetailRoute {
    const val ARG = "vocabId"
}
