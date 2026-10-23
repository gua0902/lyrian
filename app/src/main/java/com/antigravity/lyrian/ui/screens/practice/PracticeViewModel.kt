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
import com.antigravity.lyrian.data.audio.YouTubeCandidateScorer
import com.antigravity.lyrian.data.audio.YouTubePlayerController
import com.antigravity.lyrian.data.audio.YouTubeWebSearch
import com.antigravity.lyrian.data.dictionary.DictionaryRepository
import com.antigravity.lyrian.data.dictionary.DictionaryResult
import com.antigravity.lyrian.data.lang.LanguageData
import com.antigravity.lyrian.data.lang.LanguageDataStore
import com.antigravity.lyrian.data.lyrics.LyricsParser
import com.antigravity.lyrian.data.settings.PlaybackOffsets
import com.antigravity.lyrian.data.settings.UserSettings
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.ChoiceRecord
import com.antigravity.lyrian.domain.model.BlankDecision
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.PracticeModeId
import com.antigravity.lyrian.domain.model.ResultWord
import com.antigravity.lyrian.domain.practice.BlankDifficulty
import com.antigravity.lyrian.domain.practice.CommonWordFilter
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import com.antigravity.lyrian.ui.navigation.Routes
import com.antigravity.lyrian.util.toDayKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
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
    /**
     * 目前是否由**歌曲時間軸**驅動節奏。
     *
     * true 表示歌詞與題目跟著播放時間走（擁有者指定的新模式）；
     * false 表示沒有可用音源，退回「依作答進度推進」。
     */
    val isSongDriven: Boolean = false,
    /**
     * 因為「上一句還沒作答」而自動暫停時，正在等哪一個空格。
     *
     * null 表示沒有在等。作答完成後會清空並淡入恢復播放。
     */
    val waitingForBlankIndex: Int? = null,
    /** 音源相關的提示（例如「找不到對應的錄音版本」）。 */
    val audioNotice: String? = null,    /**
     * 播放器本身回報的錯誤。
     *
     * 與 [audioNotice] **分開存放**：播放器是單例，它的錯誤會一直留在狀態裡，
     * 若混進 [audioNotice] 就會讓「上一首歌的播放錯誤」黏到下一首歌上。
     * 這個欄位每次載入新歌時都會清空。
     */
    val playbackError: String? = null,

    /** 使用者為這首歌設定的歌詞偏移（毫秒）。正值＝歌詞延後。 */
    val lyricOffsetMs: Long = 0L,
    /** 目前播放的是候選清單中的第幾個版本（0-based）。 */
    val playerVersionIndex: Int = 0,
    /** 候選版本總數；<= 1 時不顯示「換版本」。 */
    val playerVersionCount: Int = 0,
    /** 影片長寬比（寬/高）；用來把播放器塞進封面框。 */
    val videoAspectRatio: Float? = null,

    // ---------- 結果頁（模組 2）----------

    /** 這一輪開始的時間（第一次組完題）。用來算「用時」。 */
    val sessionStartedAtMs: Long = 0L,
    /** 用時（毫秒）；尚未完成時為 null。 */
    val elapsedMs: Long? = null,
    /** 答錯的題目（正解 + 詞性 + 釋義）。 */
    val wrongWords: List<ResultWord> = emptyList(),
    /**
     * 延伸詞彙：本首歌中**沒被挖空**、等級高於設定值的字。
     *
     * 依原形去重、等級高者優先，最多 [EXTENSION_LIMIT] 條。
     */
    val extensionWords: List<ResultWord> = emptyList(),
    /** 原始（未查字典）的延伸詞彙，字典查完後才填 [extensionWords]。 */
    val extensionCandidates: List<String> = emptyList(),
    /** 每一句的挖空決策；作答落庫時要靠它知道「這題挖的是哪個原形、什麼等級」。 */
    val blankDecisions: List<BlankDecision> = emptyList(),
    /** 這一輪的挖空難度（只影響選詞優先級）。 */
    val difficulty: BlankDifficulty = BlankDifficulty.DEFAULT,
    /** 這一輪的題數上限。 */
    val maxBlanks: Int = FillChoiceBuilder.DEFAULT_MAX_BLANKS,
    /**
     * 歌詞是否有時間軸。
     *
     * 有的話**歌詞行只能由 `currentTime` 推進**，作答不能讓行號前進
     * （擁有者指定的模組 B）。沒有時間軸時才退回「答完自動下一題」。
     */
    val hasTimeline: Boolean = false,
    /** 只有播放器、沒有歌詞（手動貼 YouTube 連結時的兜底模式）。 */
    val playerOnly: Boolean = false,
    /**
     * AI 是否已啟用。
     *
     * 本輪 AI 一律是「未啟用」（模組 6 只做設定儲存），因此結果頁的
     * 「值得學的表達」區塊整塊隱藏——擁有者指定 AI 未啟用時不顯示空殼。
     */
    val aiConfigured: Boolean = false,
) {
    val currentQuestion: FillChoiceQuestion?
        get() = questions.getOrNull(activeBlankIndex)

    val currentRecord: ChoiceRecord?
        get() = currentQuestion?.let { selections[it.blankIndex] }

    /**
     * 全部答完。
     *
     * ⚠️ 判斷依據是**答了幾題**，不是 [activeBlankIndex]。
     * 歌曲驅動時題目軸由播放時間決定，最後一句唱完之後 index 不會再增加——
     * 用 index 判斷的話，跟著歌唱歌永遠不會「完成」，也就永遠看不到結果頁。
     */
    val isComplete: Boolean
        get() = questions.isNotEmpty() && selections.size >= questions.size

    val correctCount: Int get() = selections.values.count { it.isCorrect }

    /**
     * 還沒作答的題目，依歌詞順序。
     *
     * 這是模組 B 的單一真相來源：暫停判斷、追回判斷、進度條刻度全都看它。
     * 已作答的題目**永遠不會**從這裡消失（換行、換版本、暫停都不會清 `selections`）。
     */
    val unansweredQuestions: List<FillChoiceQuestion>
        get() = questions.filterNot { selections.containsKey(it.blankIndex) }

    /** 最早一個尚未作答的題目；全部答完時為 null。 */
    val earliestUnanswered: FillChoiceQuestion?
        get() = unansweredQuestions.minByOrNull { it.lineIndex }

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

/** 在 YouTube Music 上搜尋不到可配對的版本。 */
private const val NO_AUDIO_MATCH = "找不到這首歌對應的錄音版本，只能純歌詞練習。"

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

/**
 * 「上一句沒作答就暫停」的寬限時間。
 *
 * 擁有者指定 0.5 秒：畫面捲到下一句後 0.5 秒內補上就不算錯過。
 * 短於此會來不及反應，長於此就失去「跟上歌曲」的壓力。
 */

/** 音量淡出／淡入的時間。擁有者指定 0.3 秒。 */
private const val FADE_OUT_MS = 300L
/** 因為等作答而暫停時顯示的提示。 */
private const val WAITING_FOR_ANSWER = "已暫停，補上這句的填空就會繼續播放。"

/** 拖曳放手時，往前找未答挖空的容許誤差（避免差幾十毫秒就跳過該題）。 */
private const val SNAP_TOLERANCE_MS = 1_500L

/** 所有候選版本都不允許嵌入時顯示的提示（模組 A.5）。 */
private const val EMBED_BLOCKED = "這些版本都不允許嵌入播放，換一首歌試試。"

/** 結果頁的延伸詞彙上限（擁有者指定最多 20 條）。 */
private const val EXTENSION_LIMIT = 20

/**
 * 自動入庫的最低等級（模組 D）。
 *
 * B1 以上不論對錯一律入庫；A1/A2 一律不入庫。
 */
private val WORDBANK_MIN_LEVEL = CefrLevel.B1

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
    /**
     * 手動貼上的 YouTube 影片 id（模組 A 的手動兜底）。
     *
     * 非 null 時**跳過 YouTube 搜尋**，直接用這個影片——
     * 外呼被擋（503）或斷網時，這是唯一能播的路徑。
     */
    val manualVideoId: String? = null,
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
    private val youTubeSearch: YouTubeWebSearch,
    private val playbackOffsets: PlaybackOffsets,
    private val languageData: LanguageDataStore,
    private val userSettings: UserSettings,
    private val dictionary: DictionaryRepository,
    val player: YouTubePlayerController,
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
        viewModelScope.launch { load(songId) }
        observePlayback()
    }

    // ---------- 音訊 ----------

    /** 是否已經搜尋過音源（避免每次按播放都重打 YouTube）。 */
    private var audioResolved = false

    /** 排序後的候選版本；「換版本」就是換這份清單的索引。 */
    private var candidates: List<YouTubeCandidateScorer.Scored> = emptyList()

    /**
     * 正在淡出／暫停的旗標。
     *
     * 暫停要花 0.3 秒淡出，這段時間輪詢還會繼續跑；沒有這個旗標就會重複觸發暫停。
     */
    private var pausing = false

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
                        videoAspectRatio = playback.videoAspectRatio,
                    )
                }

                // 版權方關掉嵌入（101/150 → playabilityStatus UNPLAYABLE）是最常見的失敗。
                // 自動換下一個候選；全部用完才告訴使用者「此版本不允許嵌入」。
                if (playback.embedBlocked) {
                    onSwitchVersion(auto = true)
                }
            }
        }

        // 播放中才輪詢位置。
        //
        // ⚠️ **暫停時不更新位置**——這正是「暫停期間歌詞時間軸同步凍結」的實作。
        // ⚠️ 位置要加上使用者微調的 offset 才拿去比對歌詞時間戳（調整立即生效）。
        //
        // ## 三條正交的因果（模組 B-1）
        //
        // ```
        // 換行 → 只由播放時間決定（這裡）
        // 暫停 → 只由「進入一行尚未作答的挖空」決定（enterUnansweredLine）
        // 作答 → 只解除暫停，不改換行（onSelectOption）
        // ```
        viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                if (state.isPlaying && !pausing) {
                    val position = player.currentPositionMs()
                    val lyricPosition = position + state.lyricOffsetMs
                    val currentLine = lineIndexAt(lyricPosition)

                    if (currentLine != null) {
                        // ---- B-4 追回機制 ----
                        // 有「比現在這行更早」的未答挖空 → 拉回該行開頭並暫停。
                        // 觸發場景：卡頓、廣告、拖動、換版本、淡出那 0.3 秒跑過頭。
                        val earliest = state.earliestUnanswered
                        if (earliest != null && earliest.lineIndex < currentLine) {
                            _uiState.update { it.copy(playbackPositionMs = position) }
                            enterUnansweredLine(earliest, seekBack = true)
                        } else {
                            _uiState.update {
                                it.copy(
                                    playbackPositionMs = position,
                                    singingLineIndex = currentLine,
                                    isSongDriven = true,
                                )
                            }

                            // ---- B-2 行進入驅動 ----
                            // 進入新的一行時，只要該行有未答挖空就**立即**暫停。
                            // 刻意不用「時間落在某區間內」判斷，避免 tick 間隔剛好跳過整句。
                            if (currentLine != state.singingLineIndex) {
                                val question = questionForLine(currentLine)
                                if (question != null &&
                                    !_uiState.value.selections.containsKey(question.blankIndex)
                                ) {
                                    enterUnansweredLine(question, seekBack = true)
                                }
                            }
                        }
                    } else {
                        _uiState.update { it.copy(playbackPositionMs = position) }
                    }
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    /**
     * 暫停在一個尚未作答的挖空上。
     *
     * **這是唯一會暫停的地方**（使用者的播放鍵除外）。流程：
     *  1. 把題目切到那一題（否則使用者補不到它）
     *  2. 0.3 秒淡出後 pause
     *  3. seek 回該行開頭——保證「播放不會越過該句」，也讓補答後能重聽整句
     */
    private fun enterUnansweredLine(question: FillChoiceQuestion, seekBack: Boolean) {
        if (pausing) return
        pausing = true

        _uiState.update {
            it.copy(
                activeBlankIndex = question.blankIndex,
                options = optionsByBlank[question.blankIndex].orEmpty(),
                waitingForBlankIndex = question.blankIndex,
                singingLineIndex = question.lineIndex,
                audioNotice = WAITING_FOR_ANSWER,
            )
        }

        viewModelScope.launch {
            player.fadeOut(FADE_OUT_MS)
            player.pause()

            if (seekBack) {
                val target = (question.startMs - _uiState.value.lyricOffsetMs).coerceAtLeast(0L)
                player.seekTo(target)
                _uiState.update {
                    it.copy(playbackPositionMs = target, singingLineIndex = question.lineIndex)
                }
            }
            pausing = false
        }
    }

    /** 目前這一行的題目；沒有挖空時回傳 null。 */
    private fun questionForLine(lineIndex: Int?): FillChoiceQuestion? {
        if (lineIndex == null) return null
        return _uiState.value.questions.firstOrNull { it.lineIndex == lineIndex }
    }

    /**
     * 準備音源。第一次呼叫才會真的打 YouTube。
     *
     * ⚠️ **時長不符時不硬播**：見 `AudioTrackMatcher`。若挑到的版本
     * 與 LRCLIB 的時間戳明顯不是同一個錄音，硬播只會讓歌詞整首錯位。
     */
    /**
     * 找音源：用「歌手 + 歌名」搜 YouTube，排序後取最佳候選。
     *
     * 只做一次（[audioResolved]）；換版本會走 [onSwitchVersion]，不會重新搜尋。
     */
    fun prepareAudio() {
        if (audioResolved || _uiState.value.isAudioLoading) return
        val head = songHead ?: return

        audioResolved = true
        _uiState.update {
            it.copy(isAudioLoading = true, audioNotice = null, playbackError = null)
        }

        // ---- 手動兜底：直接播使用者貼的影片，不搜尋 ----
        // 503／斷網時這是唯一能播的路徑，所以放在最前面。
        head.manualVideoId?.let { videoId ->
            android.util.Log.d(TAG, "手動音源：直接播放 $videoId")
            candidates = listOf(
                YouTubeCandidateScorer.Scored(
                    video = com.antigravity.lyrian.data.audio.YouTubeVideo(
                        videoId = videoId,
                        title = head.title,
                        channel = "手動輸入",
                        durationMs = head.durationMs,
                        viewCount = 0,
                    ),
                    score = 0.0,
                    isConfident = true,
                    durationDeltaMs = 0L,
                    penaltyKeywords = emptyList(),
                ),
            )
            playCandidate(index = 0)
            return
        }

        viewModelScope.launch {
            val query = "${head.artist} ${head.title}".trim()
            val videos = youTubeSearch.search(query)

            if (videos.isEmpty()) {
                _uiState.update {
                    it.copy(isAudioLoading = false, audioNotice = NO_AUDIO_MATCH)
                }
                return@launch
            }

            val ranked = YouTubeCandidateScorer.rank(
                candidates = videos,
                expectedTitle = head.title,
                expectedArtist = head.artist,
                expectedDurationMs = head.durationMs,
            )

            if (ranked.isEmpty()) {
                _uiState.update {
                    it.copy(isAudioLoading = false, audioNotice = NO_AUDIO_MATCH)
                }
                return@launch
            }

            candidates = ranked
            playCandidate(index = 0)

            android.util.Log.d(
                TAG,
                "音源排序（${head.title}）：" + ranked.joinToString(" | ") {
                    "${it.video.videoId} score=${"%.1f".format(it.score)} " +
                        "delta=${it.durationDeltaMs}ms conf=${it.isConfident} " +
                        "penalty=${it.penaltyKeywords} ${it.video.channel}"
                },
            )
        }
    }

    /** 播放候選清單中的第 [index] 個版本。 */
    private fun playCandidate(index: Int) {
        val list = candidates
        val chosen = list.getOrNull(index)
        if (chosen == null) {
            _uiState.update { it.copy(isAudioLoading = false, audioNotice = NO_AUDIO_MATCH) }
            return
        }

        // 使用者是「按了播放鍵」才走到這裡的，所以要自動播
        player.load(videoId = chosen.video.videoId, startAtMs = 0L, autoPlay = true)

        _uiState.update {
            it.copy(
                isAudioLoading = false,
                isAudioReady = true,
                playerVersionIndex = index,
                playerVersionCount = list.size,
                audioNotice = if (chosen.isConfident) {
                    null
                } else {
                    AUDIO_MAY_NOT_ALIGN
                },
                playbackError = null,
            )
        }
    }

    /**
     * 換下一個候選版本。
     *
     * @param auto true 表示是「播放器回報不能嵌入」時自動換的
     */
    fun onSwitchVersion(auto: Boolean = false) {
        val list = candidates
        if (list.isEmpty()) return

        // 全部候選都試過了 → 明確告訴使用者，不要讓畫面一直空轉
        if (auto && _uiState.value.playerVersionIndex >= list.lastIndex) {
            if (_uiState.value.audioNotice == EMBED_BLOCKED) return
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    audioNotice = EMBED_BLOCKED,
                )
            }
            return
        }

        val next = (_uiState.value.playerVersionIndex + 1) % list.size
        playCandidate(next)

        if (!auto) {
            _uiState.update { it.copy(audioNotice = null) }
        }
    }

    /**
     * 微調歌詞偏移。**調整立即生效**：下一次位置輪詢就套用新的 offset。
     *
     * @param deltaMs 正數＝歌詞延後（歌聲比歌詞早）
     */
    fun onAdjustOffset(deltaMs: Long) {
        val head = songHead ?: return
        val next = (_uiState.value.lyricOffsetMs + deltaMs)
            .coerceIn(-PlaybackOffsets.MAX_OFFSET_MS, PlaybackOffsets.MAX_OFFSET_MS)

        playbackOffsets.setOffsetMs(head.songId, next)

        // 立即用新 offset 重算「正在唱哪一行」，不必等下一次輪詢
        val lyricPosition = player.currentPositionMs() + next
        _uiState.update {
            it.copy(
                lyricOffsetMs = next,
                singingLineIndex = lineIndexAt(lyricPosition) ?: it.singingLineIndex,
            )
        }
    }

    fun onResetOffset() {
        val head = songHead ?: return
        playbackOffsets.setOffsetMs(head.songId, 0L)
        _uiState.update { it.copy(lyricOffsetMs = 0L) }
    }

    /**
     * 播放鍵：**同時控制播放器與歌詞滾動**。
     *
     * 這是擁有者指定的核心行為——只有一個開關，不是兩個。
     * 使用者手動按下播放時，視同「我知道我欠一題，先讓我聽」：清掉等待狀態。
     * （但未答的題目**不會消失**，追回機制仍會在下一次 tick 把它拉回來。）
     */
    fun onTogglePlayPause() {
        if (!_uiState.value.isAudioReady) {
            prepareAudio()
            return
        }

        if (_uiState.value.waitingForBlankIndex != null) {
            _uiState.update {
                it.copy(waitingForBlankIndex = null, audioNotice = null)
            }
        }

        player.togglePlayPause()
    }

    /** 拖曳進度條過程中的即時 seek（不判斷未答，放手時才算）。 */
    fun onSeekPlayback(positionMs: Long) {
        player.seekTo(positionMs)
        _uiState.update { it.copy(playbackPositionMs = positionMs) }
    }

    /**
     * 拖曳結束（模組 B-7 / H-2）。
     *
     * 擁有者指定：「允許拖動，但到達後必須暫停在第一個未答挖空處，不得直接播過去」。
     * 因此放手時把位置對齊到「拖到的地方之後第一個未答的挖空」，並停在那裡。
     */
    fun onTimelineSeekFinished() {
        val state = _uiState.value
        val next = state.unansweredQuestions
            .filter { it.startMs >= state.playbackPositionMs - SNAP_TOLERANCE_MS }
            .minByOrNull { it.startMs }
            ?: state.unansweredQuestions.minByOrNull { it.lineIndex }
            ?: return

        player.pause()
        enterUnansweredLine(next, seekBack = true)
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
        // 使用者真的離開練習頁（不是旋轉）→ 停掉播放。
        // WebView 本身由畫面的 DisposableEffect 解除綁定（見 PracticeScreen）。
        player.stop()
    }

    private suspend fun load(songId: Long?) {
        if (songId == null) {
            failWith(ERROR_NO_SESSION)
            return
        }

        // 語言資料（等級／原形／詞頻）約 6 萬筆，第一次載入要解析 0.9 MB，
        // 因此在 IO 執行緒完成後才組題——否則第一次進練習頁會掉一格畫面。
        val language = languageData.get()
        val difficulty = userSettings.difficultyValue
        val maxBlanks = userSettings.maxBlanksValue

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
                        manualVideoId = session.manualVideoId,
                    ),
                    lines = LyricsParser.parse(
                        lyricsFile = session.lyricsFile,
                        syncedLyrics = session.syncedLyrics,
                        plainLyrics = session.plainLyrics,
                    ),
                ),
                language = language,
                difficulty = difficulty,
                maxBlanks = maxBlanks,
            )
            return
        }

        // 第 2 層：程序被系統回收後，從 Room 快取取回。
        // 這條路徑需要第 1 次作答時寫入的 song / lyric_line（見 ensureSongPersisted）。
        val restored = restoreFromCache(songId)
        if (restored != null) {
            applySource(restored, language, difficulty, maxBlanks)
        } else {
            failWith(ERROR_NO_SESSION)
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
    private fun applySource(
        source: SongSource,
        language: LanguageData,
        difficulty: BlankDifficulty,
        maxBlanks: Int,
    ) {
        songHead = source.head

        // 播放器是單例，可能還留著上一首歌的狀態 → 每次都清乾淨
        player.stop()
        audioResolved = false
        candidates = emptyList()

        val dataset = FillChoiceBuilder.buildDataset(
            lines = source.lines,
            difficulty = difficulty,
            maxBlanks = maxBlanks,
            levelSource = language,
        )

        logBlankDecisions(dataset.debug, difficulty, maxBlanks)

        if (dataset.questions.isEmpty()) {
            // 手動輸入「只有 YouTube 連結、沒有歌詞」時，這不是錯誤——
            // 它正是外呼被擋（503／斷網）時的兜底路徑：至少要把歌播出來。
            val playerOnly = source.head.manualVideoId != null

            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = if (playerOnly) null else ERROR_NO_BLANKS,
                    playerOnly = playerOnly,
                    songTitle = source.head.title,
                    artist = source.head.artist,
                    coverUrl = source.head.coverUrl,
                    lines = dataset.lines,
                    hasTimeline = dataset.lines.any { line -> line.startMs > 0L },
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
                // 換歌時把節奏狀態歸零，避免上一首的暫停／行號黏過來
                isSongDriven = false,
                singingLineIndex = null,
                waitingForBlankIndex = null,
                playbackPositionMs = 0L,
                // 每首歌有自己的歌詞偏移（存在本地，下次進來仍然有效）
                lyricOffsetMs = playbackOffsets.offsetMs(source.head.songId),
                playerVersionIndex = 0,
                playerVersionCount = 0,
                videoAspectRatio = null,
                isAudioReady = false,
                isAudioLoading = false,
                // 結果頁的統計只存在這一次的 session，不落庫
                sessionStartedAtMs = System.currentTimeMillis(),
                elapsedMs = null,
                wrongWords = emptyList(),
                extensionWords = emptyList(),
                difficulty = difficulty,
                maxBlanks = maxBlanks,
                hasTimeline = dataset.lines.any { it.startMs > 0L },
                blankDecisions = dataset.debug,
                extensionCandidates = computeExtensionCandidates(
                    lines = source.lines,
                    difficulty = difficulty,
                    language = language,
                    blanked = allAnswers.map { it.lowercase() }.toSet(),
                ),
            )
        }
    }

    /**
     * 找出「本首歌中沒被挖空、等級高於這次難度」的字。
     *
     * 依擁有者指定：**依原形去重、等級高者優先、最多 20 條**。
     */
    private fun computeExtensionCandidates(
        lines: List<LyricLine>,
        difficulty: BlankDifficulty,
        language: LanguageData,
        blanked: Set<String>,
    ): List<String> {
        val floor = difficulty.minLevel

        // lemma → 排序鍵（等級序、詞頻排名），取較難的那一個
        data class Key(val level: Int, val rank: Int)
        val byLemma = LinkedHashMap<String, Key>()

        lines.forEach { line ->
            FillChoiceBuilder.TOKEN_PATTERN.findAll(line.text).forEach { match ->
                val token = match.value
                if (token.length < FillChoiceBuilder.BLANK_MIN_WORD_LENGTH) return@forEach
                if (token.lowercase() in blanked) return@forEach
                if (CommonWordFilter.classify(token).isCommon) return@forEach
                if (language.isHighFrequency(token)) return@forEach

                val level = language.lookup(token)
                if (level.treatAsLevel < floor) return@forEach

                val key = Key(level.treatAsLevel.ordinal, level.frequencyRank ?: Int.MAX_VALUE)
                val previous = byLemma[level.lemma]
                if (previous == null || key.level > previous.level ||
                    (key.level == previous.level && key.rank > previous.rank)
                ) {
                    byLemma[level.lemma] = key
                }
            }
        }

        return byLemma.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Key>> { it.value.level }
                    .thenByDescending { it.value.rank }
                    .thenBy { it.key },
            )
            .map { it.key }
            .take(EXTENSION_LIMIT)
    }

    private fun failWith(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    /**
     * 印出「每句選中的詞、原形、等級、詞頻、排序依據」，讓擁有者能直接驗證選詞。
     *
     * 輸出長這樣（可在 logcat 用 `LyrianPractice` 過濾）：
     * ```
     * D/LyrianPractice: 挖空統計：48 句 → 22 題（起點 B1，全部）；過濾 96 個高頻詞
     * D/LyrianPractice: 挖空 L3: silhouettes | 原形 silhouette | 等級 C1 | 詞頻 BNC 11637 | 同級取罕見（BNC 11637 而非 BNC 6159）
     * D/LyrianPractice:   L3 過濾: love(Top 1000 高頻詞), the(冠詞)
     * ```
     */
    private fun logBlankDecisions(
        decisions: List<BlankDecision>,
        difficulty: BlankDifficulty,
        maxBlanks: Int,
    ) {
        if (decisions.isEmpty()) return

        val blanks = decisions.count { it.chosenWord != null }
        val filtered = decisions.sumOf { it.rejected.size }
        val filler = decisions.count { it.chosenWord == null && it.rejected.isEmpty() }

        android.util.Log.d(
            TAG,
            "挖空統計：${decisions.size} 句 → $blanks 題（難度 ${difficulty.label}，上限 $maxBlanks）" +
                "；過濾 $filtered 個字；無實詞略過 $filler 句",
        )

        decisions.forEach { decision ->
            if (decision.chosenWord != null) {
                val freq = decision.frequencyRank?.let { "BNC $it" } ?: "未收錄"
                android.util.Log.d(
                    TAG,
                    "挖空 L${decision.lineIndex}: ${decision.chosenWord} | 原形 " +
                        "${decision.chosenLemma ?: decision.chosenWord} | 等級 " +
                        "${decision.chosenLevel ?: "未收錄"}（${decision.chosenLevelSource}）" +
                        " | 詞頻 $freq | ${decision.basis ?: "—"}",
                )
            } else if (decision.rejected.isNotEmpty()) {
                android.util.Log.d(
                    TAG,
                    "挖空 L${decision.lineIndex}: （未選）${decision.basis ?: "非本次選句"}",
                )
            }

            if (decision.rejected.isNotEmpty()) {
                val detail = decision.rejected.joinToString { "${it.word}(${it.levelSource})" }
                android.util.Log.d(TAG, "  L${decision.lineIndex} 過濾: $detail")
            }
        }
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
        val wasWaitingForThis = state.waitingForBlankIndex == question.blankIndex

        _uiState.update {
            it.copy(
                selections = it.selections + (question.blankIndex to ChoiceRecord(choice, attempt.isCorrect)),
            )
        }

        // 答錯仍照常進字庫（擁有者指定）——不論對錯都落庫，錯的才進字庫
        persist(question, choice, attempt.isCorrect)

        // ---- B-1 第三條因果 ----
        // 作答**只解除暫停**：淡入恢復播放。不碰 `singingLineIndex`、
        // 也不碰 `activeBlankIndex`——換行永遠只由播放時間決定。
        // 未答狀態本來就只增不減，所以「回到前面補答」永遠可行。
        if (wasWaitingForThis) {
            _uiState.update {
                it.copy(waitingForBlankIndex = null, audioNotice = null)
            }
            viewModelScope.launch {
                player.fadeIn(FADE_OUT_MS)
                player.play()
            }
        }

        finishIfComplete()
    }

    /**
     * 全部答完時，算出結果頁要的統計與兩份字表。
     *
     * 「用時」只存在 session 狀態裡（擁有者指定不新增資料表）；
     * 字典查詢是離線資產，查 20~30 個字只是一連串雜湊查表。
     */
    private fun finishIfComplete() {
        val state = _uiState.value
        if (state.elapsedMs != null) return
        if (state.questions.isEmpty() || state.selections.size < state.questions.size) return

        val elapsed = (System.currentTimeMillis() - state.sessionStartedAtMs).coerceAtLeast(0L)
        _uiState.update { it.copy(elapsedMs = elapsed) }

        viewModelScope.launch {
            val wrong = state.questions
                .filter { question -> state.selections[question.blankIndex]?.isCorrect == false }
                .map { toResultWord(it.answer) }

            val extension = state.extensionCandidates.map { toResultWord(it) }

            _uiState.update { it.copy(wrongWords = wrong, extensionWords = extension) }
        }
    }

    /** 查一個字的詞性與中文釋義；查不到時只留單字本身。 */
    private suspend fun toResultWord(word: String): ResultWord {
        val hit = dictionary.lookup(word)
        return if (hit is DictionaryResult.Found) {
            ResultWord(
                word = word,
                partOfSpeech = hit.entry.partOfSpeech,
                meaning = hit.entry.translation,
            )
        } else {
            ResultWord(word = word)
        }
    }

    /**
     * 前進到下一題（**只有「沒有時間軸」時才成立**）。
     *
     * 擁有者指定的模組 B：歌詞行只能由 `currentTime` 推進。
     * 因此只要歌詞有時間戳，作答就**不得**讓行號前進——就算音源還沒載入、
     * `isSongDriven` 還是 false 也一樣。用 [PracticeUiState.hasTimeline] 當守門員，
     * 而不是 `isSongDriven`（後者要等第一次輪詢才會變 true，中間那幾秒就會亂跳）。
     */
    fun onNext() {
        val state = _uiState.value
        if (state.hasTimeline) return
        if (state.currentRecord == null) return

        val nextIndex = state.activeBlankIndex + 1

        _uiState.update {
            it.copy(
                activeBlankIndex = nextIndex,
                options = optionsByBlank[nextIndex].orEmpty(),
            )
        }
    }

    /**
     * 跳到指定題（進度條上點未答刻度、或補答入口）。
     *
     * 模組 B-6：「使用者任何時候都應能回到未答的題目補答」。
     * 因此這裡會**暫停**並把時間對齊到該句開頭——不會只是換題目而繼續播。
     */
    fun onSeekTo(blankIndex: Int) {
        val state = _uiState.value
        val clamped = blankIndex.coerceIn(0, (state.questions.size - 1).coerceAtLeast(0))
        val question = state.questions.getOrNull(clamped) ?: return

        if (state.unansweredQuestions.any { it.blankIndex == clamped }) {
            // 未答的題目：停下來讓使用者補答（不繼續往前播）
            player.pause()
            enterUnansweredLine(question, seekBack = true)
            return
        }

        // 已答的題目：單純跳時間，不強制暫停
        _uiState.update {
            it.copy(
                activeBlankIndex = clamped,
                options = optionsByBlank[clamped].orEmpty(),
            )
        }

        if (state.isAudioReady) {
            // 歌詞位置 = 播放位置 + offset，所以跳回歌詞時間要反過來扣掉 offset
            onSeekPlayback((question.startMs - state.lyricOffsetMs).coerceAtLeast(0L))
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
                waitingForBlankIndex = null,
                singingLineIndex = null,
                // 結果頁的統計歸零重算（延伸詞彙不必重查，字是一樣的）
                sessionStartedAtMs = System.currentTimeMillis(),
                elapsedMs = null,
                wrongWords = emptyList(),
                extensionWords = emptyList(),
            )
        }

        if (state.isAudioReady) {
            onSeekPlayback(0L)
        }
    }

    /**
     * 把一次作答落庫：作答紀錄 →（依等級）字庫 → 每日統計。
     *
     * ## 入庫規則是「依 CEFR 等級」，不是「依對錯」（模組 D）
     *
     * ```
     * B1 以上：不論答對答錯，一律入庫
     * A1 / A2：一律不入庫（使用者要的話，結果頁有「加入字庫」按鈕）
     * ```
     *
     * 理由：這個 App 的字庫是「學習清單」，不是「錯題本」。B1 的字已經
     * 超出使用者的舒適區，就算猜對也值得排進複習；A1/A2 的字猜錯只是手滑，
     * 收進字庫只會把清單灌滿早就會的字。
     *
     * 答錯時仍然會記 `errorCount`（供日後排複習順序），但**入不入庫由等級決定**。
     *
     * **失敗不中斷練習**——與「AI 不在關鍵路徑上」同一原則。
     */
    private fun persist(question: FillChoiceQuestion, choice: String, isCorrect: Boolean) {
        viewModelScope.launch {
            runCatching {
                val songId = ensureSongPersisted()

                val decision = _uiState.value.blankDecisions.firstOrNull {
                    it.lineIndex == question.lineIndex
                }
                val level = CefrLevel.fromLabel(decision?.chosenLevel)
                    ?: CefrLevel.C2 // 未收錄的字視為高階
                val lemma = decision?.chosenLemma
                    ?: FillChoiceBuilder.normalizeAnswer(question.answer)

                val shouldAddToWordbank = level >= WORDBANK_MIN_LEVEL

                val vocabId = when {
                    !shouldAddToWordbank -> null

                    isCorrect -> vocabDao.ensureInWordbank(
                        lemma = lemma,
                        surfaceForm = question.answer,
                        contextLine = question.originalLine,
                        songId = songId,
                        cefr = level.label,
                        now = System.currentTimeMillis(),
                    )

                    else -> vocabDao.addFromError(
                        lemma = lemma,
                        surfaceForm = question.answer,
                        contextLine = question.originalLine,
                        songId = songId,
                        cefr = level.label,
                        now = System.currentTimeMillis(),
                    ).first
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
     * 結果頁的「加入字庫」（模組 D.3）。
     *
     * **手動加入不受等級限制**——這是使用者明確表示「我要這個字」。
     * 同一個 lemma 已存在時不會重複新增（`ensureInWordbank` 是冪等的）。
     */
    fun onAddToWordbank(word: String, lemma: String, contextLine: String?) {
        viewModelScope.launch {
            runCatching {
                val songId = ensureSongPersisted()
                vocabDao.ensureInWordbank(
                    lemma = FillChoiceBuilder.normalizeAnswer(lemma),
                    surfaceForm = word,
                    contextLine = contextLine,
                    songId = songId,
                    cefr = null,
                    now = System.currentTimeMillis(),
                )
            }.onFailure {
                android.util.Log.w(TAG, "手動加入字庫失敗", it)
            }

            _uiState.update { state ->
                state.copy(
                    wrongWords = state.wrongWords.map {
                        if (it.word == word) it.copy(addedToWordbank = true) else it
                    },
                    extensionWords = state.extensionWords.map {
                        if (it.word == word) it.copy(addedToWordbank = true) else it
                    },
                )
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
