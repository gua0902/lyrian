package com.antigravity.lyrian.ui.screens.wordbank

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.data.dictionary.DictionaryRepository
import com.antigravity.lyrian.data.dictionary.DictionaryResult
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
    private val dictionary: DictionaryRepository,
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
     * 字典是離線資產，這裡不會連外；[MAX_LOOKUPS] 只是限制每次進入頁面時的
     * 解析量，剩下的會在下次進入時補齊。
     */
    fun fetchMissingDictionaryData() {
        viewModelScope.launch {
            val pending = uiState.value.items
                .filterNot { it.dictionaryFetched }
                .take(MAX_LOOKUPS)

            for (vocab in pending) {
                val result = dictionary.lookup(vocab.lemma)
                if (result !is DictionaryResult.Found) {
                    // 查不到就**不要**標記為已查過——否則這個字永遠不會再試，
                    // 使用者只看得到一句「查不到」。
                    continue
                }

                vocabDao.updateDictionary(
                    id = vocab.id,
                    translation = result.entry.translation,
                    phonetic = result.entry.phonetic,
                    // ECDICT 的標音不區分英／美，因此不標腔調，避免標錯
                    accent = null,
                    partOfSpeech = result.entry.partOfSpeech,
                    cefr = result.entry.level?.label,
                    now = System.currentTimeMillis(),
                    markFetched = true,
                )
            }
        }
    }

    private companion object {
        /** 一次最多補查幾個字（純本機解析，不需要節流，只是避免一次掃完整個字庫）。 */
        const val MAX_LOOKUPS = 8
    }
}
