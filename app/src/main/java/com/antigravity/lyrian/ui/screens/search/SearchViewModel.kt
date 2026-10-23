package com.antigravity.lyrian.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.data.lang.LanguageDataStore
import com.antigravity.lyrian.data.remote.SongSearchRepository
import com.antigravity.lyrian.data.remote.SongSearchResult
import com.antigravity.lyrian.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 搜尋頁的 UI 狀態。
 *
 * 用單一 data class 而非散落的 mutable state——這樣狀態空間是封閉的，
 * 「載入中但有錯誤」這類矛盾狀態在編譯期就不存在。
 */
data class SearchUiState(
    val artist: String = "",
    val track: String = "",
    val isLoading: Boolean = false,
    val results: List<SongSearchResult> = emptyList(),
    /** 已送出過至少一次搜尋；用來決定要不要顯示結果區塊 */
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 兩個欄位都填了才能搜尋 */
    val canSearch: Boolean get() = artist.isNotBlank() && track.isNotBlank() && !isLoading
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SongSearchRepository,
    private val languageData: LanguageDataStore,
    private val userSettings: UserSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onArtistChange(value: String) {
        _uiState.update { it.copy(artist = value, errorMessage = null) }
    }

    fun onTrackChange(value: String) {
        _uiState.update { it.copy(track = value, errorMessage = null) }
    }

    fun search() {
        val state = _uiState.value
        if (!state.canSearch) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                // 「N 個填空」要用使用者目前的等級門檻算，
                // 否則清單上的數字會與進練習頁後實際的題數不一致。
                repository.search(
                    artist = state.artist,
                    track = state.track,
                    minLevel = userSettings.minLevelValue,
                    levelSource = languageData.get(),
                )
            }
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasSearched = true,
                            results = results,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasSearched = true,
                            results = emptyList(),
                            errorMessage = error.message ?: "搜尋失敗，請稍後再試",
                        )
                    }
                }
        }
    }
}
