package com.antigravity.lyrian.ui.screens.wordbank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** 字庫的篩選條件。 */
enum class WordbankFilter { ALL, MASTERED, REVIEWING }

data class WordbankUiState(
    val filter: WordbankFilter = WordbankFilter.ALL,
    val items: List<VocabEntity> = emptyList(),
    val total: Int = 0,
    val mastered: Int = 0,
    val learning: Int = 0,
) {
    /** 「待複習」= 尚未掌握的全部 */
    val dueForReview: Int get() = total - mastered
}

@HiltViewModel
class WordbankViewModel @Inject constructor(
    private val vocabDao: VocabDao,
) : ViewModel() {

    private val _filter = MutableStateFlow(WordbankFilter.ALL)
    val filter: StateFlow<WordbankFilter> = _filter.asStateFlow()

    /** 目前的清單（依篩選條件切換來源）。 */
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
            learning = total - mastered,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordbankUiState(),
    )

    fun onFilterChange(filter: WordbankFilter) {
        _filter.value = filter
    }
}
