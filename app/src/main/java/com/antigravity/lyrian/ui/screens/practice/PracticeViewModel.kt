package com.antigravity.lyrian.ui.screens.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.lyrian.data.local.AttemptDao
import com.antigravity.lyrian.data.local.AttemptEntity
import com.antigravity.lyrian.data.local.DailyStatDao
import com.antigravity.lyrian.data.local.LyricLineEntity
import com.antigravity.lyrian.data.local.LyricLineDao
import com.antigravity.lyrian.data.local.SongDao
import com.antigravity.lyrian.data.local.SongEntity
import com.antigravity.lyrian.data.local.VocabDao
import com.antigravity.lyrian.data.active.PracticeSessionStore
import com.antigravity.lyrian.data.lyrics.LyricsParser
import com.antigravity.lyrian.domain.model.ChoiceRecord
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.PracticeModeId
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import com.antigravity.lyrian.ui.navigation.Routes
import com.antigravity.lyrian.util.toDayKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 練習頁的 UI 狀態。
 *
 * 用單一 data class 讓狀態空間封閉——「已答完但還在作答中」這類矛盾狀態不存在。
 */
data class PracticeUiState(
    val songTitle: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    val lines: List<LyricLine> = emptyList(),
    val questions: List<FillChoiceQuestion> = emptyList(),
    val activeBlankIndex: Int = 0,
    /** blankIndex → 作答結果 */
    val selections: Map<Int, ChoiceRecord> = emptyMap(),
    /** 當前題的 4 個選項 */
    val options: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val currentQuestion: FillChoiceQuestion?
        get() = questions.getOrNull(activeBlankIndex)

    val currentRecord: ChoiceRecord?
        get() = currentQuestion?.let { selections[it.blankIndex] }

    /** 全部答完 */
    val isComplete: Boolean
        get() = questions.isNotEmpty() && activeBlankIndex >= questions.size

    val correctCount: Int get() = selections.values.count { it.isCorrect }

    val total: Int get() = questions.size

    val percent: Int get() = if (total > 0) Math.round(correctCount * 100f / total) else 0
}

/**
 * 練習頁的 ViewModel。
 *
 * 資料來源是 [PracticeSessionStore]（搜尋頁選歌時寫入），
 * 因此**不需要重新打 API**——歌詞在搜尋時就已取得。
 */
@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val sessionStore: PracticeSessionStore,
    private val songDao: SongDao,
    private val lyricLineDao: LyricLineDao,
    private val attemptDao: AttemptDao,
    private val vocabDao: VocabDao,
    private val dailyStatDao: DailyStatDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    /** 歌詞只在第一次作答時寫入快取，避免每次作答都 delete + insert。 */
    private var lyricsPersisted = false
    private var localSongId: Long? = null

    init {
        val songId = savedStateHandle.get<String>(Routes.ARG_SONG_ID)?.toLongOrNull()
        load(songId)
    }

    private fun load(songId: Long?) {
        val session = songId?.let { sessionStore.take(it) }

        if (session == null) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "找不到這首歌的練習資料，請重新搜尋。")
            }
            return
        }

        val lines = LyricsParser.parse(
            lyricsFile = session.lyricsFile,
            syncedLyrics = session.syncedLyrics,
            plainLyrics = session.plainLyrics,
        )
        val dataset = FillChoiceBuilder.buildDataset(lines)

        if (dataset.questions.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "這首歌沒有可挖空的歌詞。",
                    songTitle = session.title,
                    artist = session.artist,
                    lines = dataset.lines,
                )
            }
            return
        }

        val allAnswers = dataset.questions.map { it.answer }
        val firstOptions = FillChoiceBuilder.buildOptions(dataset.questions.first(), allAnswers)

        _uiState.update {
            it.copy(
                isLoading = false,
                songTitle = session.title,
                artist = session.artist,
                coverUrl = session.coverUrl,
                lines = dataset.lines,
                questions = dataset.questions,
                options = firstOptions,
            )
        }
    }

    fun onSelectOption(choice: String) {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        if (state.currentRecord != null) return // 已作答，忽略重複點擊

        val attempt = FillChoiceBuilder.grade(question, choice)

        _uiState.update {
            it.copy(
                selections = it.selections + (question.blankIndex to ChoiceRecord(choice, attempt.isCorrect)),
            )
        }

        persist(question, choice, attempt.isCorrect)
    }

    /** 前進到下一題。全部答完時 [PracticeUiState.isComplete] 會變成 true。 */
    fun onNext() {
        val state = _uiState.value
        if (state.currentRecord == null) return

        val nextIndex = state.activeBlankIndex + 1
        val nextQuestion = state.questions.getOrNull(nextIndex)
        val allAnswers = state.questions.map { it.answer }

        _uiState.update {
            it.copy(
                activeBlankIndex = nextIndex,
                options = nextQuestion?.let { q -> FillChoiceBuilder.buildOptions(q, allAnswers) }
                    ?: emptyList(),
            )
        }
    }

    fun onReplay() {
        val state = _uiState.value
        val first = state.questions.firstOrNull() ?: return
        val allAnswers = state.questions.map { it.answer }

        _uiState.update {
            it.copy(
                activeBlankIndex = 0,
                selections = emptyMap(),
                options = FillChoiceBuilder.buildOptions(first, allAnswers),
            )
        }
    }

    /**
     * 把一次作答落庫：作答紀錄 →（答錯時）字庫 → 每日統計。
     *
     * **失敗不中斷練習**——與「AI 不在關鍵路徑上」同一原則。
     */
    private fun persist(question: FillChoiceQuestion, choice: String, isCorrect: Boolean) {
        viewModelScope.launch {
            runCatching {
                val session = sessionStore.peek()
                val songId = ensureSongPersisted()

                val vocabId = if (!isCorrect) {
                    val (id, _) = vocabDao.addFromError(
                        lemma = FillChoiceBuilder.normalizeAnswer(question.answer),
                        surfaceForm = question.answer,
                        contextLine = question.originalLine,
                        songId = songId,
                        now = System.currentTimeMillis(),
                    )
                    id
                } else {
                    null
                }

                attemptDao.insert(
                    AttemptEntity(
                        modeId = PracticeModeId.FILL_CHOICE.name,
                        songId = songId,
                        lineIndex = question.lineIndex,
                        vocabId = vocabId,
                        prompt = "${question.prefix}____${question.suffix}",
                        userAnswer = choice,
                        correctAnswer = question.answer,
                        score = if (isCorrect) 1f else 0f,
                        isCorrect = isCorrect,
                        createdAt = System.currentTimeMillis(),
                    ),
                )

                dailyStatDao.bumpOrCreate(
                    day = toDayKey(),
                    score = if (isCorrect) 1 else 0,
                    attempts = 1,
                    correct = if (isCorrect) 1 else 0,
                )

                // 避免未使用的變數警告，同時保留 session 供日後擴充
                session?.songId
            }.onFailure { error ->
                android.util.Log.w("Lyrian", "記錄作答失敗（不影響練習）", error)
            }
        }
    }

    /** 第一次作答時把歌曲與歌詞寫入快取；之後沿用同一個 id。 */
    private suspend fun ensureSongPersisted(): Long? {
        localSongId?.let { return it }

        val session = sessionStore.peek() ?: return null

        val songId = songDao.upsertByLrclibId(
            SongEntity(
                lrclibId = session.songId,
                title = session.title,
                artist = session.artist,
                album = session.album,
                durationMs = session.durationMs,
                coverUrl = session.coverUrl,
                genre = session.genre,
                createdAt = System.currentTimeMillis(),
            ),
        )

        if (!lyricsPersisted) {
            val lines = _uiState.value.lines.map { line ->
                LyricLineEntity(
                    songId = songId,
                    lineIndex = line.index,
                    text = line.text,
                    startMs = line.startMs,
                    endMs = line.endMs,
                )
            }
            lyricLineDao.replaceForSong(songId, lines)
            lyricsPersisted = true
        }

        localSongId = songId
        return songId
    }
}
