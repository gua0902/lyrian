package com.antigravity.lyrian.ui.screens.wordbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.data.dictionary.DictionaryRepository
import com.antigravity.lyrian.data.dictionary.DictionaryResult
import com.antigravity.lyrian.data.local.VocabDao
import com.antigravity.lyrian.data.local.VocabEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 字庫的篩選條件。 */
enum class WordbankFilter { ALL, MASTERED, REVIEWING }

/** 一頁載入幾個字。 */
internal const val WORDBANK_PAGE_SIZE = 200

data class WordbankUiState(
    val filter: WordbankFilter = WordbankFilter.ALL,
    val items: List<VocabEntity> = emptyList(),
    val total: Int = 0,
    val mastered: Int = 0,
    /** 後面還有字可以載入（清單尾端顯示「載入更多」）。 */
    val hasMore: Boolean = false,
) {
    /** 「待複習」= 尚未掌握的全部 */
    val dueForReview: Int get() = total - mastered
}

/**
 * 字庫的分頁。
 *
 * ## 為什麼需要它
 *
 * 舊版把 `observeAll(limit = 200)` 寫死在 DAO 的預設值，於是**字庫超過 200 個字
 * 之後，舊的單字就從畫面上消失了**——但橫幅顯示的 `total` 仍是全部。
 * 使用者看到「共 350 個字」，清單卻永遠只有 200 筆，而且沒有任何線索。
 *
 * 這裡改成「一次看一個視窗、按需要加大」，沒有新依賴（不需要 Paging3）。
 */
internal object WordbankPaging {

    /**
     * 是否該顯示「載入更多」。
     *
     * @param fetched 這個視窗實際拿到的列數
     * @param limit 目前視窗大小
     * @param total 資料庫裡的總數
     */
    fun hasMore(fetched: Int, limit: Int, total: Int): Boolean =
        // 視窗被填滿（代表後面可能還有）而且總數還大於視窗
        fetched >= limit && total > limit
}

@HiltViewModel
class WordbankViewModel @Inject constructor(
    private val vocabDao: VocabDao,
    private val dictionary: DictionaryRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(WordbankFilter.ALL)
    val filter: StateFlow<WordbankFilter> = _filter.asStateFlow()

    /** 目前清單視窗大小；按「載入更多」時長大。 */
    private val _limit = MutableStateFlow(WORDBANK_PAGE_SIZE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val windowFlow = _limit.flatMapLatest { limit ->
        vocabDao.observeAll(limit).map { all -> all to limit }
    }

    private val listFlow = combine(_filter, windowFlow) { filter, (all, limit) ->
        val items = when (filter) {
            WordbankFilter.ALL -> all
            WordbankFilter.MASTERED -> all.filter { it.status == VocabEntity.STATUS_MASTERED }
            WordbankFilter.REVIEWING -> all.filter { it.status != VocabEntity.STATUS_MASTERED }
        }
        Triple(items, all.size, limit)
    }

    val uiState: StateFlow<WordbankUiState> = combine(
        _filter,
        listFlow,
        vocabDao.observeTotal(),
        vocabDao.observeCountByStatus(VocabEntity.STATUS_MASTERED),
    ) { filter, (items, fetched, limit), total, mastered ->
        WordbankUiState(
            filter = filter,
            items = items,
            total = total,
            mastered = mastered,
            hasMore = WordbankPaging.hasMore(fetched = fetched, limit = limit, total = total),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordbankUiState(),
    )

    fun onFilterChange(filter: WordbankFilter) {
        _filter.value = filter
    }

    /** 多載入一頁。使用者按清單尾端的「載入更多」時呼叫。 */
    fun onLoadMore() {
        _limit.update { it + WORDBANK_PAGE_SIZE }
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
