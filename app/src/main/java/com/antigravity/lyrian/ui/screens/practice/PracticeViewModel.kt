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
import com.antigravity.lyrian.data.audio.AudioPlayerController
import com.antigravity.lyrian.data.audio.AudioSourceResolver
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

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

    // ---------- 音訊 ----------

    /** 正在解析音源（搜尋 YouTube Music → 取串流 URL）。 */
    val isAudioLoading: Boolean = false,
    /** 音源已就緒，可以播放。 */
    val isAudioReady: Boolean = false,
    /** 播放器是否正在播放。 */
    val isPlaying: Boolean = false,
    /** 正在緩衝。 */
    val isBuffering: Boolean = false,
    /** 播放位置（毫秒）。 */
    val playbackPositionMs: Long = 0L,
    /** 曲目總長（毫秒）；未知時為 null。 */
    val playbackDurationMs: Long? = null,
    /**
     * 目前**唱到**的歌詞行索引；沒有時間戳時為 null。
     *
     * 這與 [activeBlankIndex]（作答進度）是**兩條獨立的軸**：
     * 前者來自音訊時間，後者來自使用者答到哪。
     */
    val singingLineIndex: Int? = null,
    /** 音源相關的提示（例如「找不到對應的錄音版本」）。 */
    val audioNotice: String? = null,
    /**
     * 播放器本身回報的錯誤。
     *
     * 與 [audioNotice] **分開存放**：播放器是單例，它的錯誤會一直留在狀態裡，
     * 若混進 [audioNotice] 就會讓「上一首歌的播放錯誤」黏到下一首歌上。
     * 這個欄位每次載入新歌時都會清空。
     */
    val playbackError: String? = null,
) {
    val currentQuestion: FillChoiceQuestion?
        get() = questions.getOrNull(activeBlankIndex)

    val currentRecord: ChoiceRecord?
        get() = currentQuestion?.let { selections[it.blankIndex] }

    /** 全部答完 */
    val isComplete: Boolean
        get() = questions.isNotEmpty() && activeBlankIndex >= questions.size

    val correctCount: Int get() = selections.values.count { it.isCorrect }

    /**
     * 已作答的**最後一行**索引；尚未作答任何一題時為 -1。
     *
     * 給歌詞捲動判斷「這題答完了沒」（見 `LyricFollowController`）：
     * 使用者還在思考時不能搶畫面，所以要比較「想捲過去的行」與這個值。
     */
    val lastAnsweredLineIndex: Int
        get() = questions
            .filter { selections.containsKey(it.blankIndex) }
            .maxOfOrNull { it.lineIndex }
            ?: -1

    val total: Int get() = questions.size

    val percent: Int get() = if (total > 0) Math.round(correctCount * 100f / total) else 0
}

/** 找不到練習資料。使用者唯一的出路是返回。 */
private const val ERROR_NO_SESSION = "找不到這首歌的練習資料，請重新搜尋。"

/** 有歌詞，但沒有任何一行可以挖空（純演奏曲、或全是很短的虛詞）。 */
private const val ERROR_NO_BLANKS = "這首歌沒有可挖空的歌詞。"

/** 在 YouTube Music 上找不到可播放的對應版本。 */
private const val NO_AUDIO_FOUND = "找不到這首歌的可播放版本，只能純歌詞練習。"

/** 找到了，但時長與歌詞時間戳差距過大，可能不是同一個錄音。 */
private const val AUDIO_MAY_NOT_ALIGN =
    "找到的版本長度與歌詞不符，歌詞可能會有些對不上。"

/**
 * 播放位置的取樣間隔。
 *
 * Media3 沒有「位置變化」的 callback（位置是連續值，每帧回報太吵），
 * 因此以固定間隔取樣。50ms 足以讓歌詞高亮跟得上歌聲，又不會浪費 CPU。
 */
private const val POSITION_POLL_MS = 50L

private const val TAG = "LyrianPractice"

/** 歌曲的基本資訊。與歌詞來源分開，因為兩層退路取到的方式不同。 */
private data class SongHead(
    val songId: Long,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    /**
     * LRCLIB 的時長（毫秒）。
     *
     * **音源配對的關鍵**：用來在 YouTube Music 的眾多版本
     * （錄音室／現場／MV／remaster）中挑出同一個錄音。見 `AudioTrackMatcher`。
     */
    val durationMs: Long? = null,
)

/** 一份完整的練習資料來源：歌曲資訊 + 歌詞行。 */
private data class SongSource(
    val head: SongHead,
    val lines: List<LyricLine>,
)

/**
 * 練習頁的 ViewModel。
 *
 * 資料來源是 [PracticeSessionStore]（搜尋頁選歌時寫入），
 * 因此**不需要重新打 API**——歌詞在搜尋時就已取得。
 *
 * **載入有兩層退路**，見 [load]：記憶體 session → Room 快取。
 */
@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val sessionStore: PracticeSessionStore,
    private val songDao: SongDao,
    private val lyricLineDao: LyricLineDao,
    private val attemptDao: AttemptDao,
    private val vocabDao: VocabDao,
    private val dailyStatDao: DailyStatDao,
    private val audioResolver: AudioSourceResolver,
    val player: AudioPlayerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    /** 歌詞只在第一次作答時寫入快取，避免每次作答都 delete + insert。 */
    private var lyricsPersisted = false
    private var localSongId: Long? = null

    /**
     * 目前這首歌的基本資訊。
     *
     * 由 [applySource] 填入，供 [ensureSongPersisted] 使用。
     * **不從 `sessionStore` 讀**——程序死亡後回復時那裡是空的。
     */
    private var songHead: SongHead? = null

    /** 全部題目的選項**預先算好**，避免每次換題重算（也讓上下題的選項穩定）。 */
    private var optionsByBlank: Map<Int, List<String>> = emptyMap()

    init {
        val songId = savedStateHandle.get<String>(Routes.ARG_SONG_ID)?.toLongOrNull()
        load(songId)
        observePlayback()
    }

    // ---------- 音訊 ----------

    /** 是否已經解析過音源（避免每次按播放都重打 YouTube）。 */
    private var audioResolved = false

    /**
     * 把播放器的狀態同步進 UI 狀態。
     *
     * 播放器是**單例**（旋轉螢幕不中斷音樂），所以它的狀態可能比這個
     * ViewModel 還早存在。因此除了收集 flow，也要立刻讀一次目前值。
     */
    private fun observePlayback() {
        viewModelScope.launch {
            player.state.collect { playback ->
                _uiState.update {
                    it.copy(
                        isPlaying = playback.isPlaying,
                        isBuffering = playback.isBuffering,
                        playbackPositionMs = playback.positionMs,
                        playbackDurationMs = playback.durationMs,
                        playbackError = playback.error,
                    )
                }
            }
        }

        // 播放中才輪詢位置。Media3 沒有「位置變化」的 callback
        // （位置是連續值，全部回報會太吵），因此以固定間隔取樣。
        viewModelScope.launch {
            while (true) {
                if (_uiState.value.isPlaying) {
                    val position = player.currentPositionMs()
                    _uiState.update {
                        it.copy(
                            playbackPositionMs = position,
                            singingLineIndex = lineIndexAt(position),
                        )
                    }
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    /**
     * 準備音源。第一次呼叫才會真的打 YouTube。
     *
     * ⚠️ **時長不符時不硬播**：見 `AudioTrackMatcher`。若挑到的版本
     * 與 LRCLIB 的時間戳明顯不是同一個錄音，硬播只會讓歌詞整首錯位。
     */
    fun prepareAudio() {
        if (audioResolved || _uiState.value.isAudioLoading) return
        val head = songHead ?: return

        audioResolved = true
        _uiState.update {
            it.copy(isAudioLoading = true, audioNotice = null, playbackError = null)
        }

        viewModelScope.launch {
            val resolved = audioResolver.resolve(
                title = head.title,
                artist = head.artist,
                expectedDurationMs = head.durationMs,
            )

            when (resolved) {
                is AudioSourceResolver.Result.Failed -> {
                    _uiState.update {
                        it.copy(
                            isAudioLoading = false,
                            audioNotice = describeAudioFailure(resolved.failure),
                        )
                    }
                }

                is AudioSourceResolver.Result.Ok -> {
                    val ok = resolved.resolved
                    player.prepare(ok.stream.url)

                    _uiState.update {
                        it.copy(
                            isAudioLoading = false,
                            isAudioReady = true,
                            audioNotice = if (ok.isConfident) null else AUDIO_MAY_NOT_ALIGN,
                        )
                    }
                }
            }
        }
    }

    /**
     * 把音源失敗翻成**可行動**的訊息。
     *
     * 「找不到」要使用者換一首，「服務掛了」要使用者稍後再試——
     * 講成同一句會讓人以為是 App 壞了。
     */
    private fun describeAudioFailure(failure: AudioSourceResolver.Failure): String = when (failure) {
        is AudioSourceResolver.Failure.NoCandidate -> NO_AUDIO_FOUND
        is AudioSourceResolver.Failure.Unavailable ->
            "這首歌的版本無法播放（${failure.reason}），只能純歌詞練習。"
        is AudioSourceResolver.Failure.Error ->
            "音源服務暫時無法使用，只能純歌詞練習。"
    }

    /**
     * 播放鍵：**同時控制播放器與歌詞滾動**。
     *
     * 這是擁有者指定的核心行為——只有一個開關，不是兩個。
     */
    fun onTogglePlayPause() {
        if (!_uiState.value.isAudioReady) {
            prepareAudio()
            return
        }
        player.togglePlayPause()
    }

    /** 拖曳播放進度條。 */
    fun onSeekPlayback(positionMs: Long) {
        player.seekTo(positionMs)
        _uiState.update { it.copy(playbackPositionMs = positionMs) }
    }

    /**
     * 某個時間點**正在唱**哪一行。
     *
     * 取「開始時間 <= 位置」的最後一行；位置在所有行之前時回傳 null。
     * 沒有時間戳（純文字歌詞）時全部為 0，此時回傳 null 讓 UI 不要跟隨。
     */
    fun lineIndexAt(positionMs: Long): Int? {
        val lines = _uiState.value.lines
        if (lines.isEmpty()) return null
        // 純文字歌詞每行都有間隔（LyricsParser 會補），但仍要防禦全為 0 的情況
        if (lines.all { it.startMs <= 0L }) return null

        return lines.lastOrNull { it.startMs <= positionMs }?.index
    }

    override fun onCleared() {
        super.onCleared()
        // 使用者真的離開練習頁（不是旋轉）→ 停掉音樂並釋放原生解碼器。
        //
        // ⚠️ 這裡是唯一正確的釋放點：ViewModel 在**設定變更**時不會被清除，
        // 所以旋轉螢幕時音樂不中斷；只有離開這個頁面才會走到這裡。
        player.stop()
        player.release()
    }

    private fun load(songId: Long?) {
        if (songId == null) {
            failWith(ERROR_NO_SESSION)
            return
        }

        // 第 1 層：記憶體中的 session（涵蓋設定變更／旋轉螢幕）。
        // 用 get() 而非已移除的 take()——見 PracticeSessionStore 的類別註解。
        sessionStore.get(songId)?.let { session ->
            applySource(
                source = SongSource(
                    head = SongHead(
                        songId = songId,
                        title = session.title,
                        artist = session.artist,
                        coverUrl = session.coverUrl,
                        durationMs = session.durationMs,
                    ),
                    lines = LyricsParser.parse(
                        lyricsFile = session.lyricsFile,
                        syncedLyrics = session.syncedLyrics,
                        plainLyrics = session.plainLyrics,
                    ),
                ),
            )
            return
        }

        // 第 2 層：程序被系統回收後，從 Room 快取取回。
        // 這條路徑需要第 1 次作答時寫入的 song / lyric_line（見 ensureSongPersisted）。
        viewModelScope.launch {
            val restored = restoreFromCache(songId)
            if (restored != null) {
                applySource(restored)
            } else {
                failWith(ERROR_NO_SESSION)
            }
        }
    }

    /**
     * 從 Room 取回一首歌（程序死亡後的路徑）。
     *
     * ⚠️ **`lyric_line` 只存 `text`，不存挖空位置**，所以回復時要重跑
     * [FillChoiceBuilder.buildDataset]。因為亂數種子固定為 `songId`
     * （見 [randomFor]），挖出來的字與死亡前一致。
     *
     * @return 找不到歌曲或沒有歌詞行時回傳 null
     */
    private suspend fun restoreFromCache(songId: Long): SongSource? = runCatching {
        val song = songDao.findByLrclibId(songId) ?: return@runCatching null
        val storedLines = lyricLineDao.getForSong(song.id)
        if (storedLines.isEmpty()) return@runCatching null

        SongSource(
            head = SongHead(
                songId = songId,
                title = song.title,
                artist = song.artist,
                coverUrl = song.coverUrl,
                durationMs = song.durationMs,
            ),
            lines = storedLines.map { entity ->
                LyricLine(
                    index = entity.lineIndex,
                    text = entity.text,
                    startMs = entity.startMs,
                    endMs = entity.endMs,
                )
            },
        )
    }.onFailure {
        android.util.Log.w(TAG, "從快取回復練習進度失敗", it)
    }.getOrNull()

    /** 把一份歌詞來源轉成 UI 狀態。兩條來源路徑共用，確保行為一致。 */
    private fun applySource(source: SongSource) {
        songHead = source.head

        // 播放器是單例，可能還留著上一首歌的狀態 → 每次都清乾淨
        player.stop()
        audioResolved = false

        val dataset = FillChoiceBuilder.buildDataset(
            source.lines,
            randomFor(source.head.songId),
        )

        if (dataset.questions.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = ERROR_NO_BLANKS,
                    songTitle = source.head.title,
                    artist = source.head.artist,
                    coverUrl = source.head.coverUrl,
                    lines = dataset.lines,
                )
            }
            return
        }

        val allAnswers = dataset.questions.map { it.answer }

        // 一次把所有題目的選項算好並快取——換題時不必重算，選項也不會跳動
        val seed = source.head.songId
        optionsByBlank = dataset.questions.associate { question ->
            question.blankIndex to FillChoiceBuilder.buildOptions(
                question,
                allAnswers,
                randomFor(seed + question.blankIndex),
            )
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                songTitle = source.head.title,
                artist = source.head.artist,
                coverUrl = source.head.coverUrl,
                lines = dataset.lines,
                questions = dataset.questions,
                options = optionsByBlank[0].orEmpty(),
            )
        }
    }

    private fun failWith(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    /**
     * 挖空的亂數種子。
     *
     * **以 `songId` 為種子**，讓同一首歌永遠挖同一組字。這有兩個好處：
     *  1. 從 Room 回復時（第 2 層）挖出來的字與死亡前一致——否則使用者會發現
     *     「同一個空格換了字」
     *  2. 同一首歌重複進入的體驗穩定，不再每次不同
     */
    private fun randomFor(seed: Long): Random = Random(seed.hashCode())

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

        _uiState.update {
            it.copy(
                activeBlankIndex = nextIndex,
                options = optionsByBlank[nextIndex].orEmpty(),
            )
        }
    }

    /** 跳到指定題（拖曳進度條時使用）。 */
    fun onSeekTo(blankIndex: Int) {
        val state = _uiState.value
        val clamped = blankIndex.coerceIn(0, (state.questions.size - 1).coerceAtLeast(0))

        _uiState.update {
            it.copy(
                activeBlankIndex = clamped,
                options = optionsByBlank[clamped].orEmpty(),
            )
        }
    }

    fun onReplay() {
        val state = _uiState.value
        if (state.questions.isEmpty()) return

        _uiState.update {
            it.copy(
                activeBlankIndex = 0,
                selections = emptyMap(),
                options = optionsByBlank[0].orEmpty(),
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
            }.onFailure { error ->
                android.util.Log.w(TAG, "記錄作答失敗（不影響練習）", error)
            }
        }
    }

    /**
     * 第一次作答時把歌曲與歌詞寫入快取；之後沿用同一個 id。
     *
     * ⚠️ **不能依賴 `sessionStore`**：程序死亡後回復的那條路徑
     * （見 [restoreFromCache]）根本沒有記憶體 session，但歌已經在 Room 裡了。
     * 因此這裡只依賴 [songHead] 與 [_uiState]。
     */
    private suspend fun ensureSongPersisted(): Long? {
        localSongId?.let { return it }

        val head = songHead ?: return null

        val songId = songDao.upsertByLrclibId(
            SongEntity(
                lrclibId = head.songId,
                title = head.title,
                artist = head.artist,
                coverUrl = head.coverUrl,
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
